package com.ncba.countryinfo.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Enables Spring's cache abstraction; the Caffeine cache itself is configured via spring.cache.* properties. */
@Configuration
@EnableCaching
public class CacheConfig {
}
