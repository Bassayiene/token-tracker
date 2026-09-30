package com.tokentracker.exception;

/**
 * The requested resource does not exist. Mapped to HTTP 404.
 */
public class NotFoundAlertException extends RuntimeException {

    private final String entityName;
    private final String errorKey;

    public NotFoundAlertException(String message, String entityName, String errorKey) {
        super(message);
        this.entityName = entityName;
        this.errorKey = errorKey;
    }

    public String getEntityName() { return entityName; }
    public String getErrorKey()   { return errorKey; }
}
