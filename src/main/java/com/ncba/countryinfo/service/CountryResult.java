package com.ncba.countryinfo.service;

import com.ncba.countryinfo.dto.CountryInfoResponse;

/** Outcome of submitting a country: the stored record, and whether it was newly created (201) or already existed (200). */
public record CountryResult(CountryInfoResponse country, boolean created) {
}
