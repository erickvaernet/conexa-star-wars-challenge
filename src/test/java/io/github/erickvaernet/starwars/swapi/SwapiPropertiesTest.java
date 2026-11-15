package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Checks how the settings of SWAPI are read at start-up: its address and the longest times the
 * application waits for it.
 *
 * <p>Only the settings are loaded, through the real {@code application.properties}, so the tests
 * cover the whole path from the environment variable to the value the application uses. They need
 * neither the database nor Docker.
 */
class SwapiPropertiesTest {

    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Settings.class);

    @Test
    @DisplayName("Without SWAPI_BASE_URL, the address is the one of the real SWAPI")
    void addressIsTheRealSwapiByDefault() throws IOException {
        // The configuration of the tests always sets SWAPI_BASE_URL, so that no test reaches the
        // real SWAPI. The default value can only be read where it is written.
        Properties main = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

        assertThat(main.getProperty("swapi.base-url")).isEqualTo("${SWAPI_BASE_URL:https://www.swapi.tech/api}");
    }

    @Test
    @DisplayName("SWAPI_BASE_URL changes where SWAPI is")
    void addressCanBeChanged() {
        application.withPropertyValues("SWAPI_BASE_URL=http://localhost:9999/api")
                .run(context -> assertThat(context.getBean(SwapiProperties.class).baseUrl())
                        .isEqualTo("http://localhost:9999/api"));
    }

    @Test
    @DisplayName("A slash at the end of SWAPI_BASE_URL is left out, so the paths are not built with two")
    void slashAtTheEndOfTheAddressIsLeftOut() {
        application.withPropertyValues("SWAPI_BASE_URL=http://localhost:9999/api/")
                .run(context -> assertThat(context.getBean(SwapiProperties.class).baseUrl())
                        .isEqualTo("http://localhost:9999/api"));
    }

    @Test
    @DisplayName("By default the application waits 3 seconds to connect and 10 seconds for the answer")
    void defaultTimeouts() {
        application.run(context -> {
            SwapiProperties properties = context.getBean(SwapiProperties.class);
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    @DisplayName("SWAPI_CONNECT_TIMEOUT and SWAPI_READ_TIMEOUT change those times")
    void timeoutsCanBeChanged() {
        application.withPropertyValues("SWAPI_CONNECT_TIMEOUT=500ms", "SWAPI_READ_TIMEOUT=2s")
                .run(context -> {
                    SwapiProperties properties = context.getBean(SwapiProperties.class);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(500));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(2));
                });
    }

    @Test
    @DisplayName("The application does not start with an empty SWAPI_BASE_URL, and says why")
    void applicationDoesNotStartWithoutAnAddress() {
        application.withPropertyValues("SWAPI_BASE_URL=")
                .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("SWAPI_BASE_URL"));
    }

    @ParameterizedTest(name = "SWAPI_READ_TIMEOUT={0}")
    @ValueSource(strings = {"0s", "-1s"})
    @DisplayName("The application does not start with a read timeout that is not positive")
    void applicationDoesNotStartWithAReadTimeoutThatIsNotPositive(String timeout) {
        application.withPropertyValues("SWAPI_READ_TIMEOUT=" + timeout)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("SWAPI_READ_TIMEOUT"));
    }

    @ParameterizedTest(name = "SWAPI_CONNECT_TIMEOUT={0}")
    @ValueSource(strings = {"0s", "-1s"})
    @DisplayName("The application does not start with a connect timeout that is not positive")
    void applicationDoesNotStartWithAConnectTimeoutThatIsNotPositive(String timeout) {
        application.withPropertyValues("SWAPI_CONNECT_TIMEOUT=" + timeout)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("SWAPI_CONNECT_TIMEOUT"));
    }

    @Test
    @DisplayName("Requests are handled in virtual threads, so waiting for SWAPI does not hold a system thread")
    void virtualThreadsAreSwitchedOn() {
        application.run(context -> assertThat(context.getEnvironment().getProperty("spring.threads.virtual.enabled"))
                .isEqualTo("true"));
    }

    @EnableConfigurationProperties(SwapiProperties.class)
    static class Settings {
    }

}
