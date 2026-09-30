package com.tokentracker.exception;

/**
 * The request is invalid (bad parameters, unknown asset, duplicate token...). Mapped to HTTP 400.
 */
public class BadRequestAlertException extends RuntimeException {

    private final String entityName;
    private final String errorKey;

    public BadRequestAlertException(String message, String entityName, String errorKey) {
        super(message);
        this.entityName = entityName;
        this.errorKey = errorKey;
    }

    public String getEntityName() { return entityName; }
    public String getErrorKey()   { return errorKey; }
}
