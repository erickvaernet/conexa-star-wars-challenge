package io.github.erickvaernet.starwars;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erickvaernet.starwars.auth.FailedLogins;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance test for REQ-29 behind the service that publishes the application: the address that
 * the limit of login attempts counts by is the one that the proxy tells.
 *
 * <p>The public service passes each request on to the application with a header that tells the
 * address of the client, {@code X-Forwarded-For}. The application runs here on a real HTTP port
 * against a real PostgreSQL, and the test plays the part of that service: it sends that header
 * itself.
 *
 * <p>The application is configured as in the public deployment, with the variables that
 * {@link PublicDeploymentAcceptanceTest} reads from {@code render.yaml}: the same reading, so
 * this test and the file that is really deployed cannot drift apart. That file switches the seed
 * on, so the test user exists, and leaves the limit in its default values.
 *
 * <p>It shares the running application with that other test. It starts and ends with the count
 * of failed logins empty.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ContextConfiguration(initializers = PublicDeploymentAcceptanceTest.DeploymentVariables.class)
class PublicDeploymentLoginLimitAcceptanceTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String DEMO = "demo";

    /** The password published in the README. */
    private static final String DEMO_PASSWORD = "demo1234";

    private static final String WRONG_PASSWORD = "not-the-password";

    /** The header the proxy uses to tell the address of the client. */
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    /** Two addresses of a range reserved for examples: nobody has them. */
    private static final String ONE_ADDRESS = "203.0.113.7";

    private static final String ANOTHER_ADDRESS = "203.0.113.8";

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
    @DisplayName("CA-29.16: configured as in the public deployment, after five failed logins with X-Forwarded-For: 203.0.113.7, a correct login with X-Forwarded-For: 203.0.113.8 is answered with 200 and one with X-Forwarded-For: 203.0.113.7 with 429: each address that the proxy tells has its own count")
    void eachAddressThatTheProxyTellsHasItsOwnCount() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            loginFrom(ONE_ADDRESS, WRONG_PASSWORD)
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.detail").isEqualTo("Invalid username or password.");
        }

        loginFrom(ANOTHER_ADDRESS, DEMO_PASSWORD)
                .expectStatus().isOk()
                .expectBody().jsonPath("$.accessToken").value(String.class, token -> assertThat(token).isNotBlank());
        loginFrom(ONE_ADDRESS, DEMO_PASSWORD)
                .expectStatus().isEqualTo(429)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER)
                .expectBody().jsonPath("$.detail").isEqualTo("Too many failed login attempts. Try again later.");
    }

    /** A login as demo that arrives through the proxy, which tells the address it comes from. */
    private RestTestClient.ResponseSpec loginFrom(String address, String password) {
        return client.post().uri(LOGIN)
                .header(FORWARDED_FOR, address)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", DEMO, "password", password))
                .exchange();
    }

}
