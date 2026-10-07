package com.ncba.countryinfo.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A country and its details, as fetched from the FullCountryInfo SOAP operation. */
@Entity
@Table(name = "country_info")
public class CountryInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iso_code", nullable = false, unique = true, length = 3)
    private String isoCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "capital_city", length = 100)
    private String capitalCity;

    @Column(name = "phone_code", length = 20)
    private String phoneCode;

    @Column(name = "continent_code", length = 5)
    private String continentCode;

    @Column(name = "currency_iso_code", length = 5)
    private String currencyIsoCode;

    @Column(name = "country_flag", length = 500)
    private String countryFlag;

    /** Languages are owned by the country: saved, replaced and deleted together with it. */
    @OneToMany(mappedBy = "country", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<Language> languages = new ArrayList<>();

    /** Optimistic locking: a concurrent update of the same row fails instead of silently overwriting. */
    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Replaces the language list: languages already present (same ISO code) are updated in place,
     * new ones are added and missing ones removed. Matching by ISO code (instead of clear + re-add)
     * avoids Hibernate inserting the new rows before deleting the old ones, which would violate the
     * (country_id, iso_code) unique constraint.
     */
    public void replaceLanguages(List<Language> newLanguages) {
        Map<String, Language> incoming = new LinkedHashMap<>();
        newLanguages.forEach(l -> incoming.put(l.getIsoCode(), l));

        languages.removeIf(existing -> !incoming.containsKey(existing.getIsoCode()));
        Map<String, Language> current = new LinkedHashMap<>();
        languages.forEach(l -> current.put(l.getIsoCode(), l));

        incoming.forEach((isoCode, language) -> {
            Language existing = current.get(isoCode);
            if (existing != null) {
                existing.setName(language.getName());
            } else {
                addLanguage(language);
            }
        });
    }

    public void addLanguage(Language language) {
        language.setCountry(this);
        languages.add(language);
    }

    public Long getId() {
        return id;
    }

    public String getIsoCode() {
        return isoCode;
    }

    public void setIsoCode(String isoCode) {
        this.isoCode = isoCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCapitalCity() {
        return capitalCity;
    }

    public void setCapitalCity(String capitalCity) {
        this.capitalCity = capitalCity;
    }

    public String getPhoneCode() {
        return phoneCode;
    }

    public void setPhoneCode(String phoneCode) {
        this.phoneCode = phoneCode;
    }

    public String getContinentCode() {
        return continentCode;
    }

    public void setContinentCode(String continentCode) {
        this.continentCode = continentCode;
    }

    public String getCurrencyIsoCode() {
        return currencyIsoCode;
    }

    public void setCurrencyIsoCode(String currencyIsoCode) {
        this.currencyIsoCode = currencyIsoCode;
    }

    public String getCountryFlag() {
        return countryFlag;
    }

    public void setCountryFlag(String countryFlag) {
        this.countryFlag = countryFlag;
    }

    public List<Language> getLanguages() {
        return languages;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
