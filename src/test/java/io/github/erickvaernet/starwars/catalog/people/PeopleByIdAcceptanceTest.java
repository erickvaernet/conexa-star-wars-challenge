package io.github.erickvaernet.starwars.catalog.people;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
 * Acceptance tests for REQ-06: asking for one character by its identifier, as a filter of the
 * list and as its detail.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and looks for SWAPI at
 * the simulated one, which answers with real recorded responses: character 1 as SWAPI gave it,
 * and the "not found" that SWAPI really answers for character 9999. Every request carries the
 * token of the test user.
 *
 * <p>Several tests also check what SWAPI was asked, because the same answer can come from the
 * wrong place: a route that does not exist answers 404 too, and an element can be found by
 * walking the whole list. So every test starts with the cache empty and with the count of
 * requests of the simulated SWAPI at zero.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class PeopleByIdAcceptanceTest {

    private static final String PEOPLE = "/api/v1/people";

    /** The path of the list of people in SWAPI. One character is at this path plus its identifier. */
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

    @Test
    @DisplayName("CA-06.1: the detail of character 1 has the complete data of Luke Skywalker, as the table of People says")
    void detailOfAnExistingCharacterHasItsCompleteData() {
        client.get().uri(PEOPLE + "/1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict: these twelve fields and no other, every value as text, and each list in
                // the order of SWAPI. The home planet and the internal data of SWAPI (created,
                // edited, url, description) are in the recording and must not be in the answer.
                .expectBody().json("""
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
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("CA-06.2: the detail of character 9999, which does not exist, is answered with 404")
    void detailOfACharacterThatDoesNotExistIsNotFound() {
        client.get().uri(PEOPLE + "/9999")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Not Found")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").value(String.class, detail -> assertThat(detail).isNotBlank())
                .jsonPath("$.instance").isEqualTo(PEOPLE + "/9999");

        // The 404 answers what SWAPI said about that character. A route that does not exist is
        // answered with a 404 as well, but without asking SWAPI anything.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/9999")));
    }

    @Test
    @DisplayName("CA-06.3: the list filtered by id=1 is a page with one element, Luke Skywalker, out of a total of 1")
    void listFilteredByIdHasThatElement() {
        client.get().uri(PEOPLE + "?id=1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict: the same shape as any other list, and the element is a summary.
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

        // The filter asks SWAPI for that element. It does not walk the list looking for it.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/1")));
        swapi.verify(0, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    @Test
    @DisplayName("CA-06.4: the list filtered by id=9999, which does not exist, is an empty page with a total of 0, not an error")
    void listFilteredByAnIdThatDoesNotExistIsEmpty() {
        client.get().uri(PEOPLE + "?id=9999")
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

        // The page is empty because SWAPI said that the character does not exist.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/9999")));
        swapi.verify(0, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)));
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        // In the route. Not a digit; a leading zero, alone or not; a sign; eleven digits.
        "/api/v1/people/abc",
        "/api/v1/people/0",
        "/api/v1/people/01",
        "/api/v1/people/-3",
        "/api/v1/people/12345678901",
        // The same values in the parameter of the list.
        "/api/v1/people?id=abc",
        "/api/v1/people?id=0",
        "/api/v1/people?id=01",
        "/api/v1/people?id=-3",
        "/api/v1/people?id=12345678901"
    })
    @DisplayName("CA-06.5: an identifier with an invalid format is rejected with 400, in the route and in the parameter")
    void invalidIdentifierIsRejected(String request) {
        String pathWithoutQuery = request.replaceFirst("\\?.*", "");

        client.get().uri(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Bad Request")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.instance").isEqualTo(pathWithoutQuery)
                // It names the parameter, in the same form as the other parameters.
                .jsonPath("$.detail").value(String.class, detail -> assertThat(detail).contains("'id'"));

        // It is rejected here: nothing is asked to SWAPI.
        swapi.verify(0, anyRequestedFor(anyUrl()));
    }

}
