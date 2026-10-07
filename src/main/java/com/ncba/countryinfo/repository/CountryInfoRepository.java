package com.ncba.countryinfo.repository;

import java.util.Optional;

import com.ncba.countryinfo.entity.CountryInfo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryInfoRepository extends JpaRepository<CountryInfo, Long> {

    /** Name lookup is case-insensitive (utf8mb4_unicode_ci collation). Languages are fetched in the same query. */
    @EntityGraph(attributePaths = "languages")
    Optional<CountryInfo> findByName(String name);

    @EntityGraph(attributePaths = "languages")
    Optional<CountryInfo> findByIsoCode(String isoCode);
}
