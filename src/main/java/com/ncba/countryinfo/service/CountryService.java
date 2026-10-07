package com.ncba.countryinfo.service;

import static com.ncba.countryinfo.logging.LogConstants.FAILED_STATUS;
import static com.ncba.countryinfo.logging.LogConstants.SUCCESS_STATUS;

import java.util.Optional;

import com.ncba.countryinfo.entity.CountryInfo;
import com.ncba.countryinfo.exception.CountryNotFoundException;
import com.ncba.countryinfo.logging.LogConstants;
import com.ncba.countryinfo.logging.StructuredLog;
import com.ncba.countryinfo.mapper.CountryInfoMapper;
import com.ncba.countryinfo.repository.CountryInfoRepository;
import com.ncba.countryinfo.soap.CountryInfoSoapClient;
import com.ncba.countryinfo.soap.model.CountryDetails;
import com.ncba.countryinfo.util.CountryNameFormatter;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class CountryService {

    private final CountryInfoSoapClient soapClient;
    private final CountryInfoRepository repository;

    public CountryService(CountryInfoSoapClient soapClient, CountryInfoRepository repository) {
        this.soapClient = soapClient;
        this.repository = repository;
    }

    /**
     * Name -> normalized name (step 3) -> ISO code (step 4) -> full country info (step 5) -> stored
     * in MySQL (step 6).
     *
     * <p>Idempotent: a country that is already stored is returned as-is without calling SOAP. No
     * database transaction is held open during the (slow, remote) SOAP calls; the country and its
     * languages are then saved atomically in one short transaction.
     */
    public CountryResult processCountry(String rawName) {
        long startTime = System.currentTimeMillis();

        // Step 3: normalize the name ("kenya" -> "Kenya")
        String name = CountryNameFormatter.toSentenceCase(rawName);
        log("Country name normalized: '" + rawName + "' -> '" + name + "'", "info", SUCCESS_STATUS,
                "Country name normalization", startTime)
                .with("countryName", name)
                .write();

        // Already stored? Serve it from the database, no SOAP calls needed.
        Optional<CountryInfo> stored = repository.findByName(name);
        if (stored.isPresent()) {
            return existing(stored.get(), "name '" + name + "'", startTime);
        }

        // Step 4: resolve the ISO code via CountryISOCode
        String isoCode;
        try {
            isoCode = soapClient.getIsoCode(name);
        } catch (CountryNotFoundException ex) {
            log("ISO code lookup found no country for '" + name + "'", "warn", FAILED_STATUS,
                    "ISO code lookup", startTime)
                    .with("countryName", name)
                    .write();
            throw ex;
        }
        log("ISO code resolved: '" + name + "' -> " + isoCode, "info", SUCCESS_STATUS,
                "ISO code lookup", startTime)
                .with("countryName", name)
                .with("isoCode", isoCode)
                .write();

        // The stored name may differ from the input (e.g. an alias), so also check by ISO code.
        stored = repository.findByIsoCode(isoCode);
        if (stored.isPresent()) {
            return existing(stored.get(), "ISO code " + isoCode, startTime);
        }

        // Step 5: use the ISO code to fetch the full country info via FullCountryInfo
        CountryDetails details;
        try {
            details = soapClient.getFullCountryInfo(isoCode);
        } catch (CountryNotFoundException ex) {
            log("Full country info not found for ISO code " + isoCode, "warn", FAILED_STATUS,
                    "Full country info lookup", startTime)
                    .with("isoCode", isoCode)
                    .write();
            throw ex;
        }
        log("Full country info retrieved for " + isoCode + " (" + details.name() + ")", "info",
                SUCCESS_STATUS, "Full country info lookup", startTime)
                .with("isoCode", isoCode)
                .with("capitalCity", details.capitalCity())
                .with("languageCount", details.languages().size())
                .write();

        // Step 6: store the country and its languages
        CountryInfo saved;
        try {
            saved = repository.saveAndFlush(CountryInfoMapper.toEntity(details));
        } catch (DataIntegrityViolationException ex) {
            // Another request stored the same country between our check and our insert (unique iso_code).
            CountryInfo winner = repository.findByIsoCode(isoCode).orElseThrow(() -> ex);
            return existing(winner, "ISO code " + isoCode + " (stored by a concurrent request)", startTime);
        }
        log("Country stored: " + saved.getName() + " (" + saved.getIsoCode() + ") with id " + saved.getId(),
                "info", SUCCESS_STATUS, "Country persistence", startTime)
                .setTargetSystem("MySQL")
                .setResponseCode("201")
                .with("countryId", saved.getId())
                .with("isoCode", saved.getIsoCode())
                .with("languageCount", saved.getLanguages().size())
                .write();

        return new CountryResult(CountryInfoMapper.toResponse(saved), true);
    }

    private CountryResult existing(CountryInfo country, String matchedBy, long startTime) {
        log("Country already stored, matched by " + matchedBy + ": id " + country.getId()
                + " - returning stored record without calling SOAP", "info", SUCCESS_STATUS,
                "Country lookup (database)", startTime)
                .setTargetSystem("MySQL")
                .setResponseCode("200")
                .with("countryId", country.getId())
                .with("isoCode", country.getIsoCode())
                .write();
        return new CountryResult(CountryInfoMapper.toResponse(country), false);
    }

    private StructuredLog log(String message, String level, String status, String operation,
            long startTime) {
        return StructuredLog.of(getClass())
                .setLogMessage(message)
                .setLogLevel(level)
                .setTargetEndpoint(getClass().getName() + ".processCountry")
                .setTargetSystem(LogConstants.THIS_SYSTEM)
                .setProcessName("processCountry")
                .setOperationName(operation)
                .setLogType("BUSINESS")
                .setLogStatus(status)
                .setProcessId(MDC.get(LogConstants.MDC_REQUEST_ID))
                .setTransactionCost(System.currentTimeMillis() - startTime);
    }
}
