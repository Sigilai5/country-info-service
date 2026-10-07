package com.ncba.countryinfo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LanguageRequest(
        @Schema(example = "swa")
        @NotBlank(message = "language isoCode is required")
        @Pattern(regexp = "^[A-Za-z]{2,10}$", message = "language isoCode must be 2-10 letters")
        String isoCode,

        @Schema(example = "Swahili")
        @NotBlank(message = "language name is required")
        @Size(max = 100, message = "language name must be at most 100 characters")
        String name) {
}
