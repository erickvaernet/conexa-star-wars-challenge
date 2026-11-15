package io.github.erickvaernet.starwars.catalog.people;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-07 and REQ-08: filtering the list of people by a part of the name,
 * alone and together with the identifier.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and looks for SWAPI at
 * the simulated one, which answers with real recorded responses: the search of SWAPI for "sky",
 * with its three characters, the one for "zzzz", with none, and character 1. Every request
 * carries the token of the test user.
 *
 * <p>The simulated SWAPI answers 404 to a request that was not recorded, and the application
 * would turn that into an error or into "it does not exist". So the tests also check what SWAPI
 * was asked: an empty page has to be empty because SWAPI said so, and the filter by identifier
 * and name must not search SWAPI by name. For that, every test starts with the cache empty and
 * with the count of requests of the simulated SWAPI at zero.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class PeopleByNameAcceptanceTest {

    private static final String PEOPLE = "/api/v1/people";

    /** The path of the list of people in SWAPI. A search is this path with the text to look for. */
    private static final String SWAPI_PEOPLE = "/api/people";

    /**
     * The three characters whose name contains "sky", in the order of SWAPI, as the first page
     * of 10. Strict: the same shape as any other list, and each element is a summary.
     */
    private static final String THE_THREE_SKYWALKERS = """
            {
              "content": [
                { "id": "1", "name": "Luke Skywalker" },
                { "id": "11", "name": "Anakin Skywalker" },
                { "id": "43", "name": "Shmi Skywalker" }
              ],
              "page": 1,
              "size": 10,
              "totalElements": 3,
              "totalPages": 1
            }
            """;

    private static final String EMPTY_PAGE = """
            {
              "content": [],
              "page": 1,
              "size": 10,
              "totalElements": 0,
              "totalPages": 0
            }
            """;

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

    @Test
    @DisplayName("CA-07.1: the list filtered by name=sky has the 3 characters whose name contains \"sky\"")
    void listFilteredByAPartOfTheNameHasTheMatches() {
        client.get().uri(PEOPLE + "?name=sky")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json(THE_THREE_SKYWALKERS, JsonCompareMode.STRICT);

        // The matches are the ones SWAPI found: it was asked to search, once.
        swapi.verify(1, searchOfPeopleFor("sky"));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-07.2: name=SKY gives the same result as name=sky: upper and lower case are not told apart")
    void searchDoesNotTellUpperFromLowerCase() {
        client.get().uri(PEOPLE + "?name=SKY")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json(THE_THREE_SKYWALKERS, JsonCompareMode.STRICT);

        // The cache was empty, so this answer did not come from a search for "sky" made before.
        // The recording answers the text in lower case and no other: both searches reach SWAPI
        // as the same request.
        swapi.verify(1, searchOfPeopleFor("sky"));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-07.3: the second page of 2 of the search for \"sky\" has the third character, out of 3 in 2 pages")
    void searchIsCutInPages() {
        client.get().uri(PEOPLE + "?name=sky&page=2&size=2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // SWAPI answers a search with every match at once, whatever the page: the page
                // is cut out here, and the totals are counted here.
                .expectBody().json("""
                        {
                          "content": [
                            { "id": "43", "name": "Shmi Skywalker" }
                          ],
                          "page": 2,
                          "size": 2,
                          "totalElements": 3,
                          "totalPages": 2
                        }
                        """, JsonCompareMode.STRICT);

        swapi.verify(1, searchOfPeopleFor("sky"));
    }

    @Test
    @DisplayName("CA-07.5: a search without matches, name=zzzz, is an empty page with the totals in 0, not an error")
    void searchWithoutMatchesIsEmpty() {
        client.get().uri(PEOPLE + "?name=zzzz")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json(EMPTY_PAGE, JsonCompareMode.STRICT);

        // The page is empty because SWAPI searched and found nothing.
        swapi.verify(1, searchOfPeopleFor("zzzz"));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    static Stream<Arguments> invalidNames() {
        return Stream.of(
                Arguments.of("empty", "?name={name}", ""),
                Arguments.of("only spaces", "?name={name}", "   "),
                Arguments.of("101 characters", "?name={name}", "a".repeat(101)),
                // 100 characters are accepted, and the spaces around them do not count: here
                // there are 101 that count.
                Arguments.of("101 characters between spaces", "?name={name}", "  " + "a".repeat(101) + "  "),
                // A valid identifier next to it does not save it.
                Arguments.of("empty, next to a valid id", "?id=1&name={name}", ""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidNames")
    @DisplayName("CA-07.6: a name that is empty, only spaces or longer than 100 characters is rejected with 400")
    void invalidNameIsRejected(String description, String query, String name) {
        // The name goes as a variable, so that its spaces travel encoded (%20), as a client
        // would send them.
        client.get().uri(PEOPLE + query, name)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Bad Request")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.instance").isEqualTo(PEOPLE)
                // It names the parameter, in the same form as the other parameters.
                .jsonPath("$.detail").value(String.class, detail -> assertThat(detail).contains("'name'"));

        // It is rejected here: nothing is asked to SWAPI.
        swapi.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-08.1: the list filtered by id=1 and name=luke has one element, character 1")
    void listFilteredByIdAndNameHasTheElementThatMeetsBoth() {
        // The text is in lower case and the name is "Luke Skywalker": the comparison does not
        // tell them apart.
        client.get().uri(PEOPLE + "?id=1&name=luke")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("""
                        {
                          "content": [
                            { "id": "1", "name": "Luke Skywalker" }
                          ],
                          "page": 1,
                          "size": 10,
                          "totalElements": 1,
                          "totalPages": 1
                        }
                        """, JsonCompareMode.STRICT);

        // The element is asked for by its identifier, and its name is checked here. SWAPI is
        // not searched by name.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/1")));
        swapi.verify(0, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    @Test
    @DisplayName("CA-08.2: the list filtered by id=1 and name=vader is an empty page with a total of 0: character 1 is not Vader")
    void listFilteredByIdAndNameIsEmptyWhenTheNameDoesNotMatch() {
        client.get().uri(PEOPLE + "?id=1&name=vader")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json(EMPTY_PAGE, JsonCompareMode.STRICT);

        // The page is empty because character 1 exists and its name does not contain the text,
        // not because of a search that was not recorded.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/1")));
        swapi.verify(0, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    /** A request to SWAPI that searches the people whose name contains that text. */
    private static RequestPatternBuilder searchOfPeopleFor(String text) {
        return getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)).withQueryParam("name", equalTo(text));
    }

}
