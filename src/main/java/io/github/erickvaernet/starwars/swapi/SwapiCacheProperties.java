package io.github.erickvaernet.starwars.swapi;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the cache of the answers of SWAPI, read from the environment at start-up.
 *
 * <p>An invalid value stops the application from starting, so a wrong setting does not go
 * unnoticed until the cache fills up or an old answer is served.
 *
 * @param ttl     how long an answer is kept after it was stored, from {@code SWAPI_CACHE_TTL}
 * @param maxSize how many answers are kept at most, from {@code SWAPI_CACHE_MAX_SIZE}
 */
@ConfigurationProperties("swapi.cache")
record SwapiCacheProperties(Duration ttl, Integer maxSize) {

    SwapiCacheProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("SWAPI_CACHE_TTL must be a time greater than zero, such as 1h or 30m.");
        }
        // The library takes a size of zero as "keep nothing". Switching the cache off is not a
        // setting of this application, so zero is rejected like any other invalid value. The size
        // is an Integer and not an int so that an empty variable gets this same message.
        if (maxSize == null || maxSize < 1) {
            throw new IllegalArgumentException("SWAPI_CACHE_MAX_SIZE must be 1 or more.");
        }
    }

}
