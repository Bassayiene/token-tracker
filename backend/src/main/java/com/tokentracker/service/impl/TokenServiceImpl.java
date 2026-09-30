package com.tokentracker.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.tokentracker.client.WavesDataApiClient;
import com.tokentracker.client.dto.WavesAsset;
import com.tokentracker.config.TokenTrackerProperties;
import com.tokentracker.domain.Token;
import com.tokentracker.domain.TokenHolderStats;
import com.tokentracker.domain.TokenPrice;
import com.tokentracker.domain.TokenQuantity;
import com.tokentracker.domain.TokenTopHolder;
import com.tokentracker.exception.BadRequestAlertException;
import com.tokentracker.exception.NotFoundAlertException;
import com.tokentracker.repository.TokenHolderStatsRepository;
import com.tokentracker.repository.TokenPriceRepository;
import com.tokentracker.repository.TokenQuantityRepository;
import com.tokentracker.repository.TokenRepository;
import com.tokentracker.repository.TokenTopHolderRepository;
import com.tokentracker.service.TokenService;
import com.tokentracker.service.TokenSyncService;
import com.tokentracker.service.dto.HolderStatsDTO;
import com.tokentracker.service.dto.TokenPriceDTO;
import com.tokentracker.service.dto.TokenQuantityDTO;
import com.tokentracker.service.dto.TokenSummaryDTO;
import com.tokentracker.service.dto.TopHolderDTO;
import com.tokentracker.service.dto.TopHoldersDTO;

@Service
@Transactional(readOnly = true)
public class TokenServiceImpl implements TokenService {

    private static final Logger LOG = LoggerFactory.getLogger(TokenServiceImpl.class);
    private static final String ENTITY_NAME = "token";

    private static final Duration DEFAULT_PRICE_RANGE = Duration.ofDays(7);
    private static final Duration MAX_PRICE_RANGE = Duration.ofDays(366);
    private static final int DEFAULT_DAILY_RANGE_DAYS = 90;
    private static final int MAX_DAILY_RANGE_DAYS = 3660;

    private final TokenRepository tokenRepository;
    private final TokenPriceRepository priceRepository;
    private final TokenQuantityRepository quantityRepository;
    private final TokenTopHolderRepository topHolderRepository;
    private final TokenHolderStatsRepository holderStatsRepository;
    private final WavesDataApiClient dataApiClient;
    private final TokenSyncService syncService;
    private final TokenTrackerProperties properties;
    private final Clock clock;

    public TokenServiceImpl(TokenRepository tokenRepository,
            TokenPriceRepository priceRepository,
            TokenQuantityRepository quantityRepository,
            TokenTopHolderRepository topHolderRepository,
            TokenHolderStatsRepository holderStatsRepository,
            WavesDataApiClient dataApiClient,
            TokenSyncService syncService,
            TokenTrackerProperties properties,
            Clock clock) {
        this.tokenRepository = tokenRepository;
        this.priceRepository = priceRepository;
        this.quantityRepository = quantityRepository;
        this.topHolderRepository = topHolderRepository;
        this.holderStatsRepository = holderStatsRepository;
        this.dataApiClient = dataApiClient;
        this.syncService = syncService;
        this.properties = properties;
        this.clock = clock;
    }

    // ─── Tokens ──────────────────────────────────────────────────────────

    @Override
    public List<TokenSummaryDTO> findAll() {
        return tokenRepository.findAllByOrderByIdAsc().stream().map(this::toSummary).toList();
    }

    @Override
    public TokenSummaryDTO findOne(String assetId) {
        return toSummary(getToken(assetId));
    }

    @Override
    @Transactional(readOnly = false)
    public TokenSummaryDTO addToken(String assetId) {
        if (tokenRepository.existsByAssetId(assetId)) {
            throw new BadRequestAlertException("Token already tracked: " + assetId, ENTITY_NAME, "tokenexists");
        }
        WavesAsset asset = dataApiClient.getAsset(assetId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Asset not found on Waves: " + assetId, ENTITY_NAME, "assetnotfound"));

        Token token = new Token();
        token.setAssetId(asset.assetId());
        token.setName(asset.name());
        token.setTicker(asset.ticker());
        token.setDecimals(asset.decimals());
        token.setDescription(truncate(asset.description(), 1000));
        token.setCreatedAt(asset.issuedAt());
        token.setReissuable(asset.reissuable());
        token.setHasScript(asset.hasScript());
        try {
            token = tokenRepository.saveAndFlush(token);
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestAlertException("Token already tracked: " + assetId, ENTITY_NAME, "tokenexists");
        }
        LOG.info("Token added: {} ({})", token.getName(), token.getAssetId());

        // Collect the history once the token row is committed.
        String addedAssetId = token.getAssetId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        syncService.syncTokenAsync(addedAssetId);
                    }
                });
        return toSummary(token);
    }

    @Override
    @Transactional(readOnly = false)
    public void deleteToken(String assetId) {
        Token token = getToken(assetId);
        // Prices, quantities and holder data are removed by the ON DELETE CASCADE foreign keys.
        tokenRepository.delete(token);
        LOG.info("Token deleted: {} ({})", token.getName(), assetId);
    }

    // ─── Collected data ──────────────────────────────────────────────────

    @Override
    public List<TokenPriceDTO> getPrices(String assetId, Instant from, Instant to) {
        getToken(assetId);
        Instant end = to != null ? to : Instant.now(clock);
        Instant start = from != null ? from : end.minus(DEFAULT_PRICE_RANGE);
        if (start.isAfter(end)) {
            throw new BadRequestAlertException("'from' must be before 'to'", "price", "invalidrange");
        }
        if (Duration.between(start, end).compareTo(MAX_PRICE_RANGE) > 0) {
            throw new BadRequestAlertException("Range too large (max 366 days)", "price", "rangetoolarge");
        }
        return priceRepository.findByAssetIdAndPriceTimeBetweenOrderByPriceTimeAsc(assetId, start, end).stream()
                .map(p -> new TokenPriceDTO(p.getPriceTime(), p.getPrice(), p.getVolume(), p.isHasTrades()))
                .toList();
    }

    @Override
    public List<TokenQuantityDTO> getQuantities(String assetId, LocalDate from, LocalDate to) {
        Token token = getToken(assetId);
        LocalDate[] range = dailyRange(from, to, "quantity");
        return quantityRepository.findByAssetIdAndDateBetweenOrderByDateAsc(assetId, range[0], range[1]).stream()
                .map(q -> new TokenQuantityDTO(q.getDate(), q.getQuantity(), adjust(q.getQuantity(), token)))
                .toList();
    }

    @Override
    public List<HolderStatsDTO> getHolderStats(String assetId, LocalDate from, LocalDate to) {
        getToken(assetId);
        LocalDate[] range = dailyRange(from, to, "holderstats");
        return holderStatsRepository.findByAssetIdAndDateBetweenOrderByDateAsc(assetId, range[0], range[1]).stream()
                .map(s -> new HolderStatsDTO(s.getDate(), s.getHoldersCount(), s.getTop10Share(), s.getTop100Share()))
                .toList();
    }

    @Override
    public TopHoldersDTO getTopHolders(String assetId, LocalDate date) {
        Token token = getToken(assetId);
        List<LocalDate> dates = topHolderRepository.findDates(assetId);
        LocalDate day = date != null ? date : (dates.isEmpty() ? null : dates.get(0));
        if (day == null || !dates.contains(day)) {
            return new TopHoldersDTO(day, null, null, null, List.of(), dates);
        }

        List<TokenTopHolder> holders = topHolderRepository.findByAssetIdAndDateOrderByRankAsc(assetId, day);
        LocalDate previousDay = topHolderRepository.findPreviousDate(assetId, day).orElse(null);
        Map<String, TokenTopHolder> previous = previousDay == null ? Map.of()
                : topHolderRepository.findByAssetIdAndDateOrderByRankAsc(assetId, previousDay).stream()
                        .collect(Collectors.toMap(TokenTopHolder::getAddress, Function.identity()));

        // Shares are computed against the quantity of the same day, or the latest known one.
        long quantity = quantityRepository.findByAssetIdAndDate(assetId, day)
                .or(() -> quantityRepository.findFirstByAssetIdOrderByDateDesc(assetId))
                .map(TokenQuantity::getQuantity)
                .orElse(0L);

        List<TopHolderDTO> rows = holders.stream().map(h -> {
            TokenTopHolder before = previous.get(h.getAddress());
            boolean isNew = previousDay != null && before == null;
            return new TopHolderDTO(
                    h.getRank(),
                    h.getAddress(),
                    adjust(h.getBalance(), token),
                    TokenSyncServiceImpl.share(h.getBalance(), quantity),
                    before == null ? null : adjust(h.getBalance() - before.getBalance(), token),
                    before == null ? null : before.getRank() - h.getRank(),
                    isNew);
        }).toList();

        return new TopHoldersDTO(day, previousDay, holders.isEmpty() ? null : holders.get(0).getHeight(),
                quantity > 0 ? adjust(quantity, token) : null, rows, dates);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private Token getToken(String assetId) {
        return tokenRepository.findByAssetId(assetId)
                .orElseThrow(() -> new NotFoundAlertException("Token not tracked: " + assetId, ENTITY_NAME, "notfound"));
    }

    private TokenSummaryDTO toSummary(Token token) {
        String assetId = token.getAssetId();
        TokenPrice latest = priceRepository.findFirstByAssetIdOrderByPriceTimeDesc(assetId).orElse(null);
        Instant lastTradeTime = priceRepository.findFirstByAssetIdAndHasTradesTrueOrderByPriceTimeDesc(assetId)
                .map(TokenPrice::getPriceTime).orElse(null);
        TokenQuantity quantity = quantityRepository.findFirstByAssetIdOrderByDateDesc(assetId).orElse(null);
        TokenHolderStats stats = holderStatsRepository.findFirstByAssetIdOrderByDateDesc(assetId).orElse(null);

        BigDecimal lastPrice = latest == null ? null : latest.getPrice();
        BigDecimal change24h = latest == null ? null : changeSince(latest, Duration.ofHours(24));
        BigDecimal change7d = latest == null ? null : changeSince(latest, Duration.ofDays(7));

        return new TokenSummaryDTO(
                assetId,
                token.getName(),
                token.getTicker(),
                token.getDecimals(),
                token.getDescription(),
                token.getCreatedAt(),
                token.isReissuable(),
                token.isHasScript(),
                properties.priceAsset(),
                lastPrice,
                latest == null ? null : latest.getPriceTime(),
                lastTradeTime,
                change24h,
                change7d,
                quantity == null ? null : adjust(quantity.getQuantity(), token),
                quantity == null ? null : quantity.getDate(),
                stats == null ? null : stats.getHoldersCount(),
                stats == null ? null : stats.getTop10Share(),
                stats == null ? null : stats.getTop100Share(),
                stats == null ? null : stats.getDate());
    }

    /** Variation in percent between the latest price and the price {@code period} earlier. */
    private BigDecimal changeSince(TokenPrice latest, Duration period) {
        if (latest.getPrice() == null) {
            return null;
        }
        return priceRepository.findByAssetIdAndPriceTime(latest.getAssetId(), latest.getPriceTime().minus(period))
                .map(TokenPrice::getPrice)
                .filter(past -> past.signum() != 0)
                .map(past -> latest.getPrice().subtract(past)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(past, 4, RoundingMode.HALF_UP))
                .orElse(null);
    }

    private LocalDate[] dailyRange(LocalDate from, LocalDate to, String entity) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAILY_RANGE_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BadRequestAlertException("'from' must be before 'to'", entity, "invalidrange");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_DAILY_RANGE_DAYS) {
            throw new BadRequestAlertException("Range too large (max " + MAX_DAILY_RANGE_DAYS + " days)",
                    entity, "rangetoolarge");
        }
        return new LocalDate[] { start, end };
    }

    /** Raw on-chain amount to a human amount, using the token decimals. */
    private static BigDecimal adjust(long raw, Token token) {
        return BigDecimal.valueOf(raw).movePointLeft(token.getDecimals());
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
