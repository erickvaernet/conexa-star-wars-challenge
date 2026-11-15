package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.erickvaernet.starwars.catalog.CatalogSource;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Checks how the cache is put together when the application starts: which data source the rest
 * of the application receives, and which clock the cache follows.
 *
 * <p>Only the pieces of this package are loaded, with the real {@code application.properties}.
 * The tests need neither the database nor Docker, and nothing is asked to SWAPI.
 */
class SwapiCacheConfigurationTest {

    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(SwapiPieces.class);

    @Test
    @DisplayName("The data source the application receives is the one with the cache, not the bare client of SWAPI")
    void applicationReceivesTheCachedSource() {
        application.run(context ->
                assertThat(context.getBean(CatalogSource.class)).isInstanceOf(CachedCatalogSource.class));
    }

    @Test
    @DisplayName("The cache is a single bean, so the acceptance tests can find it and empty it")
    void cacheIsABean() {
        application.run(context -> assertThat(context).hasSingleBean(Cache.class));
    }

    @Test
    @DisplayName("Without a clock of its own, the cache follows the clock of the system and keeps what it is given")
    void cacheFollowsTheSystemClockByDefault() {
        application.run(context -> {
            Cache<Object, Object> cache = cacheOf(context.getBean(Cache.class));
            cache.put("key", "answer");

            assertThat(cache.getIfPresent("key")).isEqualTo("answer");
        });
    }

    @Test
    @DisplayName("When a clock is declared, the cache follows it: an answer expires as soon as that clock says so")
    void cacheFollowsTheDeclaredClock() {
        application.withUserConfiguration(ClockOfTheTest.class).run(context -> {
            Cache<Object, Object> cache = cacheOf(context.getBean(Cache.class));
            ManualClock clock = context.getBean(ManualClock.class);
            cache.put("key", "answer");

            // The default expiry is one hour.
            clock.advance(Duration.ofMinutes(59));
            assertThat(cache.getIfPresent("key")).isEqualTo("answer");

            clock.advance(Duration.ofMinutes(2));
            assertThat(cache.getIfPresent("key")).isNull();
        });
    }

    @SuppressWarnings("unchecked")
    private static Cache<Object, Object> cacheOf(Cache<?, ?> cache) {
        return (Cache<Object, Object>) cache;
    }

    @Import({SwapiCatalogSource.class, SwapiCacheConfiguration.class})
    @EnableConfigurationProperties({SwapiProperties.class, SwapiCacheProperties.class})
    static class SwapiPieces {
    }

    @Configuration(proxyBeanMethods = false)
    static class ClockOfTheTest {

        @Bean
        ManualClock cacheClock() {
            return new ManualClock();
        }

    }

}
