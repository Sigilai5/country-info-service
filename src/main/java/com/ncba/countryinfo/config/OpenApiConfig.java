package com.ncba.countryinfo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI countryInfoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Country Info Service API")
                .version("v1")
                .description("Receives a country name, fetches its ISO code and full country info "
                        + "from the CountryInfoService SOAP API, stores it in MySQL and exposes CRUD "
                        + "endpoints. Every response uses the WsResponse envelope and carries an "
                        + "X-Request-ID header for tracing."));
    }
}
