package com.ncba.countryinfo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import com.ncba.countryinfo.entity.CountryInfo;
import com.ncba.countryinfo.exception.CountryNotFoundException;
import com.ncba.countryinfo.exception.UpstreamServiceException;
import com.ncba.countryinfo.mapper.CountryInfoMapper;
import com.ncba.countryinfo.repository.CountryInfoRepository;
import com.ncba.countryinfo.service.CountryManagementService;
import com.ncba.countryinfo.service.CountryService;
import com.ncba.countryinfo.soap.CountryInfoSoapClient;
import com.ncba.countryinfo.soap.model.CountryDetails;
import com.ncba.countryinfo.soap.model.LanguageDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CountryController.class)
@Import(CountryService.class)
class CountryControllerTest {

    private static final CountryDetails KENYA = new CountryDetails("KE", "Kenya", "Nairobi", "254", "AF",
            "KES", "http://www.oorsprong.org/WebSamples.CountryInfo/Flags/Kenya.jpg",
            List.of(new LanguageDetails("swa", "Swahili")));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CountryInfoSoapClient soapClient;

    @MockitoBean
    private CountryInfoRepository repository;

    @MockitoBean
    private CountryManagementService managementService;

    @Test
    void fetchesStoresAndReturns201() throws Exception {
        given(repository.findByName("Kenya")).willReturn(Optional.empty());
        given(repository.findByIsoCode("KE")).willReturn(Optional.empty());
        given(soapClient.getIsoCode("Kenya")).willReturn("KE");
        given(soapClient.getFullCountryInfo("KE")).willReturn(KENYA);
        given(repository.saveAndFlush(any(CountryInfo.class))).willAnswer(inv -> withId(inv.getArgument(0), 1L));

        mockMvc.perform(post("/api/v1/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"kenya\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/countries/1"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.responseCode").value("201"))
                .andExpect(jsonPath("$.responseMessage").value("Country information stored"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Kenya"))
                .andExpect(jsonPath("$.data.isoCode").value("KE"))
                .andExpect(jsonPath("$.data.capitalCity").value("Nairobi"))
                .andExpect(jsonPath("$.data.phoneCode").value("254"))
                .andExpect(jsonPath("$.data.currencyIsoCode").value("KES"))
                .andExpect(jsonPath("$.data.languages[0].isoCode").value("swa"))
                .andExpect(jsonPath("$.data.languages[0].name").value("Swahili"));
    }

    @Test
    void returns409WithExistingRecordWhenCountryAlreadyStored() throws Exception {
        given(repository.findByName("Kenya"))
                .willReturn(Optional.of(withId(CountryInfoMapper.toEntity(KENYA), 7L)));

        mockMvc.perform(post("/api/v1/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"KENYA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.responseCode").value("409"))
                .andExpect(jsonPath("$.responseMessage").value("Country 'Kenya' (KE) already exists with id 7"))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.isoCode").value("KE"));
    }

    @Test
    void returns404WhenCountryUnknown() throws Exception {
        given(repository.findByName(anyString())).willReturn(Optional.empty());
        given(soapClient.getIsoCode(anyString()))
                .willThrow(new CountryNotFoundException("No country found with the name 'Narnia'"));

        mockMvc.perform(post("/api/v1/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"narnia\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.responseCode").value("404"))
                .andExpect(jsonPath("$.responseMessage").value("No country found with the name 'Narnia'"));
    }

    @Test
    void returns404WhenFullCountryInfoMissing() throws Exception {
        given(repository.findByName(anyString())).willReturn(Optional.empty());
        given(repository.findByIsoCode(anyString())).willReturn(Optional.empty());
        given(soapClient.getIsoCode("Kenya")).willReturn("KE");
        given(soapClient.getFullCountryInfo("KE"))
                .willThrow(new CountryNotFoundException("No country information found for ISO code 'KE'"));

        mockMvc.perform(post("/api/v1/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"kenya\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.responseCode").value("404"));
    }

    @Test
    void returns503WhenSoapServiceUnavailable() throws Exception {
        given(repository.findByName(anyString())).willReturn(Optional.empty());
        given(soapClient.getIsoCode(anyString())).willThrow(new UpstreamServiceException(
                "Country lookup service is not responding. Please try again later.", null));

        mockMvc.perform(post("/api/v1/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"kenya\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.responseCode").value("503"));
    }

    @Test
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.responseCode").value("400"))
                .andExpect(jsonPath("$.responseMessage").value("Request validation failed"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors.name").value("name is required"));
    }

    @Test
    void rejectsInvalidCharacters() throws Exception {
        mockMvc.perform(post("/api/v1/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"<script>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.responseCode").value("400"))
                .andExpect(jsonPath("$.responseMessage").value("Malformed or unreadable JSON request body"));
    }

    @Test
    void rejectsUnsupportedMethod() throws Exception {
        mockMvc.perform(put("/api/v1/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"kenya\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.responseCode").value("405"));
    }

    /** Simulates the database assigning an ID (the entity has no public ID setter). */
    private static CountryInfo withId(CountryInfo entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
