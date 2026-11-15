package io.github.erickvaernet.starwars.swapi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
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
 * Acceptance tests for REQ-22: a request to SWAPI that fails in a way that usually goes away by
 * itself is sent once more, and any other failure is not.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and looks for SWAPI at
 * the simulated one. Every request carries the token of the test user. The retries run with their
 * default settings: one more attempt, 200 milliseconds after the failure.
 *
 * <p>The failures of SWAPI are not recordings. Nobody can make the real SWAPI fail on demand, so
 * each test tells the simulated SWAPI what to answer, and the recordings come back when the test
 * ends. Where SWAPI "answers well", the answer is the recording.
 *
 * <p>Each test counts how many requests the simulated SWAPI received: that is the only way to
 * tell from outside whether a request was sent again. Every test starts with the cache empty, so
 * that an answer stored by another one does not hide a request.
 *
 * <p>The longest time the application waits for SWAPI is lowered to 1 second by configuration, as
 * in the tests of REQ-16, so the test of the slow answer does not take 10 seconds. Those tests run
 * on this same application: this class also leaves the cache empty when each test ends, because
 * they expect their requests to reach SWAPI.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"SEED_ENABLED=true", "SWAPI_READ_TIMEOUT=1s"})
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class SwapiRetryAcceptanceTest {

    private static final String PEOPLE = "/api/v1/people";

    /** The path of the list of people in SWAPI. */
    private static final String SWAPI_PEOPLE = "/api/people";

    /** The path of character 1 in SWAPI. */
    private static final String SWAPI_CHARACTER_1 = "/api/people/1";

    /** The path in SWAPI of a character that does not exist. */
    private static final String SWAPI_CHARACTER_9999 = "/api/people/9999";

    /** A text that only the failed answer of SWAPI has, to check that it does not get out. */
    private static final String SWAPI_ERROR_TEXT = "upstream-failure-marker";

    /** Three times the longest wait configured for this class. */
    private static final int SLOW_ANSWER_MILLISECONDS = 3000;

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
    void bringTheRecordingsBackAndEmptyTheCache() {
        swapi.resetToDefaultMappings();
        swapi.resetRequests();
        caches.forEach(Cache::invalidateAll);
    }

    @Test
    @DisplayName("CA-22.1: when SWAPI answers 503 to a request and well to the next one, the list answers 200 and SWAPI receives two requests")
    void failureThatGoesAwayIsNotSeenFromOutside() {
        // Not a recording: the simulated SWAPI is told to fail once.
        swapiFailsOnce(SWAPI_PEOPLE, error(503));

        client.get().uri(PEOPLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(10)
                .jsonPath("$.content[0].name").isEqualTo("Luke Skywalker")
                .jsonPath("$.totalElements").isEqualTo(82);

        swapi.verify(2, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-22.2: when SWAPI always answers 503, the list answers 502 without internal details and SWAPI receives exactly two requests")
    void failureThatStaysIsAnsweredWithBadGatewayAfterOneRetry() {
        // Not a recording: the simulated SWAPI is told to fail every time.
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1).willReturn(error(503)));

        String body = expectProblem(client.get().uri(PEOPLE).exchange(), 502, "Bad Gateway", PEOPLE);

        assertThat(body).doesNotContain(SWAPI_ERROR_TEXT);
        // One retry and no more: neither a single request, nor a third one.
        swapi.verify(2, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-22.3: when SWAPI cuts the connection in a request and answers well to the next one, the detail answers 200 and SWAPI receives two requests")
    void connectionThatIsCutOnceIsNotSeenFromOutside() {
        // Not a recording: the simulated SWAPI is told to close the connection once, without
        // answering anything.
        swapiFailsOnce(SWAPI_CHARACTER_1, aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

        client.get().uri(PEOPLE + "/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("1")
                .jsonPath("$.name").isEqualTo("Luke Skywalker");

        swapi.verify(2, getRequestedFor(urlPathEqualTo(SWAPI_CHARACTER_1)));
        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-22.4: when SWAPI answers 429, the list answers 502 and SWAPI receives a single request")
    void tooManyRequestsIsNotRetried() {
        // Not a recording: the simulated SWAPI is told to answer what the real one answers after
        // 100 requests in 15 minutes. It would answer the same to a retry, which is never sent.
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1).willReturn(error(429)));

        expectProblem(client.get().uri(PEOPLE).exchange(), 502, "Bad Gateway", PEOPLE);

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-22.5: when SWAPI takes longer than the read timeout, the list answers 504 and SWAPI receives a single request")
    void slowAnswerIsNotRetried() {
        // Not a recording as it was served: it is the recorded first page, held back for longer
        // than the application waits.
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .withBodyFile("people-page-1-limit-10.json")
                        .withFixedDelay(SLOW_ANSWER_MILLISECONDS)));

        expectProblem(client.get().uri(PEOPLE).exchange(), 504, "Gateway Timeout", PEOPLE);

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-22.6: when character 9999 does not exist, the detail answers 404 and SWAPI receives a single request")
    void characterThatDoesNotExistIsNotRetried() {
        // A recording: the "not found" that SWAPI really answers for character 9999.
        expectProblem(client.get().uri(PEOPLE + "/9999").exchange(), 404, "Not Found", PEOPLE + "/9999");

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_CHARACTER_9999)));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-22.7: when SWAPI answers 502 to a request and well to the next one, asking twice for the list answers 200 both times and SWAPI receives two requests in all")
    void answerThatArrivedInTheRetryIsStored() {
        // Not a recording: the simulated SWAPI is told to fail once.
        swapiFailsOnce(SWAPI_PEOPLE, error(502));

        String first = listOfPeople();
        String second = listOfPeople();

        assertThat(first).contains("Luke Skywalker");
        assertThat(second).isEqualTo(first);
        // The failed request and its retry. The second list did not go back to SWAPI: the answer
        // that arrived in the retry was stored like any other.
        swapi.verify(2, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    /** An error answer of SWAPI with a status and a text that the application must not let out. */
    private static ResponseDefinitionBuilder error(int status) {
        return aResponse()
                .withStatus(status)
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .withBody("{\"message\":\"" + SWAPI_ERROR_TEXT + "\"}");
    }

    /**
     * Tells the simulated SWAPI to fail the first request to a path and to answer the following
     * ones with its recording.
     *
     * <p>The failure is ahead of the recording only until it is served once: from then on it no
     * longer applies, and the recording answers.
     */
    private void swapiFailsOnce(String path, ResponseDefinitionBuilder failure) {
        swapi.stubFor(get(urlPathEqualTo(path)).atPriority(1)
                .inScenario("SWAPI fails once")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(failure)
                .willSetStateTo("SWAPI recovered"));
    }

    /** Asks for the first page of people, checks that it answers 200 and returns its body. */
    private String listOfPeople() {
        return client.get().uri(PEOPLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    /**
     * Checks an error in Problem Details format that shows nothing internal, and returns its
     * body.
     */
    private String expectProblem(RestTestClient.ResponseSpec response, int status, String title, String path) {
        byte[] bytes = response
                .expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo(title)
                .jsonPath("$.status").isEqualTo(status)
                .jsonPath("$.detail").value(String.class, detail -> assertThat(detail).isNotBlank())
                .jsonPath("$.instance").isEqualTo(path)
                .returnResult().getResponseBody();
        String body = new String(bytes, StandardCharsets.UTF_8);

        // Neither where SWAPI is, nor the names of classes or exceptions, nor a stack trace.
        assertThat(body)
                .doesNotContain(swapi.baseUrl())
                .doesNotContain("localhost")
                .doesNotContain("Exception")
                .doesNotContain("java.")
                .doesNotContain("springframework");
        return body;
    }

}
