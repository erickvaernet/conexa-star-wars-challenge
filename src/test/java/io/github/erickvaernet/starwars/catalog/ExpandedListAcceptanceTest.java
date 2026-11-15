package io.github.erickvaernet.starwars.catalog;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Acceptance tests for REQ-24: a list brings the complete data of each element when it is asked
 * for with {@code expanded=true}, and stays as it was without it.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and looks for SWAPI at
 * the simulated one, which answers with real recorded responses. SWAPI has the same parameter:
 * the recordings whose name ends in {@code -expanded} are what it answered to a request with
 * {@code expanded=true}, and they only answer a request that carries it. Every request carries
 * the token of the test user.
 *
 * <p>The criteria of the other three entities are in the class of each one, and the ones about
 * the cache, about a failure of SWAPI and about the API documentation are next to the others of
 * their kind.
 *
 * <p>The tests also check what SWAPI was asked, because the complete data can only come from one
 * request that carries {@code expanded=true}. For that, every test starts with the cache empty
 * and with the count of requests of the simulated SWAPI at zero.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class ExpandedListAcceptanceTest {

    private static final String PEOPLE = "/api/v1/people";

    /** The path of the list of people in SWAPI. One character is at this path plus its identifier. */
    private static final String SWAPI_PEOPLE = "/api/people";

    /** The fields of the table of People of the specification, which are the ones of its detail. */
    private static final List<String> FIELDS_OF_A_CHARACTER = List.of(
            "id", "name", "birthYear", "gender", "height", "mass", "hairColor", "skinColor", "eyeColor",
            "filmIds", "starshipIds", "vehicleIds");

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
    @DisplayName("CA-24.1: the first page of 2 people with expanded=true has the same totals, 82 people in 41 pages, and two elements with exactly the fields of the table of People")
    void expandedPageHasTheSameTotalsAndTheCompleteDataOfEachElement() {
        client.get().uri(PEOPLE + "?expanded=true&size=2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict: the page has the shape of any other list, and each element has these
                // twelve fields and no other, every value as text and each list in the order of
                // SWAPI. The home planet and the internal data of SWAPI (created, edited, url,
                // description) are in the recording and must not be in the answer.
                .expectBody().json("""
                        {
                          "content": [
                            {
                              "id": "1",
                              "name": "Luke Skywalker",
                              "birthYear": "19BBY",
                              "gender": "male",
                              "height": "172",
                              "mass": "77",
                              "hairColor": "blond",
                              "skinColor": "fair",
                              "eyeColor": "blue",
                              "filmIds": ["1", "2", "3", "6"],
                              "starshipIds": ["12", "22"],
                              "vehicleIds": ["14", "30"]
                            },
                            {
                              "id": "2",
                              "name": "C-3PO",
                              "birthYear": "112BBY",
                              "gender": "n/a",
                              "height": "167",
                              "mass": "75",
                              "hairColor": "n/a",
                              "skinColor": "gold",
                              "eyeColor": "yellow",
                              "filmIds": ["1", "2", "3", "4", "5", "6"],
                              "starshipIds": [],
                              "vehicleIds": []
                            }
                          ],
                          "page": 1,
                          "size": 2,
                          "totalElements": 82,
                          "totalPages": 41
                        }
                        """, JsonCompareMode.STRICT);

        // The complete data came in one request, the one of the page, with the parameter that
        // SWAPI has for this. No element was asked for on its own.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("limit", equalTo("2"))
                .withQueryParam("expanded", equalTo("true")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-24.2: the first element of the page with expanded=true is equal, field by field, to the detail of character 1, Luke Skywalker")
    void expandedElementIsEqualToItsDetail() {
        // The detail is asked for first, with the cache empty, so it comes from its own recording:
        // the one of character 1 asked for alone. Asked for after the list, it would be the
        // element that the list left stored, and the two would be equal whatever they were.
        Map<String, Object> detail = JsonPath.read(getOk(PEOPLE + "/1"), "$");
        Map<String, Object> first = JsonPath.read(listOfPeople("?expanded=true&size=2"), "$.content[0]");

        // The two answers of SWAPI carry the same data, and the API translates both the same way.
        assertThat(detail).containsEntry("name", "Luke Skywalker");
        assertThat(detail.keySet()).containsExactlyInAnyOrderElementsOf(FIELDS_OF_A_CHARACTER);
        assertThat(first).isEqualTo(detail);
        // Each one came from its own request to SWAPI.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/1")));
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE))
                .withQueryParam("expanded", equalTo("true")));
        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest(name = "GET {0}?expanded=false")
    @ValueSource(strings = {
        "/api/v1/people",
        "/api/v1/films",
        "/api/v1/starships",
        "/api/v1/vehicles"
    })
    @DisplayName("CA-24.3: a list with expanded=false is the same as without the parameter: every element has exactly id and name")
    void listWithExpandedFalseIsTheSameAsWithoutTheParameter(String path) {
        String withoutTheParameter = getOk(path);
        // The cache is emptied in between: the second answer is not the first one read again,
        // it is what the application builds for expanded=false from what SWAPI answers.
        caches.forEach(Cache::invalidateAll);
        String withExpandedFalse = getOk(path + "?expanded=false");

        assertThat(JsonPath.<Map<String, Object>>read(withExpandedFalse, "$"))
                .isEqualTo(JsonPath.<Map<String, Object>>read(withoutTheParameter, "$"));
        List<Map<String, Object>> content = JsonPath.read(withExpandedFalse, "$.content");
        assertThat(content).isNotEmpty().allSatisfy(element -> assertThat(element).containsOnlyKeys("id", "name"));

        // Neither of the two requests asked SWAPI for the complete data.
        swapi.verify(2, anyRequestedFor(anyUrl()).withQueryParam("expanded", absent()));
        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-24.6: the people whose name contains \"sky\", with expanded=true and 2 per page, are 3 in 2 pages, and the page has two elements with their complete data")
    void expandedSearchByNameHasTheCompleteDataOfTheMatches() {
        client.get().uri(PEOPLE + "?name=sky&expanded=true&size=2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict. SWAPI answers a search with every match at once, whatever the page and
                // the size: the three are in the recording, and the third one, Shmi Skywalker,
                // belongs to the second page.
                .expectBody().json("""
                        {
                          "content": [
                            {
                              "id": "1",
                              "name": "Luke Skywalker",
                              "birthYear": "19BBY",
                              "gender": "male",
                              "height": "172",
                              "mass": "77",
                              "hairColor": "blond",
                              "skinColor": "fair",
                              "eyeColor": "blue",
                              "filmIds": ["1", "2", "3", "6"],
                              "starshipIds": ["12", "22"],
                              "vehicleIds": ["14", "30"]
                            },
                            {
                              "id": "11",
                              "name": "Anakin Skywalker",
                              "birthYear": "41.9BBY",
                              "gender": "male",
                              "height": "188",
                              "mass": "84",
                              "hairColor": "blond",
                              "skinColor": "fair",
                              "eyeColor": "blue",
                              "filmIds": ["4", "5", "6"],
                              "starshipIds": ["39", "59", "65"],
                              "vehicleIds": ["44", "46"]
                            }
                          ],
                          "page": 1,
                          "size": 2,
                          "totalElements": 3,
                          "totalPages": 2
                        }
                        """, JsonCompareMode.STRICT);

        // One request: the search, with the parameter. SWAPI ignores it there, because a search
        // already brings the complete data, but it is sent whenever it is asked for.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE))
                .withQueryParam("name", equalTo("sky"))
                .withQueryParam("expanded", equalTo("true")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-24.7: with expanded=true, the list filtered by id=1 has one element equal to the detail of character 1, and the one filtered by id=9999 is empty with a total of 0")
    void expandedListFilteredByIdHasTheDetailOfThatElement() {
        String filtered = listOfPeople("?id=1&expanded=true");

        Map<String, Object> page = JsonPath.read(filtered, "$");
        assertThat(page).containsOnlyKeys("content", "page", "size", "totalElements", "totalPages")
                .containsEntry("page", 1)
                .containsEntry("size", 10)
                .containsEntry("totalElements", 1)
                .containsEntry("totalPages", 1);
        List<Map<String, Object>> content = JsonPath.read(filtered, "$.content");
        assertThat(content).hasSize(1);
        // The filter and the detail ask SWAPI for the same element, so the second one is
        // answered by the cache: what is compared is what the detail answers, whatever its source.
        Map<String, Object> detail = JsonPath.read(getOk(PEOPLE + "/1"), "$");
        assertThat(detail.keySet()).containsExactlyInAnyOrderElementsOf(FIELDS_OF_A_CHARACTER);
        assertThat(content.getFirst()).isEqualTo(detail);

        client.get().uri(PEOPLE + "?id=9999&expanded=true")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("""
                        {
                          "content": [],
                          "page": 1,
                          "size": 10,
                          "totalElements": 0,
                          "totalPages": 0
                        }
                        """, JsonCompareMode.STRICT);

        // Each element was asked to SWAPI by its identifier. The list was not walked.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/1")));
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/9999")));
        swapi.verify(0, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    @Test
    @DisplayName("CA-24.8: with expanded=true, the list filtered by id=1 and name=vader is empty with a total of 0: character 1 is Luke Skywalker")
    void expandedListFilteredByIdAndByANameThatDoesNotMatchIsEmpty() {
        client.get().uri(PEOPLE + "?id=1&name=vader&expanded=true")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("""
                        {
                          "content": [],
                          "page": 1,
                          "size": 10,
                          "totalElements": 0,
                          "totalPages": 0
                        }
                        """, JsonCompareMode.STRICT);

        // The page is empty because of the name of the character that SWAPI did return, not
        // because the character was not found. No search was made.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/1")));
        swapi.verify(0, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    @Test
    @DisplayName("CA-24.9: page 50 of the people with expanded=true is empty and keeps the right totals: 82 people in 9 pages")
    void expandedPageAfterTheLastOneIsEmptyWithTheRightTotals() {
        // With expanded=true SWAPI answers a page after the last one the same way as without it:
        // the recording shows that it repeats its last page, with the complete data of its two
        // characters. The application must not pass those elements on.
        client.get().uri(PEOPLE + "?page=50&expanded=true")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("""
                        {
                          "content": [],
                          "page": 50,
                          "size": 10,
                          "totalElements": 82,
                          "totalPages": 9
                        }
                        """, JsonCompareMode.STRICT);

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE))
                .withQueryParam("page", equalTo("50"))
                .withQueryParam("limit", equalTo("10"))
                .withQueryParam("expanded", equalTo("true")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    // The third value is the parameter sent without a value: "expanded=".
    @ParameterizedTest(name = "expanded={0}")
    @CsvSource({
        "1",
        "TRUE",
        "''"
    })
    @DisplayName("CA-24.10: a value of expanded that is not exactly true or false is rejected with 400, the detail and errors name the parameter, and SWAPI receives no request")
    void invalidValueOfExpandedIsRejected(String value) {
        client.get().uri(PEOPLE + "?expanded=" + value)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                // Strict: the fields of every error of the API and errors, nothing else. The two
                // texts are the fixed ones of the specification: the name of the parameter and
                // its rule, without the value that was received.
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Parameter 'expanded' must be true or false.",
                          "instance": "/api/v1/people",
                          "errors": [
                            { "field": "expanded", "message": "must be true or false" }
                          ]
                        }
                        """, JsonCompareMode.STRICT);

        // It is rejected here: nothing is asked to SWAPI.
        swapi.verify(0, anyRequestedFor(anyUrl()));
    }

    /** Asks for the list of people, checks that it answers 200 and returns the body. */
    private String listOfPeople(String query) {
        return getOk(PEOPLE + query);
    }

    /** Asks for a list or for a detail, checks that the answer is a 200 and returns its body. */
    private String getOk(String path) {
        return client.get().uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

}
