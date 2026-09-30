package com.tokentracker.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.tokentracker.service.AccountService;

/**
 * Makes sure the configured admin account exists once the schema is migrated.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private final AccountService accountService;

    public AdminAccountInitializer(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public void run(ApplicationArguments args) {
        accountService.ensureAdminAccount();
    }
}
