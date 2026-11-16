package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Checks how the settings of the limit of login attempts are read at start-up: how many failed
 * logins block an address, and how long the window they are counted in lasts.
 *
 * <p>Only the settings are loaded, through the real {@code application.properties}, so the tests
 * cover the whole path from the environment variable to the value the application uses. They need
 * neither the database nor Docker.
 */
class LoginAttemptPropertiesTest {

    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Settings.class);

    @Test
    @DisplayName("By default, five failed logins block an address, and the window lasts fifteen minutes")
    void defaultLimitAndWindow() {
        application.run(context -> {
            LoginAttemptProperties properties = context.getBean(LoginAttemptProperties.class);
            assertThat(properties.maxFailedAttempts()).isEqualTo(5);
            assertThat(properties.failedAttemptsWindow()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.enabled()).isTrue();
        });
    }

    @Test
    @DisplayName("LOGIN_MAX_FAILED_ATTEMPTS and LOGIN_FAILED_ATTEMPTS_WINDOW change the limit and the window")
    void limitAndWindowCanBeChanged() {
        application.withPropertyValues("LOGIN_MAX_FAILED_ATTEMPTS=2", "LOGIN_FAILED_ATTEMPTS_WINDOW=1m")
                .run(context -> {
                    LoginAttemptProperties properties = context.getBean(LoginAttemptProperties.class);
                    assertThat(properties.maxFailedAttempts()).isEqualTo(2);
                    assertThat(properties.failedAttemptsWindow()).isEqualTo(Duration.ofMinutes(1));
                });
    }

    @Test
    @DisplayName("With LOGIN_MAX_FAILED_ATTEMPTS=0 the application starts, and the limit is switched off")
    void zeroSwitchesTheLimitOff() {
        application.withPropertyValues("LOGIN_MAX_FAILED_ATTEMPTS=0")
                .run(context -> assertThat(context.getBean(LoginAttemptProperties.class).enabled()).isFalse());
    }

    @ParameterizedTest(name = "LOGIN_MAX_FAILED_ATTEMPTS={0}, LOGIN_FAILED_ATTEMPTS_WINDOW={1}")
    @CsvSource({"1, 1s", "1, 1000ms", "100, 24h", "100, 1440m"})
    @DisplayName("The ends of both ranges are accepted: from 1 to 100 failed logins, and a window from one second to 24 hours")
    void endsOfTheRangesAreAccepted(int attempts, String window) {
        application.withPropertyValues(
                        "LOGIN_MAX_FAILED_ATTEMPTS=" + attempts, "LOGIN_FAILED_ATTEMPTS_WINDOW=" + window)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest(name = "LOGIN_MAX_FAILED_ATTEMPTS=\"{0}\"")
    @ValueSource(strings = {"", "-1", "101"})
    @DisplayName("The application does not start with a limit that is empty or out of the range from 0 to 100, and says which variable to fix")
    void applicationDoesNotStartWithALimitOutOfRange(String attempts) {
        application.withPropertyValues("LOGIN_MAX_FAILED_ATTEMPTS=" + attempts)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("LOGIN_MAX_FAILED_ATTEMPTS"));
    }

    @ParameterizedTest(name = "LOGIN_FAILED_ATTEMPTS_WINDOW=\"{0}\"")
    @ValueSource(strings = {"", "0s", "-1m", "1ns", "1ms", "999ms", "15", "900", "24h1s", "25h"})
    @DisplayName("The application does not start with a window that is empty, shorter than one second or longer than 24 hours, and says which variable to fix: a number without its unit is read as milliseconds")
    void applicationDoesNotStartWithAWindowOutOfRange(String window) {
        application.withPropertyValues("LOGIN_FAILED_ATTEMPTS_WINDOW=" + window)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("LOGIN_FAILED_ATTEMPTS_WINDOW"));
    }

    @ParameterizedTest(name = "LOGIN_FAILED_ATTEMPTS_WINDOW=\"{0}\"")
    @ValueSource(strings = {"", "0s", "500ms", "25h"})
    @DisplayName("The window is checked with the limit switched off too: a wrong value does not wait hidden until the limit is switched on")
    void windowIsCheckedWithTheLimitSwitchedOffToo(String window) {
        application.withPropertyValues("LOGIN_MAX_FAILED_ATTEMPTS=0", "LOGIN_FAILED_ATTEMPTS_WINDOW=" + window)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("LOGIN_FAILED_ATTEMPTS_WINDOW"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"LOGIN_MAX_FAILED_ATTEMPTS=many", "LOGIN_MAX_FAILED_ATTEMPTS=2.5",
        "LOGIN_FAILED_ATTEMPTS_WINDOW=soon", "LOGIN_FAILED_ATTEMPTS_WINDOW=15 minutes"})
    @DisplayName("The application does not start with a value that cannot be read, and says which variable to fix")
    void applicationDoesNotStartWithAValueThatCannotBeRead(String setting) {
        String variable = setting.substring(0, setting.indexOf('='));

        application.withPropertyValues(setting)
                .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining(variable));
    }

    @EnableConfigurationProperties(LoginAttemptProperties.class)
    static class Settings {
    }

}
