package com.ncba.countryinfo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;

import com.ncba.countryinfo.entity.CountryInfo;
import com.ncba.countryinfo.mapper.CountryInfoMapper;
import com.ncba.countryinfo.repository.CountryInfoRepository;
import com.ncba.countryinfo.soap.CountryInfoSoapClient;
import com.ncba.countryinfo.soap.model.CountryDetails;
import com.ncba.countryinfo.soap.model.LanguageDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class CountryServiceTest {

    private static final CountryDetails UGANDA = new CountryDetails("UG", "Uganda", "Kampala", "256", "AF",
            "UGX", "http://www.oorsprong.org/WebSamples.CountryInfo/Flags/Uganda.jpg",
            List.of(new LanguageDetails("eng", "English")));

    private CountryInfoSoapClient soapClient;
    private CountryInfoRepository repository;
    private CountryService service;

    @BeforeEach
    void setUp() {
        soapClient = mock(CountryInfoSoapClient.class);
        repository = mock(CountryInfoRepository.class);
        service = new CountryService(soapClient, repository);
    }

    @Test
    void storedCountryIsReturnedWithoutCallingSoap() {
        given(repository.findByName("Uganda")).willReturn(Optional.of(stored(UGANDA, 3L)));

        CountryResult result = service.processCountry("  uganda ");

        assertThat(result.created()).isFalse();
        assertThat(result.country().id()).isEqualTo(3L);
        verifyNoInteractions(soapClient);
    }

    @Test
    void storedCountryMatchedByIsoCodeSkipsFullInfoCall() {
        given(repository.findByName("Uganda")).willReturn(Optional.empty());
        given(soapClient.getIsoCode("Uganda")).willReturn("UG");
        given(repository.findByIsoCode("UG")).willReturn(Optional.of(stored(UGANDA, 3L)));

        CountryResult result = service.processCountry("uganda");

        assertThat(result.created()).isFalse();
        verify(soapClient, never()).getFullCountryInfo(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void newCountryIsFetchedAndStored() {
        given(repository.findByName("Uganda")).willReturn(Optional.empty());
        given(soapClient.getIsoCode("Uganda")).willReturn("UG");
        given(repository.findByIsoCode("UG")).willReturn(Optional.empty());
        given(soapClient.getFullCountryInfo("UG")).willReturn(UGANDA);
        given(repository.saveAndFlush(any(CountryInfo.class))).willAnswer(inv -> withId(inv.getArgument(0), 5L));

        CountryResult result = service.processCountry("uganda");

        assertThat(result.created()).isTrue();
        assertThat(result.country().id()).isEqualTo(5L);
        assertThat(result.country().capitalCity()).isEqualTo("Kampala");
        assertThat(result.country().languages()).extracting("name").containsExactly("English");
    }

    @Test
    void concurrentInsertReturnsTheRowStoredByTheOtherRequest() {
        given(repository.findByName("Uganda")).willReturn(Optional.empty());
        given(soapClient.getIsoCode("Uganda")).willReturn("UG");
        given(repository.findByIsoCode("UG"))
                .willReturn(Optional.empty())                  // before insert: not there yet
                .willReturn(Optional.of(stored(UGANDA, 9L)));  // after duplicate-key: the winner's row
        given(soapClient.getFullCountryInfo("UG")).willReturn(UGANDA);
        given(repository.saveAndFlush(any(CountryInfo.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate entry 'UG' for key 'uk_country_info_iso_code'"));

        CountryResult result = service.processCountry("uganda");

        assertThat(result.created()).isFalse();
        assertThat(result.country().id()).isEqualTo(9L);
    }

    private static CountryInfo stored(CountryDetails details, Long id) {
        return withId(CountryInfoMapper.toEntity(details), id);
    }

    private static CountryInfo withId(CountryInfo entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
