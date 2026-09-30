package com.tokentracker.exception;

/**
 * An upstream Waves API (Data API or node) could not be reached or returned an unusable response.
 * Mapped to HTTP 502 when it surfaces through a REST call.
 */
public class WavesApiException extends RuntimeException {

    public WavesApiException(String message) {
        super(message);
    }

    public WavesApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
