package com.ncba.countryinfo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Incoming payload for {@code POST /api/v1/countries}, e.g. {@code {"name": "Tanzania"}}. */
public record CountryRequest(
        @Schema(description = "Country name, any case (normalized before the SOAP lookup)", example = "kenya")
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        @Pattern(regexp = "^[\\p{L} .'&()-]*$",
                message = "name may only contain letters, spaces and . ' & ( ) -")
        String name) {
}
