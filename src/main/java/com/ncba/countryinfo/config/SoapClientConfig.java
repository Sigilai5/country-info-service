package com.ncba.countryinfo.config;

import java.net.http.HttpClient;

import com.ncba.countryinfo.soap.SoapLoggingInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.transport.http.JdkHttpClientMessageSender;

@Configuration
@EnableConfigurationProperties(SoapClientProperties.class)
public class SoapClientConfig {

    @Bean
    public Jaxb2Marshaller countryInfoMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        // Classes generated from src/main/resources/wsdl/CountryInfoService.wsdl at build time
        marshaller.setContextPath("com.ncba.countryinfo.soap.generated");
        return marshaller;
    }

    @Bean
    public WebServiceTemplate countryInfoWebServiceTemplate(Jaxb2Marshaller countryInfoMarshaller,
            SoapClientProperties properties, SoapLoggingInterceptor loggingInterceptor) {
        // One shared, thread-safe HttpClient with connection reuse; timeouts protect our threads
        // from a slow or hung upstream.
        JdkHttpClientMessageSender messageSender = new JdkHttpClientMessageSender(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build());
        messageSender.setRequestTimeout(properties.readTimeout());

        WebServiceTemplate template = new WebServiceTemplate(countryInfoMarshaller);
        template.setDefaultUri(properties.url());
        template.setMessageSender(messageSender);
        template.setInterceptors(new ClientInterceptor[] {loggingInterceptor});
        return template;
    }
}
