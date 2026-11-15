package io.github.erickvaernet.starwars;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Checks the simulated SWAPI that the tests use instead of the real service.
 *
 * <p>It needs neither the application nor Docker: only the stub is started.
 */
@SpringJUnitConfig(SwapiStubConfiguration.class)
class SwapiStubTest {

    @Autowired
    private Environment environment;

    private RestTestClient swapi;

    @BeforeEach
    void setUp() {
        swapi = RestTestClient.bindToServer().baseUrl(swapiBaseUrl()).build();
    }

    @Test
    @DisplayName("The address of SWAPI points to the local stub, not to the real service")
    void swapiAddressPointsToTheLocalStub() {
        assertThat(swapiBaseUrl()).startsWith("http://localhost:").endsWith("/api");
    }

    @Test
    @DisplayName("A recorded request gets the real response that SWAPI gave")
    void recordedRequestGetsTheRealResponse() {
        swapi.get().uri("/people/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.result.uid").isEqualTo("1")
                .jsonPath("$.result.properties.name").isEqualTo("Luke Skywalker");
    }

    @Test
    @DisplayName("A request that was not recorded gets no response to work with")
    void requestThatWasNotRecordedGetsNoResponse() {
        swapi.get().uri("/people/2").exchange().expectStatus().isNotFound();
    }

    /** The same variable the application reads to find SWAPI. */
    private String swapiBaseUrl() {
        return environment.getRequiredProperty("SWAPI_BASE_URL");
    }

}
