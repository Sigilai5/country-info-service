package com.ncba.countryinfo.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ncba.countryinfo.logging.LogConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;

/**
 * Standard envelope for every API response, success or error, so consumers can always read
 * {@code responseCode}/{@code responseMessage} and quote the {@code requestId} to support.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response envelope")
public record WsResponse<T>(
        @Schema(description = "HTTP status code", example = "200") String responseCode,
        @Schema(description = "Human-readable outcome", example = "Request processed successfully") String responseMessage,
        @Schema(description = "Correlation ID, also returned in the X-Request-ID header") String requestId,
        @Schema(description = "Response time (UTC)") Instant timestamp,
        @Schema(description = "Response payload (absent on errors)") T data,
        @Schema(description = "Field validation errors (only on 400)") Map<String, String> errors) {

    public static <T> WsResponse<T> success(HttpStatusCode status, String message, T data) {
        return new WsResponse<>(String.valueOf(status.value()), message,
                MDC.get(LogConstants.MDC_REQUEST_ID), Instant.now(), data, null);
    }

    public static <T> WsResponse<T> error(HttpStatusCode status, String message) {
        return error(status, message, null);
    }

    public static <T> WsResponse<T> error(HttpStatusCode status, String message,
            Map<String, String> errors) {
        return new WsResponse<>(String.valueOf(status.value()), message,
                MDC.get(LogConstants.MDC_REQUEST_ID), Instant.now(), null, errors);
    }
}
