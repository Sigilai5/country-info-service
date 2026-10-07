-- Countries fetched from the CountryInfoService SOAP API (FullCountryInfo)
CREATE TABLE country_info (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    iso_code          VARCHAR(3)   NOT NULL,
    name              VARCHAR(100) NOT NULL,
    capital_city      VARCHAR(100),
    phone_code        VARCHAR(20),
    continent_code    VARCHAR(5),
    currency_iso_code VARCHAR(5),
    country_flag      VARCHAR(500),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_country_info_iso_code UNIQUE (iso_code),
    INDEX idx_country_info_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Languages spoken in a country (one country -> many languages)
CREATE TABLE language (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    country_id BIGINT       NOT NULL,
    iso_code   VARCHAR(10)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_language_country FOREIGN KEY (country_id) REFERENCES country_info (id) ON DELETE CASCADE,
    CONSTRAINT uk_language_country_iso UNIQUE (country_id, iso_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
