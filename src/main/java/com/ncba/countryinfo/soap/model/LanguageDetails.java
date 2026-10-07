package com.ncba.countryinfo.soap.model;

/** A language spoken in a country, as returned by FullCountryInfo (e.g. swa / Swahili). */
public record LanguageDetails(String isoCode, String name) {
}
