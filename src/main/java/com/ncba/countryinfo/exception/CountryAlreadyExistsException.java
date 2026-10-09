package com.ncba.countryinfo.exception;

import com.ncba.countryinfo.dto.CountryInfoResponse;

/**
 * The submitted country is already stored. Maps to 409 Conflict; the existing record is returned in
 * the response's {@code data} so the client can use it (e.g. its ID) without another call.
 */
public class CountryAlreadyExistsException extends RuntimeException {

    private final transient CountryInfoResponse existing;

    public CountryAlreadyExistsException(CountryInfoResponse existing) {
        super("Country '" + existing.name() + "' (" + existing.isoCode() + ") already exists with id " + existing.id());
        this.existing = existing;
    }

    public CountryInfoResponse getExisting() {
        return existing;
    }
}
