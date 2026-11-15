package io.github.erickvaernet.starwars.swapi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-32: the answers of SWAPI are kept for a while, so that a repeated
 * request is answered without going back to SWAPI.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and looks for SWAPI at
 * the simulated one. Every request carries the token of the test user. Each test counts how many
 * requests the simulated SWAPI received: that is the only way to tell from outside whether an
 * answer came from the cache.
 *
 * <p>The cache runs with its default settings: an answer expires one hour after it was stored.
 * No test waits for that hour. The cache reads the time from the clock that this class gives it,
 * which only moves when a test tells it to.
 *
 * <p>Every test starts with the cache empty, so none depends on what another one left stored.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class SwapiCacheAcceptanceTest {

    private static final String PEOPLE = "/api/v1/people";

    /** The path of the list of people in SWAPI. */
    private static final String SWAPI_PEOPLE = "/api/people";

    @LocalServerPort
    private int port;

    @Autowired
    private WireMockServer swapi;

    /** The clock of the cache, in the hands of the tests. */
    @Autowired
    private ManualClock clock;

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
    @DisplayName("CA-32.1: with the cache empty, asking twice for the same page of people sends SWAPI a single request")
    void repeatedRequestDoesNotGoBackToSwapi() {
        String first = listOfPeople("?page=1");
        String second = listOfPeople("?page=1");

        // The stored answer is the real page, and the client cannot tell it from a fresh one.
        assertThat(first).contains("Luke Skywalker");
        assertThat(second).isEqualTo(first);
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    @Test
    @DisplayName("CA-32.3: once the expiry of one hour has passed, repeating a request sends SWAPI a new one")
    void expiredAnswerIsAskedAgain() {
        listOfPeople("");

        // One minute before the expiry, the answer is still the stored one.
        clock.advance(Duration.ofMinutes(59));
        listOfPeople("");
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));

        // One minute after it, the same request goes back to SWAPI.
        clock.advance(Duration.ofMinutes(2));
        listOfPeople("");
        swapi.verify(2, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    @Test
    @DisplayName("CA-32.4: an error of SWAPI is not stored: 502 the first time, 200 once SWAPI recovers, and SWAPI receives two requests")
    void errorOfSwapiIsNotStored() {
        // Not a recording: the simulated SWAPI is told to fail.
        StubMapping failure = swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .withBody("{\"message\":\"Internal Server Error\"}")));

        client.get().uri(PEOPLE).exchange().expectStatus().isEqualTo(502);

        // SWAPI recovers: it answers with the recording again.
        swapi.removeStub(failure);

        assertThat(listOfPeople("")).contains("Luke Skywalker");
        swapi.verify(2, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));

        // The answer that did arrive is stored like any other. Without this last step the test
        // would also pass with no cache at all, and it would prove nothing about the error.
        listOfPeople("");
        swapi.verify(2, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    @Test
    @DisplayName("CA-32.5: a character that does not exist is not stored: asking twice for character 9999 answers 404 both times, and SWAPI receives two requests")
    void characterThatDoesNotExistIsNotStored() {
        // A recording: the "not found" that SWAPI really answers for character 9999.
        client.get().uri(PEOPLE + "/9999").exchange().expectStatus().isNotFound();
        client.get().uri(PEOPLE + "/9999").exchange().expectStatus().isNotFound();

        swapi.verify(2, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/9999")));

        // A character that does exist is stored like any other answer. Without this last step
        // the test would also pass with no cache at all, and it would prove nothing about the
        // one that does not exist.
        client.get().uri(PEOPLE + "/1").exchange().expectStatus().isOk();
        client.get().uri(PEOPLE + "/1").exchange().expectStatus().isOk();
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/1")));
    }

    @Test
    @DisplayName("The detail of a character and the list filtered by its identifier share one stored answer: SWAPI receives a single request")
    void detailAndFilterByIdShareOneStoredAnswer() {
        client.get().uri(PEOPLE + "/1").exchange().expectStatus().isOk();

        assertThat(listOfPeople("?id=1")).contains("Luke Skywalker");

        // Neither a second request for the character nor one for the list.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/1")));
        swapi.verify(0, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    @Test
    @DisplayName("Walking the pages of a search by name sends SWAPI a single request: the whole answer is stored, whatever the page")
    void pagesOfASearchShareOneStoredAnswer() {
        String firstPage = listOfPeople("?name=sky&size=2&page=1");
        String secondPage = listOfPeople("?name=sky&size=2&page=2");
        // Another page size is not another request either.
        String wholeSearch = listOfPeople("?name=sky&size=10");

        // Each page is cut out of the same stored answer.
        assertThat(firstPage).contains("Luke Skywalker", "Anakin Skywalker").doesNotContain("Shmi Skywalker");
        assertThat(secondPage).contains("Shmi Skywalker").doesNotContain("Luke Skywalker", "Anakin Skywalker");
        assertThat(wholeSearch).contains("Luke Skywalker", "Anakin Skywalker", "Shmi Skywalker");
        swapi.verify(1, searchOfPeopleFor("sky"));
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    @Test
    @DisplayName("Two searches that differ only in upper and lower case, or in the spaces around the text, share one stored answer")
    void searchesThatDifferOnlyInCaseShareOneStoredAnswer() {
        String lowerCase = peopleNamed("sky");
        String upperCase = peopleNamed("SKY");
        String betweenSpaces = peopleNamed("  Sky ");

        assertThat(lowerCase).contains("Luke Skywalker", "Anakin Skywalker", "Shmi Skywalker");
        assertThat(upperCase).isEqualTo(lowerCase);
        assertThat(betweenSpaces).isEqualTo(lowerCase);
        // One request, and it is the search: the three answers are not the plain list of people.
        swapi.verify(1, searchOfPeopleFor("sky"));
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    @Test
    @DisplayName("A search without matches is stored like any correct answer: asking twice for it sends SWAPI a single request")
    void searchWithoutMatchesIsStored() {
        // A recording: SWAPI really answers 200 with an empty list, not a "not found".
        String first = listOfPeople("?name=zzzz");
        String second = listOfPeople("?name=zzzz");

        assertThat(first).contains("\"totalElements\":0");
        assertThat(second).isEqualTo(first);
        swapi.verify(1, searchOfPeopleFor("zzzz"));
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    /** A request to SWAPI that searches the people whose name contains that text. */
    private static RequestPatternBuilder searchOfPeopleFor(String text) {
        return getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)).withQueryParam("name", equalTo(text));
    }

    /**
     * Asks for the people whose name contains that text, checks that it answers 200 and returns
     * the body. The text goes as a variable, so that its spaces travel encoded (%20).
     */
    private String peopleNamed(String name) {
        byte[] body = client.get().uri(PEOPLE + "?name={name}", name)
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult().getResponseBody();
        return new String(body, StandardCharsets.UTF_8);
    }

    /** Asks for the list of people, checks that it answers 200 and returns the body. */
    private String listOfPeople(String query) {
        byte[] body = client.get().uri(PEOPLE + query)
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult().getResponseBody();
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * Puts the clock of the cache in the hands of the tests: the application uses this clock
     * instead of the one of the system.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {

        @Bean
        ManualClock cacheClock() {
            return new ManualClock();
        }

    }

}
