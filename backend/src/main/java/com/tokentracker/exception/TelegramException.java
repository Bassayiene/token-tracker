package com.tokentracker.exception;

/**
 * Telegram refused or could not receive a message. The message never contains the bot token. Mapped to HTTP 502.
 */
public class TelegramException extends RuntimeException {

    public TelegramException(String message) {
        super(message);
    }
}
