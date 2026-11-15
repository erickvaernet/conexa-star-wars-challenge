package io.github.erickvaernet.starwars;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-19: the application starts and reports that it is ready.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and the tests call it
 * the same way Docker does to decide whether the container is healthy.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class HealthAcceptanceTest {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("CA-19.1: the health check reports that the application is ready")
    void healthCheckReportsThatTheApplicationIsReady() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("The health check does not reveal internal details")
    void healthCheckDoesNotRevealInternalDetails() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectBody().jsonPath("$.components").doesNotExist();
    }

    @Test
    @DisplayName("No other Actuator endpoint is reachable")
    void noOtherActuatorEndpointIsReachable() {
        // Without a session all of them are answered with "unauthorized", exposed or not. That
        // they are not exposed is checked with a valid token, in ProtectedAccessAcceptanceTest.
        client.get().uri("/actuator/env").exchange().expectStatus().is4xxClientError();
        client.get().uri("/actuator/beans").exchange().expectStatus().is4xxClientError();
        client.get().uri("/actuator/metrics").exchange().expectStatus().is4xxClientError();
    }

}
