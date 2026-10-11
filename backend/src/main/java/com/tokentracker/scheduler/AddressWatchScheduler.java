package com.tokentracker.scheduler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tokentracker.service.AddressWatchService;
import com.tokentracker.service.dto.AddressCheckResultDTO;

/**
 * Checks the watched addresses. The cron expression comes from {@code token-tracker.watch.cron} (UTC).
 */
@Component
public class AddressWatchScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(AddressWatchScheduler.class);

    private final AddressWatchService watchService;

    public AddressWatchScheduler(AddressWatchService watchService) {
        this.watchService = watchService;
    }

    /** Every 10 minutes by default: balances of every watched address, variations sent to Telegram. */
    @Scheduled(cron = "${token-tracker.watch.cron}", zone = "UTC")
    public void checkAddresses() {
        try {
            List<AddressCheckResultDTO> results = watchService.checkAll();
            if (results.isEmpty()) {
                return;
            }
            long failed = results.stream().filter(r -> r.error() != null).count();
            int changes = results.stream().mapToInt(AddressCheckResultDTO::changes).sum();
            if (failed == 0) {
                LOG.debug("Address watch done for {} address(es), {} variation(s)", results.size(), changes);
            } else {
                LOG.warn("Address watch done for {} address(es), {} variation(s), {} with errors",
                        results.size(), changes, failed);
            }
        } catch (RuntimeException e) {
            LOG.error("Address watch failed: {}", e.getMessage(), e);
        }
    }
}
