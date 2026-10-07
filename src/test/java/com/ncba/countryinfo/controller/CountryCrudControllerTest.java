package com.ncba.countryinfo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLTransientConnectionException;
import java.time.Instant;
import java.util.List;

import com.ncba.countryinfo.dto.CountryInfoResponse;
import com.ncba.countryinfo.dto.LanguageResponse;
import com.ncba.countryinfo.dto.PageResponse;
import com.ncba.countryinfo.exception.ConflictException;
import com.ncba.countryinfo.exception.CountryNotFoundException;
import com.ncba.countryinfo.exception.DatabaseTimeoutException;
import com.ncba.countryinfo.service.CountryManagementService;
import com.ncba.countryinfo.service.CountryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

/** HTTP contract of the CRUD endpoints (status codes, envelope, validation); the service is mocked. */
@WebMvcTest(CountryController.class)
class CountryCrudControllerTest {

    private static final CountryInfoResponse KENYA = new CountryInfoResponse(1L, "KE", "Kenya", "Nairobi", "254",
            "AF", "KES", "http://www.oorsprong.org/WebSamples.CountryInfo/Flags/Kenya.jpg",
            List.of(new LanguageResponse("swa", "Swahili")), 0L, Instant.now(), Instant.now());

    private static final String VALID_UPDATE = """
            {"isoCode":"KE","name":"Kenya","capitalCity":"Nairobi","phoneCode":"254","continentCode":"AF",
             "currencyIsoCode":"KES","countryFlag":"http://example.com/ke.jpg",
             "languages":[{"isoCode":"swa","name":"Swahili"},{"isoCode":"eng","name":"English"}],"version":0}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CountryManagementService managementService;

    @MockitoBean
    private CountryService countryService;

    @Test
    void listsCountriesWithPagingMetadata() throws Exception {
        given(managementService.findAll(any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(KENYA), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/countries?page=0&size=20&sort=name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("200"))
                .andExpect(jsonPath("$.data.content[0].isoCode").value("KE"))
                .andExpect(jsonPath("$.data.content[0].languages[0].name").value("Swahili"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void getsCountryById() throws Exception {
        given(managementService.findById(1L)).willReturn(KENYA);

        mockMvc.perform(get("/api/v1/countries/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Kenya"))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    void returns404ForUnknownId() throws Exception {
        given(managementService.findById(99L)).willThrow(new CountryNotFoundException("Country with id 99 not found"));

        mockMvc.perform(get("/api/v1/countries/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.responseCode").value("404"))
                .andExpect(jsonPath("$.responseMessage").value("Country with id 99 not found"));
    }

    @Test
    void returns400ForNonNumericOrNonPositiveId() throws Exception {
        mockMvc.perform(get("/api/v1/countries/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.responseMessage").value("Invalid value 'abc' for parameter 'id'"));
        mockMvc.perform(get("/api/v1/countries/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").exists());
        verifyNoInteractions(managementService);
    }

    @Test
    void updatesCountry() throws Exception {
        given(managementService.update(eq(1L), any())).willReturn(KENYA);

        mockMvc.perform(put("/api/v1/countries/1").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseMessage").value("Country updated"))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void rejectsInvalidUpdateWithFieldErrors() throws Exception {
        mockMvc.perform(put("/api/v1/countries/1").contentType(MediaType.APPLICATION_JSON).content("""
                        {"isoCode":"K1","name":"","currencyIsoCode":"SHILLING","countryFlag":"not a url",
                         "languages":[{"isoCode":"","name":"Swahili"}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.responseMessage").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.isoCode").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.currencyIsoCode").exists())
                .andExpect(jsonPath("$.errors.countryFlag").exists())
                .andExpect(jsonPath("$.errors['languages[0].isoCode']").exists());
        verifyNoInteractions(managementService);
    }

    @Test
    void returns409OnConflict() throws Exception {
        given(managementService.update(eq(1L), any()))
                .willThrow(new ConflictException("Country 1 was modified by someone else"));

        mockMvc.perform(put("/api/v1/countries/1").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.responseCode").value("409"));
    }

    @Test
    void returns503WhenDatabaseTimesOut() throws Exception {
        given(managementService.findById(1L)).willThrow(new DatabaseTimeoutException(
                "The database did not respond in time (JpaRepository.saveAndFlush)",
                new CannotAcquireLockException("Lock wait timeout exceeded; try restarting transaction")));

        mockMvc.perform(get("/api/v1/countries/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.responseCode").value("503"))
                .andExpect(jsonPath("$.responseMessage")
                        .value("The database is taking too long to respond. Please try again shortly."));
    }

    @Test
    void returns503WhenNoDatabaseConnectionAvailableInTime() throws Exception {
        given(managementService.findAll(any(Pageable.class))).willThrow(new CannotCreateTransactionException(
                "Could not open JPA EntityManager for transaction",
                new SQLTransientConnectionException("HikariPool-1 - Connection is not available, request timed out after 5000ms")));

        mockMvc.perform(get("/api/v1/countries"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.responseMessage")
                        .value("The database is taking too long to respond. Please try again shortly."));
    }

    @Test
    void returns503WhenDatabaseIsDown() throws Exception {
        given(managementService.findById(1L))
                .willThrow(new DataAccessResourceFailureException("Communications link failure"));

        mockMvc.perform(get("/api/v1/countries/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.responseMessage")
                        .value("The database is temporarily unavailable. Please try again shortly."));
    }

    @Test
    void deletesCountry() throws Exception {
        mockMvc.perform(delete("/api/v1/countries/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseMessage").value("Country 1 deleted"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void returns404WhenDeletingUnknownId() throws Exception {
        willThrow(new CountryNotFoundException("Country with id 99 not found")).given(managementService).delete(99L);

        mockMvc.perform(delete("/api/v1/countries/99"))
                .andExpect(status().isNotFound());
    }
}
