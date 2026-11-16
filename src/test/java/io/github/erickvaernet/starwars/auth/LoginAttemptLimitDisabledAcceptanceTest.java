package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance test for REQ-29 with the limit switched off: the failed logins are not counted and
 * the login is never answered with 429.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, with the seed switched
 * on. It is a class apart from the other tests of the limit because the limit is read at
 * start-up: here the application starts with {@code LOGIN_MAX_FAILED_ATTEMPTS=0}.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"SEED_ENABLED=true", "LOGIN_MAX_FAILED_ATTEMPTS=0"})
@Import(TestcontainersConfiguration.class)
class LoginAttemptLimitDisabledAcceptanceTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String DEMO = "demo";

    /** The password published in the README. */
    private static final String DEMO_PASSWORD = "demo1234";

    private static final String WRONG_PASSWORD = "not-the-password";

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext application;

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
    @DisplayName("CA-29.13: with LOGIN_MAX_FAILED_ATTEMPTS=0, ten failed logins are answered with 401 and the correct login after them with 200: the limit is switched off")
    void limitIsSwitchedOff() {
        for (int attempt = 1; attempt <= 10; attempt++) {
            login(WRONG_PASSWORD)
                    .expectStatus().isUnauthorized()
                    .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .expectHeader().doesNotExist(HttpHeaders.RETRY_AFTER)
                    .expectBody().jsonPath("$.detail").isEqualTo("Invalid username or password.");
        }

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

}
