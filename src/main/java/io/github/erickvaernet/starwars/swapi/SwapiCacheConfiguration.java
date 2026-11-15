package io.github.erickvaernet.starwars.swapi;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.erickvaernet.starwars.catalog.CatalogSource;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Builds the cache of the answers of SWAPI, in memory, with an expiry and a maximum size, and
 * puts it in front of the client of SWAPI.
 */
@Configuration(proxyBeanMethods = false)
class SwapiCacheConfiguration {

    /**
     * The cache, with the expiry and the size that were configured.
     *
     * <p>An answer expires a fixed time after it was <em>stored</em>, however often it is read:
     * counting from the last read would keep a popular answer forever, and it would never be
     * refreshed. When the cache is full, the library discards the answer it estimates least
     * likely to be asked for again, by how often and how recently each one was used.
     *
     * <p>Keys and values are of any type: each kind of request has its own key and its own
     * answer, all in one cache with one limit.
     *
     * @param properties the expiry and the maximum size
     * @param clock      the clock that measures the age of an answer. The application declares
     *                   none, so it is the one of the system; a test declares its own to make
     *                   time go by without waiting
     */
    @Bean
    Cache<Object, Object> swapiCache(SwapiCacheProperties properties, Optional<Ticker> clock) {
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.ttl())
                .maximumSize(properties.maxSize())
                .ticker(clock.orElse(Ticker.systemTicker()))
                .build();
    }

    /**
     * The data source that the rest of the application receives: the client of SWAPI, wrapped
     * so that its answers are kept.
     *
     * <p>The client is a {@link CatalogSource} as well. This one is marked as the primary one,
     * so whoever asks for a {@code CatalogSource} gets the one with the cache.
     */
    @Bean
    @Primary
    CatalogSource cachedCatalogSource(SwapiCatalogSource swapi, Cache<Object, Object> swapiCache) {
        return new CachedCatalogSource(swapi, swapiCache);
    }

}
