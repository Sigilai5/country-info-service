package com.ncba.countryinfo.exception;

/** A request that passed field validation but is still invalid (e.g. unknown sort field). Maps to 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
