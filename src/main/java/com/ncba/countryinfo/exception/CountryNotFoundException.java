package com.ncba.countryinfo.exception;

/** The country could not be found (unknown to the SOAP service, or no such stored record). Maps to 404. */
public class CountryNotFoundException extends RuntimeException {

    public CountryNotFoundException(String message) {
        super(message);
    }
}
