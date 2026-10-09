package com.ncba.countryinfo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;

import com.ncba.countryinfo.dto.CountryInfoResponse;
import com.ncba.countryinfo.entity.CountryInfo;
import com.ncba.countryinfo.exception.CountryAlreadyExistsException;
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
    void storedCountryIsRejectedWithoutCallingSoap() {
        given(repository.findByName("Uganda")).willReturn(Optional.of(stored(UGANDA, 3L)));

        assertThatThrownBy(() -> service.processCountry("  uganda "))
                .isInstanceOf(CountryAlreadyExistsException.class)
                .hasMessageContaining("already exists with id 3")
                .extracting(ex -> ((CountryAlreadyExistsException) ex).getExisting().id())
                .isEqualTo(3L);
        verifyNoInteractions(soapClient);
    }

    @Test
    void storedCountryMatchedByIsoCodeSkipsFullInfoCall() {
        given(repository.findByName("Uganda")).willReturn(Optional.empty());
        given(soapClient.getIsoCode("Uganda")).willReturn("UG");
        given(repository.findByIsoCode("UG")).willReturn(Optional.of(stored(UGANDA, 3L)));

        assertThatThrownBy(() -> service.processCountry("uganda"))
                .isInstanceOf(CountryAlreadyExistsException.class);
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

        CountryInfoResponse created = service.processCountry("uganda");

        assertThat(created.id()).isEqualTo(5L);
        assertThat(created.capitalCity()).isEqualTo("Kampala");
        assertThat(created.languages()).extracting("name").containsExactly("English");
    }

    @Test
    void concurrentInsertIsRejectedWithTheRowStoredByTheOtherRequest() {
        given(repository.findByName("Uganda")).willReturn(Optional.empty());
        given(soapClient.getIsoCode("Uganda")).willReturn("UG");
        given(repository.findByIsoCode("UG"))
                .willReturn(Optional.empty())                  // before insert: not there yet
                .willReturn(Optional.of(stored(UGANDA, 9L)));  // after duplicate-key: the winner's row
        given(soapClient.getFullCountryInfo("UG")).willReturn(UGANDA);
        given(repository.saveAndFlush(any(CountryInfo.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate entry 'UG' for key 'uk_country_info_iso_code'"));

        assertThatThrownBy(() -> service.processCountry("uganda"))
                .isInstanceOf(CountryAlreadyExistsException.class)
                .extracting(ex -> ((CountryAlreadyExistsException) ex).getExisting().id())
                .isEqualTo(9L);
    }

    private static CountryInfo stored(CountryDetails details, Long id) {
        return withId(CountryInfoMapper.toEntity(details), id);
    }

    private static CountryInfo withId(CountryInfo entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
