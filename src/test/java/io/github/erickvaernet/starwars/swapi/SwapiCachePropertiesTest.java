package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Checks how the settings of the cache are read at start-up: how long an answer of SWAPI is
 * kept, and how many are kept at most.
 *
 * <p>Only the settings are loaded, through the real {@code application.properties}, so the tests
 * cover the whole path from the environment variable to the value the application uses. They need
 * neither the database nor Docker.
 */
class SwapiCachePropertiesTest {

    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Settings.class);

    @Test
    @DisplayName("By default an answer is kept for one hour, and 500 answers are kept at most")
    void defaultExpiryAndSize() {
        application.run(context -> {
            SwapiCacheProperties properties = context.getBean(SwapiCacheProperties.class);
            assertThat(properties.ttl()).isEqualTo(Duration.ofHours(1));
            assertThat(properties.maxSize()).isEqualTo(500);
        });
    }

    @Test
    @DisplayName("SWAPI_CACHE_TTL and SWAPI_CACHE_MAX_SIZE change the expiry and the size")
    void expiryAndSizeCanBeChanged() {
        application.withPropertyValues("SWAPI_CACHE_TTL=30m", "SWAPI_CACHE_MAX_SIZE=50")
                .run(context -> {
                    SwapiCacheProperties properties = context.getBean(SwapiCacheProperties.class);
                    assertThat(properties.ttl()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(properties.maxSize()).isEqualTo(50);
                });
    }

    @ParameterizedTest(name = "SWAPI_CACHE_TTL=\"{0}\"")
    @ValueSource(strings = {"", "0s", "-1h"})
    @DisplayName("The application does not start with an expiry that is not positive, and says which variable to fix")
    void applicationDoesNotStartWithAnExpiryThatIsNotPositive(String ttl) {
        application.withPropertyValues("SWAPI_CACHE_TTL=" + ttl)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("SWAPI_CACHE_TTL"));
    }

    @ParameterizedTest(name = "SWAPI_CACHE_MAX_SIZE=\"{0}\"")
    @ValueSource(strings = {"", "0", "-1"})
    @DisplayName("The application does not start with a size below 1, and says which variable to fix")
    void applicationDoesNotStartWithASizeBelowOne(String maxSize) {
        application.withPropertyValues("SWAPI_CACHE_MAX_SIZE=" + maxSize)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("SWAPI_CACHE_MAX_SIZE"));
    }

    @Test
    @DisplayName("The application does not start with a size that is not a number")
    void applicationDoesNotStartWithASizeThatIsNotANumber() {
        application.withPropertyValues("SWAPI_CACHE_MAX_SIZE=many")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(SwapiCacheProperties.class)
    static class Settings {
    }

}
