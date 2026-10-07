package com.ncba.countryinfo.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.ncba.countryinfo.entity.CountryInfo;
import com.ncba.countryinfo.entity.Language;
import com.ncba.countryinfo.repository.CountryInfoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD against the real MySQL schema (Flyway-migrated). Each test runs in a transaction that is
 * rolled back afterwards, so the local database is left untouched. Requires MySQL to be running
 * (see README). No SOAP calls are made: data is inserted directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CountryCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CountryInfoRepository repository;

    @Autowired
    private EntityManager entityManager;

    private Long id;

    @BeforeEach
    void insertTestCountry() {
        CountryInfo country = new CountryInfo();
        country.setIsoCode("XT");
        country.setName("Testland");
        country.setCapitalCity("Old Capital");
        country.replaceLanguages(List.of(new Language("swa", "Swahili"), new Language("fra", "French")));
        id = repository.saveAndFlush(country).getId();
        entityManager.clear();
    }

    @Test
    void getsStoredCountryWithLanguages() throws Exception {
        mockMvc.perform(get("/api/v1/countries/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isoCode").value("XT"))
                .andExpect(jsonPath("$.data.languages.length()").value(2))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    void updateKeepsRenamesAddsAndRemovesLanguagesWithoutConstraintViolation() throws Exception {
        // swa is kept (renamed), fra is removed, eng is added
        mockMvc.perform(put("/api/v1/countries/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"isoCode":"xt","name":"Testland","capitalCity":"New Capital",
                         "languages":[{"isoCode":"SWA","name":"Kiswahili"},{"isoCode":"eng","name":"English"}],
                         "version":0}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isoCode").value("XT"))
                .andExpect(jsonPath("$.data.capitalCity").value("New Capital"))
                .andExpect(jsonPath("$.data.version").value(1));

        entityManager.clear();
        CountryInfo reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getLanguages()).extracting(Language::getIsoCode).containsExactlyInAnyOrder("swa", "eng");
        assertThat(reloaded.getLanguages()).extracting(Language::getName).contains("Kiswahili");
    }

    @Test
    void staleVersionIsRejectedWith409() throws Exception {
        String body = """
                {"isoCode":"XT","name":"Testland","languages":[],"version":0}
                """;
        mockMvc.perform(put("/api/v1/countries/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        entityManager.clear();

        mockMvc.perform(put("/api/v1/countries/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.responseMessage").value(org.hamcrest.Matchers.containsString("modified by someone else")));
    }

    @Test
    void deleteRemovesCountryAndItsLanguages() throws Exception {
        mockMvc.perform(delete("/api/v1/countries/" + id)).andExpect(status().isOk());
        entityManager.flush();

        assertThat(repository.findById(id)).isEmpty();
        Long orphans = entityManager.createQuery(
                "select count(l) from Language l where l.country.id = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        assertThat(orphans).isZero();
        mockMvc.perform(get("/api/v1/countries/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/v1/countries?sort=password,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.responseMessage").value(org.hamcrest.Matchers.startsWith("Cannot sort by 'password'")));
    }
}
