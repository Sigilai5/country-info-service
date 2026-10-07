package com.ncba.countryinfo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LanguageResponse(
        @Schema(example = "swa") String isoCode,
        @Schema(example = "Swahili") String name) {
}
