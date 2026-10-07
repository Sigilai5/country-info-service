package com.ncba.countryinfo.soap.model;

import java.util.List;

/**
 * Immutable view of a FullCountryInfo SOAP result. Keeps the generated JAXB types inside the
 * soap package, so the rest of the app does not depend on the WSDL contract (and cached values
 * cannot be mutated by callers).
 */
public record CountryDetails(
        String isoCode,
        String name,
        String capitalCity,
        String phoneCode,
        String continentCode,
        String currencyIsoCode,
        String countryFlag,
        List<LanguageDetails> languages) {

    public CountryDetails {
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
