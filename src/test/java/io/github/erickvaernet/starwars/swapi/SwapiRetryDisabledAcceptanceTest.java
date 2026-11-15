package io.github.erickvaernet.starwars.swapi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance test for REQ-22 with the retries switched off: a failure of SWAPI is answered at
 * once, as before the retries existed.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and looks for SWAPI at
 * the simulated one. It is a class apart from the other tests of the retries because the number
 * of retries is read at start-up: here the application starts with {@code SWAPI_MAX_RETRIES=0}.
 *
 * <p>The failure of SWAPI is not a recording: the test tells the simulated SWAPI what to answer,
 * and the recordings come back when the test ends.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"SEED_ENABLED=true", "SWAPI_MAX_RETRIES=0"})
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class SwapiRetryDisabledAcceptanceTest {

    private static final String PEOPLE = "/api/v1/people";

    /** The path of the list of people in SWAPI. */
    private static final String SWAPI_PEOPLE = "/api/people";

    @LocalServerPort
    private int port;

    @Autowired
    private WireMockServer swapi;

    /** Every cache of the application, however many there are. */
    @Autowired
    private ObjectProvider<Cache<?, ?>> caches;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = TestSession.open(RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build());
        caches.forEach(Cache::invalidateAll);
        swapi.resetRequests();
    }

    @AfterEach
    void bringTheRecordingsBack() {
        swapi.resetToDefaultMappings();
        swapi.resetRequests();
    }

    @Test
    @DisplayName("CA-22.8: with SWAPI_MAX_RETRIES=0, when SWAPI answers 503 to a request and well to the next one, the list answers 502 and SWAPI receives a single request")
    void withoutRetriesTheFirstFailureIsTheAnswer() {
        // Not a recording: the simulated SWAPI is told to fail once. The failure is ahead of the
        // recording only until it is served once; after that, the recording answers.
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .inScenario("SWAPI fails once")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .withBody("{\"message\":\"Service Unavailable\"}"))
                .willSetStateTo("SWAPI recovered"));

        client.get().uri(PEOPLE)
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
        swapi.verify(1, anyRequestedFor(anyUrl()));

        // SWAPI would have answered well to a second request: the 502 is there because that
        // request was never sent, not because SWAPI kept failing. Without this last step, the
        // test would also pass with a simulated SWAPI that always fails.
        client.get().uri(PEOPLE).exchange().expectStatus().isOk();
        swapi.verify(2, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

}
