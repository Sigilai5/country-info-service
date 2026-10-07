package com.ncba.countryinfo.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.payload;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

import java.util.List;

import javax.xml.transform.Source;

import com.ncba.countryinfo.exception.CountryNotFoundException;
import com.ncba.countryinfo.soap.model.CountryDetails;
import com.ncba.countryinfo.soap.model.LanguageDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;

/** Verifies marshalling and response handling against a mock SOAP server (no network). */
class CountryInfoSoapClientTest {

    private static final String NS = "http://www.oorsprong.org/websamples.countryinfo";

    private MockWebServiceServer server;
    private CountryInfoSoapClient client;

    @BeforeEach
    void setUp() throws Exception {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("com.ncba.countryinfo.soap.generated");
        marshaller.afterPropertiesSet();
        WebServiceTemplate template = new WebServiceTemplate(marshaller);
        template.setDefaultUri("http://localhost/CountryInfoService.wso");
        server = MockWebServiceServer.createServer(template);
        client = new CountryInfoSoapClient(template);
    }

    @Test
    void returnsIsoCode() {
        server.expect(payload(request("Kenya")))
                .andRespond(withPayload(response("KE")));

        assertThat(client.getIsoCode("Kenya")).isEqualTo("KE");
        server.verify();
    }

    @Test
    void throwsNotFoundForUnknownCountry() {
        server.expect(payload(request("Narnia")))
                .andRespond(withPayload(response("No country found by that name")));

        assertThatThrownBy(() -> client.getIsoCode("Narnia"))
                .isInstanceOf(CountryNotFoundException.class)
                .hasMessageContaining("Narnia");
    }

    @Test
    void returnsFullCountryInfo() {
        server.expect(payload(new StringSource("<FullCountryInfo xmlns=\"" + NS + "\"><sCountryISOCode>KE"
                        + "</sCountryISOCode></FullCountryInfo>")))
                .andRespond(withPayload(fullInfoResponse("KE", "Kenya", "Nairobi", "254", "AF", "KES",
                        "http://www.oorsprong.org/WebSamples.CountryInfo/Flags/Kenya.jpg",
                        "<m:tLanguage><m:sISOCode>swa</m:sISOCode><m:sName>Swahili</m:sName></m:tLanguage>")));

        CountryDetails details = client.getFullCountryInfo("KE");

        assertThat(details).isEqualTo(new CountryDetails("KE", "Kenya", "Nairobi", "254", "AF", "KES",
                "http://www.oorsprong.org/WebSamples.CountryInfo/Flags/Kenya.jpg",
                List.of(new LanguageDetails("swa", "Swahili"))));
        server.verify();
    }

    @Test
    void throwsNotFoundForUnknownIsoCode() {
        // The real service answers unknown codes with an empty record, not a SOAP fault
        server.expect(payload(new StringSource("<FullCountryInfo xmlns=\"" + NS + "\"><sCountryISOCode>ZZ"
                        + "</sCountryISOCode></FullCountryInfo>")))
                .andRespond(withPayload(fullInfoResponse("", "Country not found in the database",
                        "", "", "", "", "", "")));

        assertThatThrownBy(() -> client.getFullCountryInfo("ZZ"))
                .isInstanceOf(CountryNotFoundException.class)
                .hasMessageContaining("ZZ");
    }

    private static Source fullInfoResponse(String iso, String name, String capital, String phone,
            String continent, String currency, String flag, String languagesXml) {
        return new StringSource("<m:FullCountryInfoResponse xmlns:m=\"" + NS + "\"><m:FullCountryInfoResult>"
                + "<m:sISOCode>" + iso + "</m:sISOCode><m:sName>" + name + "</m:sName>"
                + "<m:sCapitalCity>" + capital + "</m:sCapitalCity><m:sPhoneCode>" + phone + "</m:sPhoneCode>"
                + "<m:sContinentCode>" + continent + "</m:sContinentCode>"
                + "<m:sCurrencyISOCode>" + currency + "</m:sCurrencyISOCode>"
                + "<m:sCountryFlag>" + flag + "</m:sCountryFlag>"
                + "<m:Languages>" + languagesXml + "</m:Languages>"
                + "</m:FullCountryInfoResult></m:FullCountryInfoResponse>");
    }

    private static Source request(String name) {
        return new StringSource("<CountryISOCode xmlns=\"" + NS + "\"><sCountryName>" + name
                + "</sCountryName></CountryISOCode>");
    }

    private static Source response(String result) {
        return new StringSource("<m:CountryISOCodeResponse xmlns:m=\"" + NS + "\"><m:CountryISOCodeResult>"
                + result + "</m:CountryISOCodeResult></m:CountryISOCodeResponse>");
    }
}
