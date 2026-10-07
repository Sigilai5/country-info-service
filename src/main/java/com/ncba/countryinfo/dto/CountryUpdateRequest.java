package com.ncba.countryinfo.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Full replacement of a stored country ({@code PUT /api/v1/countries/{id}}). Optional fields that
 * are omitted are cleared; {@code languages} replaces the whole language list.
 */
public record CountryUpdateRequest(
        @Schema(example = "KE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "isoCode is required")
        @Pattern(regexp = "^[A-Za-z]{2,3}$", message = "isoCode must be 2 or 3 letters")
        String isoCode,

        @Schema(example = "Kenya", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        @Pattern(regexp = "^[\\p{L} .'&()-]*$", message = "name may only contain letters, spaces and . ' & ( ) -")
        String name,

        @Schema(example = "Nairobi")
        @Size(max = 100, message = "capitalCity must be at most 100 characters")
        String capitalCity,

        @Schema(example = "254")
        @Pattern(regexp = "^[0-9+ -]{1,20}$", message = "phoneCode may only contain digits, spaces, + and -")
        String phoneCode,

        @Schema(example = "AF")
        @Pattern(regexp = "^[A-Za-z]{1,5}$", message = "continentCode must be 1-5 letters")
        String continentCode,

        @Schema(example = "KES")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currencyIsoCode must be 3 letters")
        String currencyIsoCode,

        @Schema(example = "http://www.oorsprong.org/WebSamples.CountryInfo/Flags/Kenya.jpg")
        @Size(max = 500, message = "countryFlag must be at most 500 characters")
        @URL(message = "countryFlag must be a valid URL")
        String countryFlag,

        @NotNull(message = "languages is required (use [] for none)")
        @Size(max = 50, message = "at most 50 languages")
        List<@Valid LanguageRequest> languages,

        @Schema(description = "Version from your last GET. If set and the record changed since, the update is "
                + "rejected with 409 instead of overwriting someone else's change.", example = "0")
        Long version) {
}
