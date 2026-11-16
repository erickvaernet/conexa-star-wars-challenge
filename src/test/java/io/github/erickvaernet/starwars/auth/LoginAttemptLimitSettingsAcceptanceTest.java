package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import io.github.erickvaernet.starwars.swapi.ManualClock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance test for REQ-29 with other values: the number of failed logins that blocks an
 * address and the length of the window are the configured ones.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, with the seed switched
 * on. It is a class apart from the other tests of the limit because both values are read at
 * start-up: here the application starts with {@code LOGIN_MAX_FAILED_ATTEMPTS=2} and
 * {@code LOGIN_FAILED_ATTEMPTS_WINDOW=1m}.
 *
 * <p>The test does not wait that minute. The limit reads the time from the clock that this class
 * gives it, which only moves when the test tells it to.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"SEED_ENABLED=true", "LOGIN_MAX_FAILED_ATTEMPTS=2", "LOGIN_FAILED_ATTEMPTS_WINDOW=1m"})
@Import(TestcontainersConfiguration.class)
class LoginAttemptLimitSettingsAcceptanceTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String DEMO = "demo";

    /** The password published in the README. */
    private static final String DEMO_PASSWORD = "demo1234";

    private static final String WRONG_PASSWORD = "not-the-password";

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext application;

    /** The clock of the limit, in the hands of the test. */
    @Autowired
    private ManualClock clock;

    /** A client without a session. */
    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        FailedLogins.forget(application);
    }

    @AfterEach
    void leaveTheCountEmpty() {
        FailedLogins.forget(application);
    }

    @Test
    @DisplayName("CA-29.14: with LOGIN_MAX_FAILED_ATTEMPTS=2 and LOGIN_FAILED_ATTEMPTS_WINDOW=1m, three failed logins are answered with 401, 401 and 429 with Retry-After of 60 or less, and a correct login one minute later with 200")
    void limitAndWindowAreTheConfiguredOnes() {
        login(WRONG_PASSWORD).expectStatus().isUnauthorized();
        login(WRONG_PASSWORD).expectStatus().isUnauthorized();

        String retryAfter = login(WRONG_PASSWORD)
                .expectStatus().isEqualTo(429)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.detail").isEqualTo("Too many failed login attempts. Try again later.")
                .returnResult().getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        // A whole number of seconds, and never more than the window.
        assertThat(retryAfter).isNotNull().matches("[1-9][0-9]*");
        assertThat(Integer.parseInt(retryAfter)).isLessThanOrEqualTo(60);
        // The clock did not move since the first failure, so the whole window is left.
        assertThat(Integer.parseInt(retryAfter)).isEqualTo(60);

        clock.advance(Duration.ofMinutes(1));

        login(DEMO_PASSWORD)
                .expectStatus().isOk()
                .expectBody().jsonPath("$.accessToken").value(String.class, token -> assertThat(token).isNotBlank());
    }

    /** A login as demo with that password. */
    private RestTestClient.ResponseSpec login(String password) {
        return client.post().uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", DEMO, "password", password))
                .exchange();
    }

    /**
     * Puts the clock of the limit in the hands of the test: the application uses this clock
     * instead of the one of the system.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {

        @Bean
        ManualClock loginClock() {
            return new ManualClock();
        }

    }

}
