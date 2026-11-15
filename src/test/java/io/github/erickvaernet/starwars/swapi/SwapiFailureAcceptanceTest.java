package io.github.erickvaernet.starwars.swapi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-16: what the application answers when SWAPI fails or takes too long.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and looks for SWAPI at
 * the simulated one. Every request carries the token of the test user.
 *
 * <p>These answers of SWAPI are not recordings. Nobody can make the real SWAPI fail or slow down
 * on demand, so each test tells the simulated SWAPI what to answer, and the recordings come back
 * when the test ends.
 *
 * <p>The longest time the application waits for SWAPI is 10 seconds by default. Here it is
 * lowered to 1 second by configuration, so the test of the slow answer does not take that long.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"SEED_ENABLED=true", "SWAPI_READ_TIMEOUT=1s"})
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class SwapiFailureAcceptanceTest {

    private static final String PEOPLE = "/api/v1/people";

    /** The path of the list of people in SWAPI. */
    private static final String SWAPI_PEOPLE = "/api/people";

    /** A text that only the failed answer of SWAPI has, to check that it does not get out. */
    private static final String SWAPI_ERROR_TEXT = "upstream-failure-marker";

    /** Three times the longest wait configured for this class. */
    private static final int SLOW_ANSWER_MILLISECONDS = 3000;

    @LocalServerPort
    private int port;

    @Autowired
    private WireMockServer swapi;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = TestSession.open(RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build());
    }

    @AfterEach
    void bringTheRecordingsBack() {
        swapi.resetToDefaultMappings();
        swapi.resetRequests();
    }

    @Test
    @DisplayName("CA-16.2: when SWAPI answers with an error 500, the list answers 502 without internal details")
    void swapiErrorIsAnsweredWithBadGateway() {
        // Not a recording: the simulated SWAPI is told to fail.
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .withBody("{\"message\":\"" + SWAPI_ERROR_TEXT + "\"}")));

        String body = expectProblem(client.get().uri(PEOPLE).exchange(), 502, "Bad Gateway");

        assertThat(body).doesNotContain(SWAPI_ERROR_TEXT);
        // The 502 comes from the failure of SWAPI, not from a request that never left.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    @Test
    @DisplayName("CA-16.3: when SWAPI takes longer than the read timeout, the list answers 504")
    void slowSwapiIsAnsweredWithGatewayTimeout() {
        // Not a recording as it was served: it is the recorded first page, held back for longer
        // than the application waits.
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .withBodyFile("people-page-1-limit-10.json")
                        .withFixedDelay(SLOW_ANSWER_MILLISECONDS)));

        expectProblem(client.get().uri(PEOPLE).exchange(), 504, "Gateway Timeout");

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    /**
     * Checks an error in Problem Details format that shows nothing internal, and returns its
     * body.
     */
    private String expectProblem(RestTestClient.ResponseSpec response, int status, String title) {
        byte[] bytes = response
                .expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo(title)
                .jsonPath("$.status").isEqualTo(status)
                .jsonPath("$.detail").value(String.class, detail -> assertThat(detail).isNotBlank())
                .jsonPath("$.instance").isEqualTo(PEOPLE)
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
