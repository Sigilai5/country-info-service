package com.ncba.countryinfo.service;

import static com.ncba.countryinfo.logging.LogConstants.SUCCESS_STATUS;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.ncba.countryinfo.dto.CountryInfoResponse;
import com.ncba.countryinfo.dto.CountryUpdateRequest;
import com.ncba.countryinfo.dto.LanguageRequest;
import com.ncba.countryinfo.dto.PageResponse;
import com.ncba.countryinfo.entity.CountryInfo;
import com.ncba.countryinfo.entity.Language;
import com.ncba.countryinfo.exception.ConflictException;
import com.ncba.countryinfo.exception.CountryNotFoundException;
import com.ncba.countryinfo.exception.InvalidRequestException;
import com.ncba.countryinfo.logging.LogConstants;
import com.ncba.countryinfo.logging.StructuredLog;
import com.ncba.countryinfo.mapper.CountryInfoMapper;
import com.ncba.countryinfo.repository.CountryInfoRepository;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD on stored countries (step 7). Each method runs in one transaction and maps entities to
 * DTOs inside it, so lazy language collections are loaded safely (open-in-view is disabled).
 */
@Service
public class CountryManagementService {

    /** Only these fields may be used in {@code ?sort=}; anything else is a 400, not a SQL error. */
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "name", "isoCode", "capitalCity", "continentCode", "currencyIsoCode", "createdAt", "updatedAt");

    private final CountryInfoRepository repository;

    public CountryManagementService(CountryInfoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CountryInfoResponse> findAll(Pageable pageable) {
        long startTime = System.currentTimeMillis();
        validateSort(pageable.getSort());

        // Countries are paged in SQL; their languages are then loaded in one batched query
        // (hibernate.default_batch_fetch_size) instead of one query per country.
        Page<CountryInfoResponse> page = repository.findAll(pageable).map(CountryInfoMapper::toResponse);

        log("Fetched countries page " + page.getNumber() + " (" + page.getNumberOfElements() + " of "
                + page.getTotalElements() + ")", "findAllCountries", "Fetch all countries", startTime)
                .with("page", page.getNumber())
                .with("size", page.getSize())
                .with("totalElements", page.getTotalElements())
                .write();
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public CountryInfoResponse findById(Long id) {
        long startTime = System.currentTimeMillis();
        CountryInfoResponse country = CountryInfoMapper.toResponse(getOrThrow(id));
        log("Fetched country " + id + " (" + country.isoCode() + ")", "findCountryById",
                "Fetch country by ID", startTime)
                .with("countryId", id)
                .write();
        return country;
    }

    @Transactional
    public CountryInfoResponse update(Long id, CountryUpdateRequest request) {
        long startTime = System.currentTimeMillis();
        CountryInfo country = getOrThrow(id);

        // Optimistic locking: reject the update if the record changed since the client read it.
        if (request.version() != null && !request.version().equals(country.getVersion())) {
            throw new ConflictException("Country " + id + " was modified by someone else (current version "
                    + country.getVersion() + ", yours " + request.version() + "). Fetch it again and retry.");
        }

        String isoCode = request.isoCode().trim().toUpperCase(Locale.ROOT);
        repository.findByIsoCode(isoCode)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException("ISO code " + isoCode + " is already used by country "
                            + other.getId() + " (" + other.getName() + ")");
                });
        validateLanguages(request);

        country.setIsoCode(isoCode);
        country.setName(request.name().trim());
        country.setCapitalCity(trimToNull(request.capitalCity()));
        country.setPhoneCode(trimToNull(request.phoneCode()));
        country.setContinentCode(upperOrNull(request.continentCode()));
        country.setCurrencyIsoCode(upperOrNull(request.currencyIsoCode()));
        country.setCountryFlag(trimToNull(request.countryFlag()));
        country.replaceLanguages(request.languages().stream()
                .map(l -> new Language(l.isoCode().trim().toLowerCase(Locale.ROOT), l.name().trim()))
                .toList());

        // Flush now so version/timestamp changes and any constraint or locking failure surface here.
        CountryInfo saved = repository.saveAndFlush(country);
        log("Updated country " + id + " (" + saved.getIsoCode() + "), now version " + saved.getVersion(),
                "updateCountry", "Update country", startTime)
                .with("countryId", id)
                .with("version", saved.getVersion())
                .with("languageCount", saved.getLanguages().size())
                .write();
        return CountryInfoMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        long startTime = System.currentTimeMillis();
        CountryInfo country = getOrThrow(id);
        repository.delete(country); // languages are removed with it (cascade + ON DELETE CASCADE)
        log("Deleted country " + id + " (" + country.getIsoCode() + ")", "deleteCountry", "Delete country",
                startTime)
                .with("countryId", id)
                .with("isoCode", country.getIsoCode())
                .write();
    }

    private CountryInfo getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CountryNotFoundException("Country with id " + id + " not found"));
    }

    private static void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidRequestException("Cannot sort by '" + order.getProperty() + "'. Allowed: "
                        + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()));
            }
        }
    }

    private static void validateLanguages(CountryUpdateRequest request) {
        Set<String> seen = new HashSet<>();
        for (LanguageRequest language : request.languages()) {
            if (language == null) {
                throw new InvalidRequestException("languages must not contain null entries");
            }
            if (!seen.add(language.isoCode().trim().toLowerCase(Locale.ROOT))) {
                throw new InvalidRequestException("Duplicate language isoCode '" + language.isoCode() + "'");
            }
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String upperOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private StructuredLog log(String message, String processName, String operation, long startTime) {
        return StructuredLog.of(getClass())
                .setLogMessage(message)
                .setLogLevel("info")
                .setTargetEndpoint(getClass().getName() + "." + processName)
                .setTargetSystem("MySQL")
                .setProcessName(processName)
                .setOperationName(operation)
                .setLogType("BUSINESS")
                .setLogStatus(SUCCESS_STATUS)
                .setProcessId(MDC.get(LogConstants.MDC_REQUEST_ID))
                .setTransactionCost(System.currentTimeMillis() - startTime);
    }
}
