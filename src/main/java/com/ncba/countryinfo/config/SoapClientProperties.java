package com.ncba.countryinfo.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the CountryInfoService SOAP API ({@code soap.country-info.*}).
 *
 * @param url            SOAP endpoint
 * @param connectTimeout max time to establish the TCP connection
 * @param readTimeout    max time to wait for the full response
 */
@ConfigurationProperties("soap.country-info")
public record SoapClientProperties(String url, Duration connectTimeout, Duration readTimeout) {
}
