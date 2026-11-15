package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-16: the tests never reach the real SWAPI, and the login does not need
 * SWAPI.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL. This class does
 * <strong>not</strong> import the simulated SWAPI, on purpose: it is in the place of any test
 * that forgets to. For such a test, the configuration of the tests points the application to a
 * local address where nothing listens. That is also the most faithful way to have SWAPI down.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import(TestcontainersConfiguration.class)
class SwapiUnreachableAcceptanceTest {

    private static final String PEOPLE = "/api/v1/people";

    @LocalServerPort
    private int port;

    /** The same variable the application reads to find SWAPI. */
    @Value("${SWAPI_BASE_URL}")
    private String swapiBaseUrl;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("CA-16.1: a test without the simulated SWAPI looks for SWAPI at a local address where nothing listens")
    void testWithoutTheSimulatedSwapiCannotReachTheRealOne() {
        URI address = URI.create(swapiBaseUrl);

        assertThat(address.getHost()).isEqualTo("localhost");
        assertThatThrownBy(() -> new Socket(address.getHost(), address.getPort()).close())
                .isInstanceOf(ConnectException.class);
    }

    @Test
    @DisplayName("CA-16.4: with SWAPI down, the login still answers 200")
    void loginDoesNotDependOnSwapi() {
        String token = TestSession.login(client);

        // SWAPI is down for the application: the list fails. The specification allows both
        // codes here. If the application reached the real SWAPI, this would be a 200.
        client.get().uri(PEOPLE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().value(status -> assertThat(status).isIn(502, 504))
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        // And the login keeps working after that failure. The helper checks that it answers 200.
        assertThat(TestSession.login(client)).isNotBlank();
    }

}
