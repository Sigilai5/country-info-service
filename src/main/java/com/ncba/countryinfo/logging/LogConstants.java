package com.ncba.countryinfo.logging;

public final class LogConstants {

    public static final String SUCCESS_STATUS = "SUCCESS";
    public static final String FAILED_STATUS = "FAILED";
    public static final String IN_PROGRESS_STATUS = "IN_PROGRESS";

    /** MDC keys, populated per request by {@link RequestLoggingFilter}. */
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_CLIENT_IP = "clientIp";

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String START_TIME_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".startTime";

    public static final String THIS_SYSTEM = "country-info-service";

    private LogConstants() {
    }
}
