package com.tokentracker.service.impl;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.tokentracker.client.TelegramClient;
import com.tokentracker.client.WavesNodeClient;
import com.tokentracker.client.dto.WavesAssetBalance;
import com.tokentracker.config.TokenTrackerProperties;
import com.tokentracker.domain.AddressBalance;
import com.tokentracker.domain.AddressBalanceChange;
import com.tokentracker.domain.AddressBalanceDaily;
import com.tokentracker.domain.WatchedAddress;
import com.tokentracker.exception.BadRequestAlertException;
import com.tokentracker.exception.NotFoundAlertException;
import com.tokentracker.exception.TelegramException;
import com.tokentracker.exception.WavesApiException;
import com.tokentracker.repository.AddressBalanceChangeRepository;
import com.tokentracker.repository.AddressBalanceDailyRepository;
import com.tokentracker.repository.AddressBalanceRepository;
import com.tokentracker.repository.WatchedAddressRepository;
import com.tokentracker.service.AddressWatchService;
import com.tokentracker.service.dto.AddressBalanceChangeDTO;
import com.tokentracker.service.dto.AddressBalanceDTO;
import com.tokentracker.service.dto.AddressCheckResultDTO;
import com.tokentracker.service.dto.AddressDailyBalanceDTO;
import com.tokentracker.service.dto.AddressHistoryAssetDTO;
import com.tokentracker.service.dto.WatchedAddressDTO;

/**
 * Node calls and Telegram calls are made outside of any transaction; only the snapshot comparison is transactional.
 * A check that cannot read the node changes nothing, so an outage is never mistaken for emptied balances.
 */
@Service
public class AddressWatchServiceImpl implements AddressWatchService {

    private static final Logger LOG = LoggerFactory.getLogger(AddressWatchServiceImpl.class);

    private static final String ENTITY = "watchedAddress";
    /** Id under which the native token is stored: it has no asset id on-chain. */
    static final String WAVES_ID = "WAVES";
    private static final int WAVES_DECIMALS = 8;
    private static final String EXPLORER_ADDRESS_URL = "https://wscan.io/";
    private static final int MAX_NAME_LENGTH = 40;
    private static final int MAX_ERROR_LENGTH = 500;
    /** Lines stop being added past this size, to stay under Telegram's limit with the footer. */
    private static final int MESSAGE_SOFT_LIMIT = TelegramClient.MAX_MESSAGE_LENGTH - 300;
    private static final int MAX_CHANGES_PAGE = 500;
    private static final int DEFAULT_HISTORY_DAYS = 90;
    private static final int MAX_HISTORY_DAYS = 366;

    private final WatchedAddressRepository addressRepository;
    private final AddressBalanceRepository balanceRepository;
    private final AddressBalanceChangeRepository changeRepository;
    private final AddressBalanceDailyRepository dailyRepository;
    private final WavesNodeClient nodeClient;
    private final TelegramClient telegramClient;
    private final TokenTrackerProperties.Watch watch;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AddressWatchServiceImpl(
            WatchedAddressRepository addressRepository,
            AddressBalanceRepository balanceRepository,
            AddressBalanceChangeRepository changeRepository,
            AddressBalanceDailyRepository dailyRepository,
            WavesNodeClient nodeClient,
            TelegramClient telegramClient,
            TokenTrackerProperties properties,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.addressRepository = addressRepository;
        this.balanceRepository = balanceRepository;
        this.changeRepository = changeRepository;
        this.dailyRepository = dailyRepository;
        this.nodeClient = nodeClient;
        this.telegramClient = telegramClient;
        this.watch = properties.watch();
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    // ─── Management ──────────────────────────────────────────────────────

    @Override
    public List<WatchedAddressDTO> findAll() {
        return transactionTemplate.execute(status -> addressRepository.findAllByOrderByIdAsc().stream()
                .map(this::toDto)
                .toList());
    }

    @Override
    public WatchedAddressDTO add(String rawAddress, String rawLabel) {
        String address = rawAddress.trim();
        String label = rawLabel == null || rawLabel.isBlank() ? null : rawLabel.trim();
        if (addressRepository.existsByAddress(address)) {
            throw new BadRequestAlertException("This address is already watched", ENTITY, "addressexists");
        }
        if (addressRepository.count() >= watch.maxAddresses()) {
            throw new BadRequestAlertException("At most " + watch.maxAddresses() + " addresses can be watched",
                    ENTITY, "toomanyaddresses");
        }
        if (!nodeClient.isValidAddress(address)) {
            throw new BadRequestAlertException("This is not a valid address on this network", ENTITY, "invalidaddress");
        }
        // Read before saving: if the node fails here, nothing is stored and the admin simply retries.
        Map<String, Holding> holdings = fetchHoldings(address, Map.of());

        WatchedAddress saved = transactionTemplate.execute(status -> {
            WatchedAddress entity = new WatchedAddress();
            entity.setAddress(address);
            entity.setLabel(label);
            entity.setCreatedAt(clock.instant());
            entity = addressRepository.save(entity);
            applySnapshot(entity, holdings);
            return entity;
        });
        LOG.info("Now watching {} ({} assets)", address, holdings.size());

        if (telegramClient.isConfigured()) {
            try {
                telegramClient.sendMessage("👁 Surveillance activée : <b>" + TelegramClient.escape(displayName(saved))
                        + "</b>\n" + addressLink(address) + "\n\n" + holdings.size()
                        + " actif(s) détenu(s). Les variations de solde seront signalées ici.");
            } catch (TelegramException e) {
                LOG.warn("Could not confirm the watch of {} on Telegram: {}", address, e.getMessage());
            }
        }
        return toDto(saved);
    }

    @Override
    public void delete(Long id) {
        transactionTemplate.executeWithoutResult(status -> {
            WatchedAddress entity = require(id);
            // Explicit deletes: the FK cascade only exists in the database, not in the persistence context.
            changeRepository.deleteByWatchedAddressId(id);
            dailyRepository.deleteByWatchedAddressId(id);
            balanceRepository.deleteByWatchedAddressId(id);
            addressRepository.delete(entity);
            LOG.info("Stopped watching {}", entity.getAddress());
        });
    }

    @Override
    public List<AddressBalanceDTO> getBalances(Long id) {
        return transactionTemplate.execute(status -> {
            require(id);
            return balanceRepository.findByWatchedAddressId(id).stream()
                    .sorted(Comparator.comparing((AddressBalance b) -> !WAVES_ID.equals(b.getAssetId()))
                            .thenComparing(AddressBalance::getAssetName, String.CASE_INSENSITIVE_ORDER))
                    .map(b -> new AddressBalanceDTO(b.getAssetId(), b.getAssetName(),
                            BigDecimal.valueOf(b.getBalance(), b.getDecimals()), b.getUpdatedAt()))
                    .toList();
        });
    }

    @Override
    public List<AddressBalanceChangeDTO> getChanges(Long id, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_CHANGES_PAGE));
        return transactionTemplate.execute(status -> {
            require(id);
            return changeRepository.findByWatchedAddressIdOrderByDetectedAtDescIdDesc(id, PageRequest.ofSize(size))
                    .stream()
                    .map(c -> new AddressBalanceChangeDTO(c.getId(), c.getAssetId(), c.getAssetName(),
                            BigDecimal.valueOf(c.getOldBalance(), c.getDecimals()),
                            BigDecimal.valueOf(c.getNewBalance(), c.getDecimals()),
                            BigDecimal.valueOf(c.getNewBalance() - c.getOldBalance(), c.getDecimals()),
                            c.isNewAsset(), c.getDetectedAt(), c.isNotified()))
                    .toList();
        });
    }

    @Override
    public List<AddressHistoryAssetDTO> getHistoryAssets(Long id) {
        return transactionTemplate.execute(status -> {
            require(id);
            return dailyRepository.findAssets(id).stream()
                    .sorted(Comparator.comparing((AddressHistoryAssetDTO a) -> !WAVES_ID.equals(a.assetId()))
                            .thenComparing(AddressHistoryAssetDTO::assetName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        });
    }

    @Override
    public List<AddressDailyBalanceDTO> getHistory(Long id, String assetId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_HISTORY_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BadRequestAlertException("'from' must be before 'to'", ENTITY, "invalidrange");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_HISTORY_DAYS) {
            throw new BadRequestAlertException("Range too large (max " + MAX_HISTORY_DAYS + " days)", ENTITY,
                    "rangetoolarge");
        }
        return transactionTemplate.execute(status -> {
            require(id);
            return dailyRepository
                    .findByWatchedAddressIdAndAssetIdAndDateBetweenOrderByDateAsc(id, assetId, start, end).stream()
                    .map(d -> new AddressDailyBalanceDTO(d.getDate(), BigDecimal.valueOf(d.getBalance(), d.getDecimals())))
                    .toList();
        });
    }

    // ─── Checks ──────────────────────────────────────────────────────────

    @Override
    public List<AddressCheckResultDTO> checkAll() {
        List<AddressCheckResultDTO> results = new ArrayList<>();
        for (WatchedAddress entity : addressRepository.findAllByOrderByIdAsc()) {
            try {
                results.add(checkAddress(entity.getId(), entity.getAddress()));
            } catch (RuntimeException e) {
                LOG.error("Check of {} failed: {}", entity.getAddress(), e.getMessage(), e);
                results.add(new AddressCheckResultDTO(entity.getId(), entity.getAddress(), 0, false, e.getMessage()));
            }
        }
        return results;
    }

    @Override
    public AddressCheckResultDTO check(Long id) {
        WatchedAddress entity = transactionTemplate.execute(status -> require(id));
        return checkAddress(entity.getId(), entity.getAddress());
    }

    /** Synchronized so the scheduled job and a manual check never compare the same snapshot twice. */
    private synchronized AddressCheckResultDTO checkAddress(Long id, String address) {
        Map<String, Holding> holdings;
        try {
            Map<String, AddressBalance> known = transactionTemplate.execute(status -> byAssetId(id));
            holdings = fetchHoldings(address, known);
        } catch (WavesApiException e) {
            LOG.warn("Could not read the balances of {}: {}", address, e.getMessage());
            recordError(id, e.getMessage());
            return new AddressCheckResultDTO(id, address, 0, false, e.getMessage());
        }

        Integer changes = transactionTemplate.execute(status -> addressRepository.findById(id)
                .map(entity -> applySnapshot(entity, holdings))
                .orElse(0));
        if (changes > 0) {
            LOG.info("{} variation(s) detected on {}", changes, address);
        }

        String telegramError = notifyPending(id);
        return new AddressCheckResultDTO(id, address, changes, telegramError == null, telegramError);
    }

    /** Current non-zero holdings of an address, WAVES included, keyed by asset id. */
    private Map<String, Holding> fetchHoldings(String address, Map<String, AddressBalance> known) {
        Map<String, Holding> holdings = new LinkedHashMap<>();
        long waves = nodeClient.getWavesBalance(address);
        if (waves > 0) {
            holdings.put(WAVES_ID, new Holding(WAVES_ID, "WAVES", WAVES_DECIMALS, waves));
        }
        for (WavesAssetBalance asset : nodeClient.getAssetBalances(address)) {
            if (asset.balance() <= 0) {
                continue;
            }
            String name = asset.name();
            Integer decimals = asset.decimals();
            if (name == null || decimals == null) {
                // Assets issued by a dApp come without their issue transaction: reuse what is stored, else ask the node.
                AddressBalance stored = known.get(asset.assetId());
                if (stored != null) {
                    name = stored.getAssetName();
                    decimals = stored.getDecimals();
                } else {
                    WavesAssetBalance details = nodeClient.getAssetDetails(asset.assetId());
                    name = details.name();
                    decimals = details.decimals();
                }
            }
            holdings.put(asset.assetId(), new Holding(asset.assetId(), name, decimals, asset.balance()));
        }
        return holdings;
    }

    /**
     * Replaces the stored balances by the given holdings and records the differences.
     * The very first snapshot of an address is its baseline: it records no variation.
     *
     * @return number of variations recorded
     */
    private int applySnapshot(WatchedAddress entity, Map<String, Holding> holdings) {
        Instant now = clock.instant();
        boolean baseline = entity.getLastCheckedAt() == null;
        Map<String, AddressBalance> stored = byAssetId(entity.getId());
        List<AddressBalanceChange> changes = new ArrayList<>();

        for (Holding holding : holdings.values()) {
            AddressBalance row = stored.remove(holding.assetId());
            if (row == null) {
                row = new AddressBalance();
                row.setWatchedAddressId(entity.getId());
                row.setAssetId(holding.assetId());
                row.setAssetName(truncate(holding.name(), 255));
                row.setDecimals(holding.decimals());
                row.setBalance(holding.balance());
                row.setUpdatedAt(now);
                balanceRepository.save(row);
                if (!baseline) {
                    changes.add(change(entity, row, 0L, holding.balance(), true, now));
                }
            } else if (row.getBalance() != holding.balance()) {
                changes.add(change(entity, row, row.getBalance(), holding.balance(), false, now));
                row.setBalance(holding.balance());
                row.setUpdatedAt(now);
            }
        }
        // What is left is no longer held.
        for (AddressBalance gone : stored.values()) {
            changes.add(change(entity, gone, gone.getBalance(), 0L, false, now));
            balanceRepository.delete(gone);
        }

        recordDaily(entity, holdings, stored.values(), now);
        changeRepository.saveAll(changes);
        entity.setLastCheckedAt(now);
        entity.setLastError(null);
        addressRepository.save(entity);
        return changes.size();
    }

    /**
     * Creates or updates today's row (UTC) of every held asset, so each day ends with its last observed balance.
     * Assets that just stopped being held get a 0 for today, which brings their curve down to zero.
     */
    private void recordDaily(WatchedAddress entity, Map<String, Holding> holdings, Iterable<AddressBalance> gone,
            Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Map<String, AddressBalanceDaily> rows = new LinkedHashMap<>();
        dailyRepository.findByWatchedAddressIdAndDate(entity.getId(), today)
                .forEach(row -> rows.put(row.getAssetId(), row));

        for (Holding holding : holdings.values()) {
            upsertDaily(entity, rows, holding.assetId(), holding.name(), holding.decimals(), holding.balance(), today, now);
        }
        for (AddressBalance asset : gone) {
            upsertDaily(entity, rows, asset.getAssetId(), asset.getAssetName(), asset.getDecimals(), 0L, today, now);
        }
    }

    private void upsertDaily(WatchedAddress entity, Map<String, AddressBalanceDaily> rows, String assetId,
            String assetName, int decimals, long balance, LocalDate today, Instant now) {
        AddressBalanceDaily row = rows.get(assetId);
        if (row == null) {
            row = new AddressBalanceDaily();
            row.setWatchedAddressId(entity.getId());
            row.setAssetId(assetId);
            row.setAssetName(truncate(assetName, 255));
            row.setDecimals(decimals);
            row.setDate(today);
            row.setBalance(balance);
            row.setUpdatedAt(now);
            dailyRepository.save(row);
        } else if (row.getBalance() != balance) {
            row.setBalance(balance);
            row.setUpdatedAt(now);
        }
    }

    private static AddressBalanceChange change(WatchedAddress entity, AddressBalance row, long oldBalance,
            long newBalance, boolean newAsset, Instant now) {
        AddressBalanceChange change = new AddressBalanceChange();
        change.setWatchedAddressId(entity.getId());
        change.setAssetId(row.getAssetId());
        change.setAssetName(row.getAssetName());
        change.setDecimals(row.getDecimals());
        change.setOldBalance(oldBalance);
        change.setNewBalance(newBalance);
        change.setNewAsset(newAsset);
        change.setDetectedAt(now);
        change.setNotified(false);
        return change;
    }

    // ─── Telegram ────────────────────────────────────────────────────────

    @Override
    public boolean isTelegramConfigured() {
        return telegramClient.isConfigured();
    }

    @Override
    public void sendTelegramTest() {
        telegramClient.sendMessage("✅ Token tracker : les notifications Telegram fonctionnent.");
    }

    /**
     * Sends every variation of the address not delivered yet (this check and recent failed ones) in one message.
     *
     * @return the Telegram error, null when there was nothing to send or it was delivered
     */
    private String notifyPending(Long id) {
        if (!telegramClient.isConfigured()) {
            return null;
        }
        Instant since = clock.instant().minus(watch.notificationRetry());
        String message = transactionTemplate.execute(status -> {
            List<AddressBalanceChange> pending = changeRepository
                    .findByWatchedAddressIdAndNotifiedFalseAndDetectedAtAfterOrderByDetectedAtAscIdAsc(id, since);
            WatchedAddress entity = addressRepository.findById(id).orElse(null);
            return pending.isEmpty() || entity == null ? null : buildMessage(entity, pending);
        });
        if (message == null) {
            return null;
        }
        try {
            telegramClient.sendMessage(message);
        } catch (TelegramException e) {
            LOG.warn("Telegram notification for watched address {} failed: {}", id, e.getMessage());
            recordError(id, "Telegram: " + e.getMessage());
            return e.getMessage();
        }
        transactionTemplate.executeWithoutResult(status -> changeRepository
                .findByWatchedAddressIdAndNotifiedFalseAndDetectedAtAfterOrderByDetectedAtAscIdAsc(id, since)
                .forEach(c -> c.setNotified(true)));
        return null;
    }

    String buildMessage(WatchedAddress entity, List<AddressBalanceChange> changes) {
        StringBuilder text = new StringBuilder()
                .append("🔔 <b>").append(TelegramClient.escape(displayName(entity))).append("</b>\n")
                .append(addressLink(entity.getAddress())).append("\n");
        int listed = 0;
        for (AddressBalanceChange c : changes) {
            if (listed == watch.maxChangesPerMessage() || text.length() > MESSAGE_SOFT_LIMIT) {
                break;
            }
            long delta = c.getNewBalance() - c.getOldBalance();
            text.append('\n')
                    .append(c.isNewAsset() ? "🆕" : delta > 0 ? "🟢" : "🔴")
                    // <code>: asset names are chosen by their issuer; this keeps Telegram from turning them into links.
                    .append(" <code>").append(TelegramClient.escape(truncate(c.getAssetName(), MAX_NAME_LENGTH)))
                    .append("</code> ")
                    .append(delta > 0 ? "+" : "").append(formatAmount(delta, c.getDecimals()))
                    .append(" → ").append(formatAmount(c.getNewBalance(), c.getDecimals()));
            if (c.isNewAsset()) {
                text.append(" (nouveau)");
            } else if (c.getNewBalance() == 0) {
                text.append(" (plus détenu)");
            }
            listed++;
        }
        int hidden = changes.size() - listed;
        if (hidden > 0) {
            text.append("\n… et ").append(hidden).append(" autre(s) variation(s)");
        }
        return text.toString();
    }

    /** {@code 123456789} with 2 decimals becomes {@code 1 234 567,89}. */
    static String formatAmount(long raw, int decimals) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0.##################", symbols);
        return format.format(BigDecimal.valueOf(raw, decimals));
    }

    private static String addressLink(String address) {
        return "<a href=\"" + EXPLORER_ADDRESS_URL + address + "\">" + address + "</a>";
    }

    private static String displayName(WatchedAddress entity) {
        return entity.getLabel() != null ? entity.getLabel() : entity.getAddress();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private void recordError(Long id, String error) {
        transactionTemplate.executeWithoutResult(status -> addressRepository.findById(id)
                .ifPresent(entity -> entity.setLastError(truncate(error, MAX_ERROR_LENGTH))));
    }

    private Map<String, AddressBalance> byAssetId(Long id) {
        Map<String, AddressBalance> byAssetId = new LinkedHashMap<>();
        balanceRepository.findByWatchedAddressId(id).forEach(b -> byAssetId.put(b.getAssetId(), b));
        return byAssetId;
    }

    private WatchedAddress require(Long id) {
        return addressRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException("This address is not watched", ENTITY, "notfound"));
    }

    private WatchedAddressDTO toDto(WatchedAddress entity) {
        return new WatchedAddressDTO(entity.getId(), entity.getAddress(), entity.getLabel(), entity.getCreatedAt(),
                entity.getLastCheckedAt(), entity.getLastError(),
                balanceRepository.countByWatchedAddressId(entity.getId()));
    }

    private static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private record Holding(String assetId, String name, int decimals, long balance) {
    }
}
