package io.github.erickvaernet.starwars.catalog.starships;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * Acceptance tests for REQ-03: starships are listed one page at a time and detailed like every
 * other entity.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and looks for SWAPI at
 * the simulated one, which answers with real recorded responses: the first page of 5 starships
 * and starship 9. Every request carries the token of the test user.
 *
 * <p>SWAPI cuts the list of starships in pages, like the one of people: it answers the page and
 * the size it is asked for, with the total of the list. A recording answers one page and one
 * size only.
 *
 * <p>The simulated SWAPI answers 404 to a request that was not recorded, and the application
 * would turn that into an error or into "it does not exist". So the tests also check what SWAPI
 * was asked. For that, every test starts with the cache empty and with the count of requests of
 * the simulated SWAPI at zero.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class StarshipsAcceptanceTest {

    private static final String STARSHIPS = "/api/v1/starships";

    /** The path of the list of starships in SWAPI. One starship is at this path plus its identifier. */
    private static final String SWAPI_STARSHIPS = "/api/starships";

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
    @DisplayName("CA-03.1: the first page of 5 starships has 5 elements, out of 36 starships in 8 pages")
    void firstPageOfFiveStarships() {
        client.get().uri(STARSHIPS + "?page=1&size=5")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict: the same shape as any other list, each element with its identifier and
                // its name and nothing else. The order is the one of SWAPI, which is not the one
                // of the identifiers, and they have gaps: there is no starship 1.
                .expectBody().json("""
                        {
                          "content": [
                            { "id": "2", "name": "CR90 corvette" },
                            { "id": "3", "name": "Star Destroyer" },
                            { "id": "5", "name": "Sentinel-class landing craft" },
                            { "id": "9", "name": "Death Star" },
                            { "id": "11", "name": "Y-wing" }
                          ],
                          "page": 1,
                          "size": 5,
                          "totalElements": 36,
                          "totalPages": 8
                        }
                        """, JsonCompareMode.STRICT);

        // SWAPI was asked for that page and that size, and answered that page: the total is
        // the one it gave, and nothing was cut out here.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_STARSHIPS))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("limit", equalTo("5")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-06.6: the detail of starship 9 has the fields of the table of Starships, with its relations as lists of identifiers, in text")
    void detailOfAnExistingStarshipHasItsCompleteData() {
        client.get().uri(STARSHIPS + "/9")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict: these sixteen fields and no other.
                // - Every value is text, as SWAPI sends it: "342,953" keeps its comma, "4.0" its
                //   decimals, "3 years" its unit and "n/a" stays "n/a".
                // - SWAPI calls MGLT, in capital letters, what the API answers as mglt.
                // - The Death Star has one film and no pilots: an empty relation is an empty list.
                // - The internal data of SWAPI (created, edited, url, description) is in the
                //   recording and must not be in the answer.
                .expectBody().json("""
                        {
                          "id": "9",
                          "name": "Death Star",
                          "model": "DS-1 Orbital Battle Station",
                          "starshipClass": "Deep Space Mobile Battlestation",
                          "manufacturer": "Imperial Department of Military Research, Sienar Fleet Systems",
                          "costInCredits": "1000000000000",
                          "length": "120000",
                          "crew": "342,953",
                          "passengers": "843,342",
                          "maxAtmospheringSpeed": "n/a",
                          "hyperdriveRating": "4.0",
                          "mglt": "10",
                          "cargoCapacity": "1000000000000",
                          "consumables": "3 years",
                          "filmIds": ["1"],
                          "pilotIds": []
                        }
                        """, JsonCompareMode.STRICT);

        // The starship was asked to SWAPI by its identifier. The list was not walked to find it.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_STARSHIPS + "/9")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-24.5: the first page of 2 starships with expanded=true has two elements with exactly the fields of the table of Starships")
    void expandedPageOfStarshipsHasTheCompleteDataOfEachElement() {
        client.get().uri(STARSHIPS + "?expanded=true&size=2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict: the same page and the same totals as without the parameter, and each
                // element is the detail of the starship, with its sixteen fields and no other.
                // Every value is text, as SWAPI sends it: "1,600" keeps its comma, "30-165" its
                // range and "n/a" stays "n/a". The internal data of SWAPI is in the recording
                // and must not be in the answer.
                .expectBody().json("""
                        {
                          "content": [
                            {
                              "id": "2",
                              "name": "CR90 corvette",
                              "model": "CR90 corvette",
                              "starshipClass": "corvette",
                              "manufacturer": "Corellian Engineering Corporation",
                              "costInCredits": "3500000",
                              "length": "150",
                              "crew": "30-165",
                              "passengers": "600",
                              "maxAtmospheringSpeed": "950",
                              "hyperdriveRating": "2.0",
                              "mglt": "60",
                              "cargoCapacity": "3000000",
                              "consumables": "1 year",
                              "filmIds": ["1", "3", "6"],
                              "pilotIds": []
                            },
                            {
                              "id": "3",
                              "name": "Star Destroyer",
                              "model": "Imperial I-class Star Destroyer",
                              "starshipClass": "Star Destroyer",
                              "manufacturer": "Kuat Drive Yards",
                              "costInCredits": "150000000",
                              "length": "1,600",
                              "crew": "47,060",
                              "passengers": "n/a",
                              "maxAtmospheringSpeed": "975",
                              "hyperdriveRating": "2.0",
                              "mglt": "60",
                              "cargoCapacity": "36000000",
                              "consumables": "2 years",
                              "filmIds": ["1", "2", "3"],
                              "pilotIds": []
                            }
                          ],
                          "page": 1,
                          "size": 2,
                          "totalElements": 36,
                          "totalPages": 18
                        }
                        """, JsonCompareMode.STRICT);

        // The complete data came in one request, the one of the page, with the parameter that
        // SWAPI has for this. No starship was asked for on its own.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_STARSHIPS))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("limit", equalTo("2"))
                .withQueryParam("expanded", equalTo("true")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

}
