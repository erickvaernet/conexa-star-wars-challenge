package io.github.erickvaernet.starwars.swapi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import java.io.IOException;
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
import org.springframework.core.io.ClassPathResource;
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

    /** The path of character 1 in SWAPI. */
    private static final String SWAPI_CHARACTER_1 = "/api/people/1";

    /** The address of one of the films of character 1, as it is in the recorded answer. */
    private static final String FILM_ADDRESS = "https://www.swapi.tech/api/films/2";

    /**
     * The address of a film that, in the recorded page of two people with their complete data,
     * only the second character has.
     */
    private static final String FILM_ADDRESS_OF_THE_SECOND_CHARACTER = "https://www.swapi.tech/api/films/5";

    /** The same address with nothing after its last slash: no identifier can be taken from it. */
    private static final String FILM_ADDRESS_WITHOUT_IDENTIFIER = "https://www.swapi.tech/api/films/";

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

    @Test
    @DisplayName("CA-16.5: when SWAPI gives a character a relation whose address does not end in an identifier, the detail answers 502 without internal details")
    void relationWithoutAnIdentifierIsAnsweredWithBadGateway() throws IOException {
        // Not a recording as it was served, and the only answer of the tests that alters real
        // data: it is the recorded answer for character 1, with the address of one of its four
        // films cut right before the identifier. Everything else is what SWAPI sent.
        String recorded = new ClassPathResource("swapi/__files/people-1.json")
                .getContentAsString(StandardCharsets.UTF_8);
        String altered = recorded.replace('"' + FILM_ADDRESS + '"', '"' + FILM_ADDRESS_WITHOUT_IDENTIFIER + '"');
        assertThat(altered).isNotEqualTo(recorded);
        swapi.stubFor(get(urlPathEqualTo(SWAPI_CHARACTER_1)).atPriority(1)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .withBody(altered)));

        // With three good addresses out of four, an answer of 200 with three films would mean
        // that the relation was left out in silence.
        byte[] bytes = client.get().uri(PEOPLE + "/1")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Bad Gateway")
                .jsonPath("$.status").isEqualTo(502)
                .jsonPath("$.detail").value(String.class, detail -> assertThat(detail).isNotBlank())
                .jsonPath("$.instance").isEqualTo(PEOPLE + "/1")
                .returnResult().getResponseBody();

        // Neither the address that could not be read, nor where SWAPI is, nor the names of
        // classes or exceptions, nor a stack trace.
        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .doesNotContain("swapi.tech")
                .doesNotContain(swapi.baseUrl())
                .doesNotContain("localhost")
                .doesNotContain("Exception")
                .doesNotContain("java.")
                .doesNotContain("springframework");
        // The 502 comes from what SWAPI answered, not from a request that never left.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_CHARACTER_1)));
    }

    @Test
    @DisplayName("CA-24.14: when SWAPI gives, in a page with expanded=true, a character a relation whose address does not end in an identifier, the list answers 502 without internal details")
    void relationWithoutAnIdentifierInAnExpandedPageIsAnsweredWithBadGateway() throws IOException {
        // Not a recording as it was served: it is the recorded first page of two people with
        // expanded=true, with the address of one of the six films of its second character,
        // C-3PO, cut right before the identifier. That address is only once in the page:
        // everything else, the first character included, is what SWAPI sent.
        String recorded = new ClassPathResource("swapi/__files/people-page-1-limit-2-expanded.json")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(recorded).containsOnlyOnce('"' + FILM_ADDRESS_OF_THE_SECOND_CHARACTER + '"');
        String altered = recorded.replace(
                '"' + FILM_ADDRESS_OF_THE_SECOND_CHARACTER + '"', '"' + FILM_ADDRESS_WITHOUT_IDENTIFIER + '"');
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).withQueryParam("expanded", equalTo("true")).atPriority(1)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .withBody(altered)));

        // With one good character out of two, an answer of 200 with one element, or with the
        // second one short of a film, would mean that something was left out in silence.
        String body = expectProblem(client.get().uri(PEOPLE + "?expanded=true&size=2").exchange(), 502, "Bad Gateway");

        // Nor the address that could not be read.
        assertThat(body).doesNotContain("swapi.tech");
        // The 502 comes from what SWAPI answered to the request for the complete data, not from
        // a request that never left.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)).withQueryParam("expanded", equalTo("true")));
    }

    @Test
    @DisplayName("When SWAPI starts to answer in time and stops halfway, the list answers 504, not 502")
    void swapiThatStopsHalfwayIsAnsweredWithGatewayTimeout() {
        // Not a recording as it was served: it is the recorded first page, sent in five pieces
        // along a time longer than the application waits. The headers arrive at once, so the
        // wait runs out with the answer already begun.
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .withBodyFile("people-page-1-limit-10.json")
                        .withChunkedDribbleDelay(5, SLOW_ANSWER_MILLISECONDS)));

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
