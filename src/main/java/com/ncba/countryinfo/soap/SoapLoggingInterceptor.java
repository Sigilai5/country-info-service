package com.ncba.countryinfo.soap;

import static com.ncba.countryinfo.logging.LogConstants.FAILED_STATUS;
import static com.ncba.countryinfo.logging.LogConstants.IN_PROGRESS_STATUS;
import static com.ncba.countryinfo.logging.LogConstants.SUCCESS_STATUS;

import java.io.StringWriter;
import java.time.Duration;

import javax.xml.namespace.QName;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;

import com.ncba.countryinfo.logging.LogConstants;
import com.ncba.countryinfo.logging.StructuredLog;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.MDC;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.support.PayloadRootUtils;

/**
 * Logs every outbound SOAP call (request payload, response/fault payload, duration) and records
 * the {@code soap.client.requests} timer, tagged by operation and outcome, for Prometheus.
 */
@Component
public class SoapLoggingInterceptor implements ClientInterceptor {

    public static final String TARGET_SYSTEM = "CountryInfoService (SOAP)";
    private static final String START_TIME = SoapLoggingInterceptor.class.getName() + ".startTime";

    private final MeterRegistry meterRegistry;

    public SoapLoggingInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean handleRequest(MessageContext context) {
        context.setProperty(START_TIME, System.nanoTime());
        String operation = operation(context.getRequest());
        log(operation, "SOAP request sent: " + operation, "info", IN_PROGRESS_STATUS, null)
                .with("payload", payload(context.getRequest()))
                .write();
        return true;
    }

    @Override
    public boolean handleResponse(MessageContext context) {
        String operation = operation(context.getRequest());
        long elapsedMs = record(context, operation, "success");
        log(operation, "SOAP response received: " + operation, "info", SUCCESS_STATUS, "200")
                .with("payload", payload(context.getResponse()))
                .setTransactionCost(elapsedMs)
                .write();
        return true;
    }

    @Override
    public boolean handleFault(MessageContext context) {
        String operation = operation(context.getRequest());
        long elapsedMs = record(context, operation, "fault");
        log(operation, "SOAP fault received: " + operation, "error", FAILED_STATUS, "500")
                .with("payload", payload(context.getResponse()))
                .setTransactionCost(elapsedMs)
                .write();
        return true;
    }

    /** Called after every exchange; {@code ex} is set for transport errors (timeouts, refused connections). */
    @Override
    public void afterCompletion(MessageContext context, Exception ex) {
        if (ex == null) {
            return;
        }
        String operation = operation(context.getRequest());
        long elapsedMs = record(context, operation, "error");
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
        log(operation, "SOAP call failed: " + operation + " - " + describe(cause), "error",
                FAILED_STATUS, null)
                .with("errorType", cause.getClass().getSimpleName())
                .setTransactionCost(elapsedMs)
                .write();
    }

    private static String describe(Throwable t) {
        return t.getMessage() != null ? t.getClass().getSimpleName() + ": " + t.getMessage()
                : t.getClass().getSimpleName();
    }

    private StructuredLog log(String operation, String message, String level, String status,
            String responseCode) {
        return StructuredLog.of(getClass())
                .setLogMessage(message)
                .setLogLevel(level)
                .setResponseCode(responseCode)
                .setTargetEndpoint(operation)
                .setTargetSystem(TARGET_SYSTEM)
                .setProcessName("soapCall")
                .setOperationName("SOAP " + operation)
                .setLogType("SOAP")
                .setLogStatus(status)
                .setProcessId(MDC.get(LogConstants.MDC_REQUEST_ID));
    }

    /** Records the call duration once per exchange and returns it in milliseconds. */
    private long record(MessageContext context, String operation, String outcome) {
        Object start = context.getProperty(START_TIME);
        if (!(start instanceof Long startNanos)) {
            return 0;
        }
        context.removeProperty(START_TIME);
        long elapsedNanos = System.nanoTime() - startNanos;
        Timer.builder("soap.client.requests")
                .description("Outbound SOAP calls to CountryInfoService")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(Duration.ofNanos(elapsedNanos));
        return elapsedNanos / 1_000_000;
    }

    /** The SOAP operation is the local name of the request payload's root element, e.g. CountryISOCode. */
    private static String operation(WebServiceMessage message) {
        try {
            QName root = message == null ? null
                    : PayloadRootUtils.getPayloadRootQName(message.getPayloadSource(),
                            TransformerFactory.newInstance());
            return root != null ? root.getLocalPart() : "unknown";
        } catch (TransformerException e) {
            return "unknown";
        }
    }

    private static String payload(WebServiceMessage message) {
        if (message == null) {
            return null;
        }
        try {
            Source source = message.getPayloadSource();
            if (source == null) {
                return null;
            }
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(source, new StreamResult(writer));
            return writer.toString().replaceAll(">\\s+<", "><").trim();
        } catch (Exception e) {
            return "<unreadable: " + e.getMessage() + ">";
        }
    }
}
