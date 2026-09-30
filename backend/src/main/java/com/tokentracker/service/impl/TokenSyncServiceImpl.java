package com.tokentracker.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.tokentracker.client.WavesDataApiClient;
import com.tokentracker.client.WavesNodeClient;
import com.tokentracker.client.dto.WavesAsset;
import com.tokentracker.client.dto.WavesCandle;
import com.tokentracker.client.dto.WavesDistributionPage;
import com.tokentracker.config.TokenTrackerProperties;
import com.tokentracker.domain.Token;
import com.tokentracker.domain.TokenHolderStats;
import com.tokentracker.domain.TokenPrice;
import com.tokentracker.domain.TokenQuantity;
import com.tokentracker.domain.TokenTopHolder;
import com.tokentracker.exception.NotFoundAlertException;
import com.tokentracker.exception.WavesApiException;
import com.tokentracker.repository.TokenHolderStatsRepository;
import com.tokentracker.repository.TokenPriceRepository;
import com.tokentracker.repository.TokenQuantityRepository;
import com.tokentracker.repository.TokenRepository;
import com.tokentracker.repository.TokenTopHolderRepository;
import com.tokentracker.service.TokenSyncService;
import com.tokentracker.service.dto.SyncResultDTO;

@Service
public class TokenSyncServiceImpl implements TokenSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(TokenSyncServiceImpl.class);

    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int TOP10 = 10;

    /** Top holders: highest balance first, address as tie-breaker so the ranking is deterministic. */
    private static final Comparator<Map.Entry<String, Long>> BY_BALANCE_DESC =
            Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey());

    private final TokenRepository tokenRepository;
    private final TokenPriceRepository priceRepository;
    private final TokenQuantityRepository quantityRepository;
    private final TokenTopHolderRepository topHolderRepository;
    private final TokenHolderStatsRepository holderStatsRepository;
    private final WavesDataApiClient dataApiClient;
    private final WavesNodeClient nodeClient;
    private final TokenTrackerProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    // One run of each collection at a time (scheduler, startup catch-up and manual sync can overlap).
    private final ReentrantLock priceLock = new ReentrantLock();
    private final ReentrantLock dailyLock = new ReentrantLock();

    public TokenSyncServiceImpl(TokenRepository tokenRepository,
            TokenPriceRepository priceRepository,
            TokenQuantityRepository quantityRepository,
            TokenTopHolderRepository topHolderRepository,
            TokenHolderStatsRepository holderStatsRepository,
            WavesDataApiClient dataApiClient,
            WavesNodeClient nodeClient,
            TokenTrackerProperties properties,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.tokenRepository = tokenRepository;
        this.priceRepository = priceRepository;
        this.quantityRepository = quantityRepository;
        this.topHolderRepository = topHolderRepository;
        this.holderStatsRepository = holderStatsRepository;
        this.dataApiClient = dataApiClient;
        this.nodeClient = nodeClient;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    // ─── Public API ──────────────────────────────────────────────────────

    @Override
    public List<SyncResultDTO> syncAllPrices() {
        List<SyncResultDTO> results = new ArrayList<>();
        for (Token token : tokenRepository.findAllByOrderByIdAsc()) {
            results.add(runPrices(token.getAssetId()));
        }
        return results;
    }

    @Override
    public List<SyncResultDTO> syncAllDaily() {
        List<SyncResultDTO> results = new ArrayList<>();
        for (Token token : tokenRepository.findAllByOrderByIdAsc()) {
            results.add(runDaily(token));
        }
        return results;
    }

    @Override
    public SyncResultDTO syncToken(String assetId) {
        Token token = tokenRepository.findByAssetId(assetId)
                .orElseThrow(() -> new NotFoundAlertException("Token not tracked: " + assetId, "token", "notfound"));
        return merge(runPrices(assetId), runDaily(token));
    }

    @Override
    public List<SyncResultDTO> syncAll() {
        List<SyncResultDTO> results = new ArrayList<>();
        for (Token token : tokenRepository.findAllByOrderByIdAsc()) {
            results.add(merge(runPrices(token.getAssetId()), runDaily(token)));
        }
        return results;
    }

    @Override
    public void catchUp() {
        LOG.info("Startup catch-up started");
        syncAllPrices();
        LocalDate today = LocalDate.now(clock);
        for (Token token : tokenRepository.findAllByOrderByIdAsc()) {
            boolean dailyMissing = !quantityRepository.existsByAssetIdAndDate(token.getAssetId(), today)
                    || !holderStatsRepository.existsByAssetIdAndDate(token.getAssetId(), today);
            if (dailyMissing) {
                runDaily(token);
            }
        }
        LOG.info("Startup catch-up finished");
    }

    @Override
    @Async
    public void syncTokenAsync(String assetId) {
        try {
            SyncResultDTO result = syncToken(assetId);
            LOG.info("Initial sync of {}: {} price hours, daily done={}, error={}",
                    assetId, result.priceHours(), result.dailyDone(), result.error());
        } catch (RuntimeException e) {
            LOG.error("Initial sync of {} failed: {}", assetId, e.getMessage(), e);
        }
    }

    // ─── Per-token runs (errors are isolated per token) ──────────────────

    private SyncResultDTO runPrices(String assetId) {
        priceLock.lock();
        try {
            int hours = syncPrices(assetId);
            return new SyncResultDTO(assetId, hours, false, null);
        } catch (RuntimeException e) {
            LOG.error("Price sync failed for {}: {}", assetId, e.getMessage(), e);
            return new SyncResultDTO(assetId, 0, false, "prices: " + e.getMessage());
        } finally {
            priceLock.unlock();
        }
    }

    private SyncResultDTO runDaily(Token token) {
        dailyLock.lock();
        try {
            syncDaily(token);
            return new SyncResultDTO(token.getAssetId(), 0, true, null);
        } catch (RuntimeException e) {
            LOG.error("Daily sync failed for {}: {}", token.getAssetId(), e.getMessage(), e);
            return new SyncResultDTO(token.getAssetId(), 0, false, "daily: " + e.getMessage());
        } finally {
            dailyLock.unlock();
        }
    }

    private static SyncResultDTO merge(SyncResultDTO prices, SyncResultDTO daily) {
        String error = prices.error() == null ? daily.error()
                : daily.error() == null ? prices.error()
                : prices.error() + " | " + daily.error();
        return new SyncResultDTO(prices.assetId(), prices.priceHours(), daily.dailyDone(), error);
    }

    // ─── Hourly prices ───────────────────────────────────────────────────

    /**
     * Writes one row per closed hour in the sync window. The window covers the lookback hours,
     * extends back to the last stored hour after a downtime, and is capped by the backfill days.
     *
     * @return number of hourly rows written
     */
    int syncPrices(String assetId) {
        TokenTrackerProperties.Sync sync = properties.sync();
        // Hour H is closed once H + 1h is reached.
        Instant toHour = Instant.now(clock).truncatedTo(ChronoUnit.HOURS).minus(ONE_HOUR);
        Instant oldestAllowed = toHour.minus(Duration.ofDays(sync.priceBackfillDays())).plus(ONE_HOUR);
        Instant lookbackStart = toHour.minus(Duration.ofHours(sync.priceLookbackHours() - 1L));

        Instant fromHour = priceRepository.findFirstByAssetIdOrderByPriceTimeDesc(assetId)
                .map(latest -> latest.getPriceTime().plus(ONE_HOUR))
                .map(nextMissing -> nextMissing.isBefore(lookbackStart) ? nextMissing : lookbackStart)
                .orElse(oldestAllowed);
        if (fromHour.isBefore(oldestAllowed)) {
            fromHour = oldestAllowed;
        }

        Map<Instant, WavesCandle> candles = fetchHourlyCandles(assetId, fromHour, toHour);
        Map<Instant, TokenPrice> existing = priceRepository
                .findByAssetIdAndPriceTimeBetweenOrderByPriceTimeAsc(assetId, fromHour, toHour).stream()
                .collect(Collectors.toMap(TokenPrice::getPriceTime, Function.identity()));

        final Instant windowStart = fromHour;
        BigDecimal lastPrice = priceRepository
                .findFirstByAssetIdAndPriceTimeBeforeAndPriceIsNotNullOrderByPriceTimeDesc(assetId, windowStart)
                .map(TokenPrice::getPrice)
                .orElseGet(() -> firstTradedHourIn(candles) ? null : findLastTradePrice(assetId, windowStart));

        Instant now = Instant.now(clock);
        List<TokenPrice> rows = new ArrayList<>();
        for (Instant hour = fromHour; !hour.isAfter(toHour); hour = hour.plus(ONE_HOUR)) {
            WavesCandle candle = candles.get(hour);
            TokenPrice row = existing.getOrDefault(hour, new TokenPrice());
            row.setAssetId(assetId);
            row.setPriceTime(hour);
            row.setFetchedAt(now);
            if (candle != null && candle.hasTrades()) {
                lastPrice = candle.close();
                row.setPrice(candle.close());
                row.setVolume(candle.volume() == null ? BigDecimal.ZERO : candle.volume());
                row.setHasTrades(true);
            } else {
                row.setPrice(lastPrice);
                row.setVolume(BigDecimal.ZERO);
                row.setHasTrades(false);
            }
            rows.add(row);
        }
        priceRepository.saveAll(rows);
        LOG.info("Prices of {}: {} hours written ({} -> {})", assetId, rows.size(), fromHour, toHour);
        return rows.size();
    }

    private Map<Instant, WavesCandle> fetchHourlyCandles(String assetId, Instant fromHour, Instant toHour) {
        Duration chunk = Duration.ofDays(properties.sync().priceChunkDays());
        Map<Instant, WavesCandle> candles = new HashMap<>();
        for (Instant start = fromHour; !start.isAfter(toHour); start = start.plus(chunk)) {
            Instant end = start.plus(chunk).minus(ONE_HOUR);
            if (end.isAfter(toHour)) {
                end = toHour;
            }
            for (WavesCandle candle : dataApiClient.getCandles(assetId, properties.priceAsset(), start, end,
                    WavesDataApiClient.INTERVAL_HOUR)) {
                candles.put(candle.time(), candle);
            }
        }
        return candles;
    }

    /** True when the chronologically first candle of the window already has a trade (no seed price needed). */
    private static boolean firstTradedHourIn(Map<Instant, WavesCandle> candles) {
        return candles.values().stream()
                .min(Comparator.comparing(WavesCandle::time))
                .map(WavesCandle::hasTrades)
                .orElse(false);
    }

    /**
     * Seed price for a token with no stored price: close of the last traded day before the window.
     * Returns null when the token did not trade during the search period.
     */
    private BigDecimal findLastTradePrice(String assetId, Instant before) {
        Instant lastDay = before.truncatedTo(ChronoUnit.DAYS).minus(Duration.ofDays(1));
        Instant firstDay = lastDay.minus(Duration.ofDays(properties.sync().lastTradeSearchDays() - 1L));
        return dataApiClient.getCandles(assetId, properties.priceAsset(), firstDay, lastDay,
                        WavesDataApiClient.INTERVAL_DAY).stream()
                .filter(WavesCandle::hasTrades)
                .max(Comparator.comparing(WavesCandle::time))
                .map(WavesCandle::close)
                .orElse(null);
    }

    // ─── Daily quantity, top holders and holder stats ────────────────────

    void syncDaily(Token token) {
        String assetId = token.getAssetId();
        LocalDate today = LocalDate.now(clock);

        WavesAsset asset = dataApiClient.getAsset(assetId)
                .orElseThrow(() -> new WavesApiException("Asset " + assetId + " not found on the Waves Data API"));

        // Read the distribution one block behind the tip: the tip block may still change.
        int height = nodeClient.getHeight() - 1;
        DistributionScan scan = scanDistribution(assetId, height);
        Instant now = Instant.now(clock);

        transactionTemplate.executeWithoutResult(status -> {
            TokenQuantity quantity = quantityRepository.findByAssetIdAndDate(assetId, today)
                    .orElseGet(TokenQuantity::new);
            quantity.setAssetId(assetId);
            quantity.setDate(today);
            quantity.setQuantity(asset.quantity());
            quantity.setFetchedAt(now);
            quantityRepository.save(quantity);

            topHolderRepository.deleteByAssetIdAndDate(assetId, today);
            List<TokenTopHolder> holders = new ArrayList<>();
            for (int i = 0; i < scan.top().size(); i++) {
                Map.Entry<String, Long> entry = scan.top().get(i);
                TokenTopHolder holder = new TokenTopHolder();
                holder.setAssetId(assetId);
                holder.setDate(today);
                holder.setRank(i + 1);
                holder.setAddress(entry.getKey());
                holder.setBalance(entry.getValue());
                holder.setHeight(height);
                holder.setFetchedAt(now);
                holders.add(holder);
            }
            topHolderRepository.saveAll(holders);

            TokenHolderStats stats = holderStatsRepository.findByAssetIdAndDate(assetId, today)
                    .orElseGet(TokenHolderStats::new);
            stats.setAssetId(assetId);
            stats.setDate(today);
            stats.setHoldersCount(scan.holdersCount());
            stats.setTop10Share(share(sumOfFirst(scan.top(), TOP10), asset.quantity()));
            stats.setTop100Share(share(sumOfFirst(scan.top(), scan.top().size()), asset.quantity()));
            stats.setFetchedAt(now);
            holderStatsRepository.save(stats);
        });

        LOG.info("Daily data of {}: quantity={}, holders={}, top saved={} (height {})",
                assetId, asset.quantity(), scan.holdersCount(), scan.top().size(), height);
    }

    /**
     * Reads every page of the distribution, counting holders and keeping only the N largest balances
     * in a bounded min-heap, so memory stays constant whatever the number of holders.
     */
    DistributionScan scanDistribution(String assetId, int height) {
        int limit = properties.topHoldersLimit();
        int pageSize = properties.waves().distributionPageSize();
        PriorityQueue<Map.Entry<String, Long>> heap = new PriorityQueue<>(limit + 1, BY_BALANCE_DESC.reversed());
        int holdersCount = 0;
        String after = null;
        int pages = 0;

        while (true) {
            WavesDistributionPage page = nodeClient.getDistribution(assetId, height, pageSize, after);
            pages++;
            for (Map.Entry<String, Long> entry : page.balances().entrySet()) {
                if (entry.getValue() <= 0) {
                    continue;
                }
                holdersCount++;
                heap.offer(Map.entry(entry.getKey(), entry.getValue()));
                if (heap.size() > limit) {
                    heap.poll();
                }
            }
            if (!page.hasNext()) {
                break;
            }
            if (page.lastItem() == null || page.lastItem().equals(after)) {
                throw new WavesApiException("Distribution pagination of " + assetId + " did not advance");
            }
            after = page.lastItem();
        }

        List<Map.Entry<String, Long>> top = new ArrayList<>(heap);
        top.sort(BY_BALANCE_DESC);
        LOG.debug("Distribution of {} at height {}: {} holders over {} pages", assetId, height, holdersCount, pages);
        return new DistributionScan(holdersCount, top);
    }

    record DistributionScan(int holdersCount, List<Map.Entry<String, Long>> top) {
    }

    private static long sumOfFirst(List<Map.Entry<String, Long>> sortedTop, int n) {
        return sortedTop.stream().limit(n).mapToLong(Map.Entry::getValue).sum();
    }

    /** Percentage (scale 4) of {@code part} in {@code total}; 0 when total is 0. */
    static BigDecimal share(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return BigDecimal.valueOf(part).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }
}
