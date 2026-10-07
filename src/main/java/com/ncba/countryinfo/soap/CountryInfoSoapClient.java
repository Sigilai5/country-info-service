package com.ncba.countryinfo.soap;

import java.util.List;

import com.ncba.countryinfo.exception.CountryNotFoundException;
import com.ncba.countryinfo.exception.UpstreamServiceException;
import com.ncba.countryinfo.soap.generated.CountryISOCode;
import com.ncba.countryinfo.soap.generated.CountryISOCodeResponse;
import com.ncba.countryinfo.soap.generated.FullCountryInfo;
import com.ncba.countryinfo.soap.generated.FullCountryInfoResponse;
import com.ncba.countryinfo.soap.generated.TCountryInfo;
import com.ncba.countryinfo.soap.model.CountryDetails;
import com.ncba.countryinfo.soap.model.LanguageDetails;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;

/**
 * Client for the CountryInfoService SOAP API.
 *
 * <p>Resilience (configured under {@code resilience4j.*} in application.properties):
 * <ul>
 *   <li>Retry: transient failures (I/O errors, timeouts, HTTP 5xx) are retried with exponential backoff.</li>
 *   <li>Circuit breaker: after repeated failures calls fail fast for a while instead of piling up
 *       on a dead upstream; the fallback turns this into a 503.</li>
 *   <li>Cache: country reference data rarely changes, so successful lookups are cached (failures are not).</li>
 * </ul>
 * "Country not found" is a valid business answer: it is not retried and does not trip the breaker.
 * Both operations share one breaker, since they hit the same upstream host.
 */
@Component
public class CountryInfoSoapClient {

    static final String RESILIENCE_NAME = "countryInfoSoap";
    static final String ISO_NOT_FOUND_RESULT = "No country found by that name";
    static final String INFO_NOT_FOUND_RESULT = "Country not found in the database";

    private final WebServiceTemplate webServiceTemplate;

    public CountryInfoSoapClient(WebServiceTemplate countryInfoWebServiceTemplate) {
        this.webServiceTemplate = countryInfoWebServiceTemplate;
    }

    /** Step 4 - CountryISOCode operation, e.g. "Kenya" -> "KE". The name must already be normalized. */
    @Cacheable(cacheNames = "isoCodes", key = "#countryName")
    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "isoCodeFallback")
    public String getIsoCode(String countryName) {
        CountryISOCode request = new CountryISOCode();
        request.setSCountryName(countryName);

        CountryISOCodeResponse response = call(request, CountryISOCodeResponse.class);
        String isoCode = response == null ? null : response.getCountryISOCodeResult();

        if (isBlank(isoCode) || ISO_NOT_FOUND_RESULT.equalsIgnoreCase(isoCode.trim())) {
            throw new CountryNotFoundException("No country found with the name '" + countryName + "'");
        }
        return isoCode.trim();
    }

    /** Step 5 - FullCountryInfo operation, e.g. "KE" -> Kenya, Nairobi, 254, AF, KES, flag, [Swahili]. */
    @Cacheable(cacheNames = "countryInfo", key = "#isoCode")
    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME, fallbackMethod = "fullCountryInfoFallback")
    public CountryDetails getFullCountryInfo(String isoCode) {
        FullCountryInfo request = new FullCountryInfo();
        request.setSCountryISOCode(isoCode);

        FullCountryInfoResponse response = call(request, FullCountryInfoResponse.class);
        TCountryInfo info = response == null ? null : response.getFullCountryInfoResult();

        // Unknown codes are not a SOAP fault: the service returns an empty record whose name is
        // "Country not found in the database".
        if (info == null || isBlank(info.getSISOCode())
                || INFO_NOT_FOUND_RESULT.equalsIgnoreCase(trim(info.getSName()))) {
            throw new CountryNotFoundException("No country information found for ISO code '" + isoCode + "'");
        }
        return toCountryDetails(info);
    }

    /** Sends the request and checks the reply is the expected type (a mismatch means a broken contract). */
    private <T> T call(Object request, Class<T> responseType) {
        Object response = webServiceTemplate.marshalSendAndReceive(request);
        if (response != null && !responseType.isInstance(response)) {
            throw new IllegalStateException("Unexpected SOAP response: expected " + responseType.getSimpleName()
                    + " but received " + response.getClass().getSimpleName());
        }
        return responseType.cast(response);
    }

    private static CountryDetails toCountryDetails(TCountryInfo info) {
        List<LanguageDetails> languages = info.getLanguages() == null ? List.of()
                : info.getLanguages().getTLanguage().stream()
                        .map(l -> new LanguageDetails(trim(l.getSISOCode()), trim(l.getSName())))
                        .toList();
        return new CountryDetails(
                trim(info.getSISOCode()),
                trim(info.getSName()),
                trim(info.getSCapitalCity()),
                trim(info.getSPhoneCode()),
                trim(info.getSContinentCode()),
                trim(info.getSCurrencyISOCode()),
                trim(info.getSCountryFlag()),
                languages);
    }

    // Fallbacks: Resilience4j requires the same return type as the protected method, so each
    // operation has its own thin fallback delegating to toFailure().

    @SuppressWarnings("unused")
    private String isoCodeFallback(String countryName, Throwable ex) {
        throw toFailure(ex);
    }

    @SuppressWarnings("unused")
    private CountryDetails fullCountryInfoFallback(String isoCode, Throwable ex) {
        throw toFailure(ex);
    }

    /**
     * "Not found" passes through unchanged (404); an open circuit or an exhausted retry becomes
     * an UpstreamServiceException (503).
     */
    private static RuntimeException toFailure(Throwable ex) {
        return switch (ex) {
            case CountryNotFoundException notFound -> notFound;
            case CallNotPermittedException open -> new UpstreamServiceException(
                    "Country lookup service is temporarily unavailable. Please try again shortly.", open);
            default -> new UpstreamServiceException(
                    "Country lookup service is not responding. Please try again later.", ex);
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
