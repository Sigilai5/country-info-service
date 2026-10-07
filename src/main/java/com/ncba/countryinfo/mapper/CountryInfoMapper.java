package com.ncba.countryinfo.mapper;

import com.ncba.countryinfo.dto.CountryInfoResponse;
import com.ncba.countryinfo.dto.LanguageResponse;
import com.ncba.countryinfo.entity.CountryInfo;
import com.ncba.countryinfo.entity.Language;
import com.ncba.countryinfo.soap.model.CountryDetails;

/** Converts between the SOAP model, the JPA entities and the API DTOs. */
public final class CountryInfoMapper {

    private CountryInfoMapper() {
    }

    public static CountryInfo toEntity(CountryDetails details) {
        CountryInfo entity = new CountryInfo();
        entity.setIsoCode(details.isoCode());
        entity.setName(details.name());
        entity.setCapitalCity(details.capitalCity());
        entity.setPhoneCode(details.phoneCode());
        entity.setContinentCode(details.continentCode());
        entity.setCurrencyIsoCode(details.currencyIsoCode());
        entity.setCountryFlag(details.countryFlag());
        entity.replaceLanguages(details.languages().stream()
                .map(l -> new Language(l.isoCode(), l.name()))
                .toList());
        return entity;
    }

    public static CountryInfoResponse toResponse(CountryInfo entity) {
        return new CountryInfoResponse(
                entity.getId(),
                entity.getIsoCode(),
                entity.getName(),
                entity.getCapitalCity(),
                entity.getPhoneCode(),
                entity.getContinentCode(),
                entity.getCurrencyIsoCode(),
                entity.getCountryFlag(),
                entity.getLanguages().stream()
                        .map(l -> new LanguageResponse(l.getIsoCode(), l.getName()))
                        .toList(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
