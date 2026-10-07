package com.ncba.countryinfo.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CountryNameFormatterTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "kenya            | Kenya",
            "KENYA            | Kenya",
            "tAnZaNiA         | Tanzania",
            "'  kenya  '      | Kenya",
            "united states    | United States",
            "'south   africa' | South Africa",
            "guinea-bissau    | Guinea-Bissau"
    })
    void capitalizesEachWord(String input, String expected) {
        assertThat(CountryNameFormatter.toSentenceCase(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"NULL"})
    void returnsNullForNull(String input) {
        assertThat(CountryNameFormatter.toSentenceCase(input)).isNull();
    }
}
