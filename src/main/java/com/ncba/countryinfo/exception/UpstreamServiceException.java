package com.ncba.countryinfo.exception;

/**
 * The external SOAP service failed after retries, or the circuit breaker is open. Maps to 503
 * so clients know the failure is temporary and safe to retry later.
 */
public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
