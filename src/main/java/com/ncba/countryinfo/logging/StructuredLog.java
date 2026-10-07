package com.ncba.countryinfo.logging;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Fluent structured log event. Every field is emitted as its own key in the JSON log line
 * (alongside the MDC {@code requestId}), so logs can be filtered and aggregated by field.
 *
 * <pre>{@code
 * StructuredLog.of(getClass())
 *         .setLogMessage("Country saved: " + country.getIsoCode())
 *         .setLogLevel("info")
 *         .setResponseCode("201")
 *         .setTargetSystem("MySQL")
 *         .setProcessName("createCountry")
 *         .setLogStatus(SUCCESS_STATUS)
 *         .setTransactionCost(System.currentTimeMillis() - startTime)
 *         .write();
 * }</pre>
 *
 * A new instance is created per event, so it is safe to use from concurrent requests.
 */
public final class StructuredLog {

    private final Logger logger;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private Level level = Level.INFO;
    private String message = "";
    private Throwable cause;

    private StructuredLog(Class<?> source) {
        this.logger = LoggerFactory.getLogger(source);
        fields.put("sourceSystem", source.getName());
    }

    public static StructuredLog of(Class<?> source) {
        return new StructuredLog(source);
    }

    public StructuredLog setLogMessage(String message) {
        this.message = message;
        return this;
    }

    /** Accepts trace, debug, info, warn or error (case-insensitive). */
    public StructuredLog setLogLevel(String level) {
        this.level = Level.valueOf(level.toUpperCase(Locale.ROOT));
        return this;
    }

    public StructuredLog setResponseCode(String responseCode) {
        return put("responseCode", responseCode);
    }

    public StructuredLog setResponseCode(int responseCode) {
        return put("responseCode", String.valueOf(responseCode));
    }

    public StructuredLog setTargetEndpoint(String targetEndpoint) {
        return put("targetEndpoint", targetEndpoint);
    }

    public StructuredLog setTargetSystem(String targetSystem) {
        return put("targetSystem", targetSystem);
    }

    public StructuredLog setProcessName(String processName) {
        return put("processName", processName);
    }

    public StructuredLog setOperationName(String operationName) {
        return put("operationName", operationName);
    }

    public StructuredLog setSourceSystem(String sourceSystem) {
        return put("sourceSystem", sourceSystem);
    }

    public StructuredLog setLogType(String logType) {
        return put("logType", logType);
    }

    public StructuredLog setLogStatus(String logStatus) {
        return put("logStatus", logStatus);
    }

    public StructuredLog setSourceIp(String sourceIp) {
        return put("sourceIp", sourceIp);
    }

    public StructuredLog setProcessId(String processId) {
        return put("processId", processId);
    }

    /** Elapsed time in milliseconds. */
    public StructuredLog setTransactionCost(long transactionCostMs) {
        return put("transactionCost", transactionCostMs);
    }

    /** Extra domain-specific field, e.g. {@code with("isoCode", "KE")}. */
    public StructuredLog with(String key, Object value) {
        return put(key, value);
    }

    /** Attaches an exception; its class and message are logged and the stack trace included. */
    public StructuredLog setError(Throwable cause) {
        this.cause = cause;
        put("errorType", cause.getClass().getSimpleName());
        return put("errorMessage", cause.getMessage());
    }

    public void write() {
        if (!logger.isEnabledForLevel(level)) {
            return;
        }
        // Default the caller IP from the current request when not set explicitly.
        fields.putIfAbsent("sourceIp", MDC.get(LogConstants.MDC_CLIENT_IP));
        LoggingEventBuilder event = logger.atLevel(level);
        fields.forEach((key, value) -> {
            if (value != null) {
                event.addKeyValue(key, value);
            }
        });
        if (cause != null) {
            event.setCause(cause);
        }
        event.log(message);
    }

    private StructuredLog put(String key, Object value) {
        fields.put(key, value);
        return this;
    }
}
