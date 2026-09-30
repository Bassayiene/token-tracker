package com.tokentracker.domain;

/**
 * Account roles. Visitors (no account) can only read token summaries and prices.
 */
public enum Role {
    /** Reads every collected data. */
    USER,
    /** Also adds and removes tokens and triggers synchronisations. */
    ADMIN
}
