package com.tokentracker.scheduler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tokentracker.config.TokenTrackerProperties;
import com.tokentracker.service.TokenSyncService;
import com.tokentracker.service.dto.SyncResultDTO;

/**
 * Triggers the collections. Cron expressions come from {@code token-tracker.sync.*} and are evaluated in UTC.
 */
@Component
public class TokenSyncScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(TokenSyncScheduler.class);

    private final TokenSyncService syncService;
    private final TokenTrackerProperties properties;

    public TokenSyncScheduler(TokenSyncService syncService, TokenTrackerProperties properties) {
        this.syncService = syncService;
        this.properties = properties;
    }

    /** Every hour: price of the hour that just closed (and any missing hour of the lookback window). */
    @Scheduled(cron = "${token-tracker.sync.price-cron}", zone = "UTC")
    public void syncPrices() {
        report("Hourly price sync", syncService.syncAllPrices());
    }

    /** Every day: quantity, top holders and holder statistics. */
    @Scheduled(cron = "${token-tracker.sync.daily-cron}", zone = "UTC")
    public void syncDaily() {
        report("Daily sync", syncService.syncAllDaily());
    }

    /** Fills what was missed while the service was down, without delaying the startup. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        if (!properties.sync().runOnStartup()) {
            return;
        }
        try {
            syncService.catchUp();
        } catch (RuntimeException e) {
            LOG.error("Startup catch-up failed: {}", e.getMessage(), e);
        }
    }

    private static void report(String job, List<SyncResultDTO> results) {
        long failed = results.stream().filter(r -> r.error() != null).count();
        if (failed == 0) {
            LOG.info("{} done for {} token(s)", job, results.size());
        } else {
            LOG.warn("{} done for {} token(s), {} with errors", job, results.size(), failed);
        }
    }
}
