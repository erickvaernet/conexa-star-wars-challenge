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
 * Checks how the settings of SWAPI are read at start-up: its address, the longest times the
 * application waits for it, and how a request that failed is sent again.
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
    @DisplayName("By default a request that failed is sent once more, 200 milliseconds later")
    void defaultRetries() {
        application.run(context -> {
            SwapiProperties properties = context.getBean(SwapiProperties.class);
            assertThat(properties.maxRetries()).isEqualTo(1);
            assertThat(properties.retryDelay()).isEqualTo(Duration.ofMillis(200));
        });
    }

    @Test
    @DisplayName("SWAPI_MAX_RETRIES and SWAPI_RETRY_DELAY change how many times and how long after")
    void retriesCanBeChanged() {
        application.withPropertyValues("SWAPI_MAX_RETRIES=3", "SWAPI_RETRY_DELAY=1s")
                .run(context -> {
                    SwapiProperties properties = context.getBean(SwapiProperties.class);
                    assertThat(properties.maxRetries()).isEqualTo(3);
                    assertThat(properties.retryDelay()).isEqualTo(Duration.ofSeconds(1));
                });
    }

    @Test
    @DisplayName("SWAPI_MAX_RETRIES=0 is valid: it switches the retries off")
    void retriesCanBeSwitchedOff() {
        application.withPropertyValues("SWAPI_MAX_RETRIES=0")
                .run(context -> assertThat(context.getBean(SwapiProperties.class).maxRetries()).isZero());
    }

    @Test
    @DisplayName("SWAPI_RETRY_DELAY=0s is valid: the retry is sent at once")
    void retryCanBeSentAtOnce() {
        application.withPropertyValues("SWAPI_RETRY_DELAY=0s")
                .run(context -> assertThat(context.getBean(SwapiProperties.class).retryDelay()).isZero());
    }

    @ParameterizedTest(name = "SWAPI_MAX_RETRIES=\"{0}\"")
    @ValueSource(strings = {"", "-1", "4", "100"})
    @DisplayName("The application does not start with a number of retries out of the range from 0 to 3, and says which variable to fix")
    void applicationDoesNotStartWithRetriesOutOfRange(String maxRetries) {
        application.withPropertyValues("SWAPI_MAX_RETRIES=" + maxRetries)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("SWAPI_MAX_RETRIES"));
    }

    @Test
    @DisplayName("The application does not start with a number of retries that is not a number")
    void applicationDoesNotStartWithRetriesThatAreNotANumber() {
        application.withPropertyValues("SWAPI_MAX_RETRIES=many")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest(name = "SWAPI_RETRY_DELAY=\"{0}\"")
    @ValueSource(strings = {"", "-1ms", "-200ms"})
    @DisplayName("The application does not start with a wait before a retry that is empty or negative, and says which variable to fix")
    void applicationDoesNotStartWithARetryDelayThatIsEmptyOrNegative(String delay) {
        application.withPropertyValues("SWAPI_RETRY_DELAY=" + delay)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("SWAPI_RETRY_DELAY"));
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
