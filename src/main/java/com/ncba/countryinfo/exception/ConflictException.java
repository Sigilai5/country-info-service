package com.ncba.countryinfo.exception;

/** The request conflicts with the current state (duplicate ISO code, stale version). Maps to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
