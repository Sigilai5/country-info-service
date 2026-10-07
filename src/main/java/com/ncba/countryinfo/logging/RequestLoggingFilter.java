package com.ncba.countryinfo.logging;

import static com.ncba.countryinfo.logging.LogConstants.FAILED_STATUS;
import static com.ncba.countryinfo.logging.LogConstants.IN_PROGRESS_STATUS;
import static com.ncba.countryinfo.logging.LogConstants.SUCCESS_STATUS;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs every inbound API request on arrival and on completion, and sets up the per-request
 * context used by all other logs: a {@code requestId} (taken from the caller's
 * {@code X-Request-ID} header or generated, and echoed back in the response) and the client IP.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Kubernetes probes, Prometheus scrapes and Swagger UI assets would otherwise flood the logs.
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String requestId = resolveRequestId(request);
        // getRemoteAddr honours X-Forwarded-For from trusted proxies (server.forward-headers-strategy).
        String clientIp = request.getRemoteAddr();
        String endpoint = request.getMethod() + " " + request.getRequestURI();

        request.setAttribute(LogConstants.START_TIME_ATTRIBUTE, startTime);
        response.setHeader(LogConstants.REQUEST_ID_HEADER, requestId);
        MDC.put(LogConstants.MDC_REQUEST_ID, requestId);
        MDC.put(LogConstants.MDC_CLIENT_IP, clientIp);
        try {
            StructuredLog.of(getClass())
                    .setLogMessage("Request received: " + endpoint)
                    .setLogLevel("info")
                    .setTargetEndpoint(endpoint)
                    .setTargetSystem(LogConstants.THIS_SYSTEM)
                    .setProcessName("inboundRequest")
                    .setOperationName("Request received")
                    .setLogType("HTTP_REQUEST")
                    .setLogStatus(IN_PROGRESS_STATUS)
                    .setProcessId(requestId)
                    .with("userAgent", request.getHeader("User-Agent"))
                    .write();

            chain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            StructuredLog.of(getClass())
                    .setLogMessage("Request completed: " + endpoint + " -> " + status)
                    .setLogLevel(status >= 500 ? "error" : status >= 400 ? "warn" : "info")
                    .setResponseCode(status)
                    .setTargetEndpoint(endpoint)
                    .setTargetSystem(LogConstants.THIS_SYSTEM)
                    .setProcessName("inboundRequest")
                    .setOperationName("Request completed")
                    .setLogType("HTTP_RESPONSE")
                    .setLogStatus(status < 400 ? SUCCESS_STATUS : FAILED_STATUS)
                    .setProcessId(requestId)
                    .setTransactionCost(System.currentTimeMillis() - startTime)
                    .write();
            MDC.remove(LogConstants.MDC_REQUEST_ID);
            MDC.remove(LogConstants.MDC_CLIENT_IP);
        }
    }

    /** Elapsed milliseconds since the filter saw this request, or 0 if unknown. */
    public static long elapsedMillis(HttpServletRequest request) {
        Object start = request.getAttribute(LogConstants.START_TIME_ATTRIBUTE);
        return start instanceof Long s ? System.currentTimeMillis() - s : 0;
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(LogConstants.REQUEST_ID_HEADER);
        // Only trust well-formed IDs, so callers cannot inject arbitrary text into logs.
        return incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
    }
}
