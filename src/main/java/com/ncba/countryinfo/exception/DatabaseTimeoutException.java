package com.ncba.countryinfo.exception;

/**
 * A database call did not complete in time: query timeout, row-lock wait timeout, transaction
 * timeout, or no pooled connection available. Maps to 503 with Retry-After, since the failure is
 * transient and safe to retry.
 */
public class DatabaseTimeoutException extends RuntimeException {

    public DatabaseTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
