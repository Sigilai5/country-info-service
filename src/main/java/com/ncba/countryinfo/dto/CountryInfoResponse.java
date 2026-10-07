package com.ncba.countryinfo.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** A stored country with its full information (originally from the FullCountryInfo SOAP operation). */
public record CountryInfoResponse(
        @Schema(description = "Database ID", example = "1") Long id,
        @Schema(example = "KE") String isoCode,
        @Schema(example = "Kenya") String name,
        @Schema(example = "Nairobi") String capitalCity,
        @Schema(example = "254") String phoneCode,
        @Schema(example = "AF") String continentCode,
        @Schema(example = "KES") String currencyIsoCode,
        @Schema(example = "http://www.oorsprong.org/WebSamples.CountryInfo/Flags/Kenya.jpg") String countryFlag,
        List<LanguageResponse> languages,
        @Schema(description = "Optimistic-lock version; send it back on PUT to avoid overwriting newer changes",
                example = "0") Long version,
        @Schema(description = "When the record was first stored (UTC)") Instant createdAt,
        @Schema(description = "When the record was last changed (UTC)") Instant updatedAt) {
}
