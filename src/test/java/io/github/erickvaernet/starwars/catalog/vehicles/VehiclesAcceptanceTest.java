package io.github.erickvaernet.starwars.catalog.vehicles;

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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-04: vehicles are listed one page at a time and detailed like every
 * other entity.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and looks for SWAPI at
 * the simulated one, which answers with real recorded responses: the first page of 10 vehicles
 * and vehicle 4. Every request carries the token of the test user.
 *
 * <p>SWAPI cuts the list of vehicles in pages, like the ones of people and starships: it answers
 * the page and the size it is asked for, with the total of the list. A recording answers one page
 * and one size only.
 *
 * <p>The simulated SWAPI answers 404 to a request that was not recorded, and the application
 * would turn that into an error or into "it does not exist". So the tests also check what SWAPI
 * was asked. For that, every test starts with the cache empty and with the count of requests of
 * the simulated SWAPI at zero.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class VehiclesAcceptanceTest {

    private static final String VEHICLES = "/api/v1/vehicles";

    /** The path of the list of vehicles in SWAPI. One vehicle is at this path plus its identifier. */
    private static final String SWAPI_VEHICLES = "/api/vehicles";

    /** The recorded answer of SWAPI for vehicle 4, as the simulated SWAPI serves it. */
    private static final String RECORDING_OF_VEHICLE_4 = "swapi/__files/vehicles-4.json";

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
    @DisplayName("CA-04.1: the list of vehicles is answered with 200 and with the same shape as the other three entities")
    void listOfVehiclesHasTheShapeOfEveryOtherList() {
        client.get().uri(VEHICLES)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict: the same shape as any other list, each element with its identifier and
                // its name and nothing else. Without parameters it is the first page of 10. The
                // order is the one of SWAPI, which is not the one of the identifiers (7 comes
                // before 6), and they have gaps: the first vehicle is the 4.
                .expectBody().json("""
                        {
                          "content": [
                            { "id": "4", "name": "Sand Crawler" },
                            { "id": "7", "name": "X-34 landspeeder" },
                            { "id": "6", "name": "T-16 skyhopper" },
                            { "id": "8", "name": "TIE/LN starfighter" },
                            { "id": "14", "name": "Snowspeeder" },
                            { "id": "18", "name": "AT-AT" },
                            { "id": "16", "name": "TIE bomber" },
                            { "id": "19", "name": "AT-ST" },
                            { "id": "20", "name": "Storm IV Twin-Pod cloud car" },
                            { "id": "24", "name": "Sail barge" }
                          ],
                          "page": 1,
                          "size": 10,
                          "totalElements": 39,
                          "totalPages": 4
                        }
                        """, JsonCompareMode.STRICT);

        // SWAPI was asked for that page and that size, and answered that page: the total is
        // the one it gave, and nothing was cut out here.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_VEHICLES))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("limit", equalTo("10")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-06.6: the detail of vehicle 4 has the fields of the table of Vehicles, with its relations as lists of identifiers, in text")
    void detailOfAnExistingVehicleHasItsCompleteData() {
        client.get().uri(VEHICLES + "/4")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict: these fourteen fields and no other.
                // - Every value is text, as SWAPI sends it: "2 months" keeps its unit.
                // - A vehicle has no hyperdrive rating and no MGLT: those are of a starship.
                // - The Sand Crawler has two films and no pilots: an empty relation is an empty
                //   list.
                // - The internal data of SWAPI (created, edited, url, description) is in the
                //   recording and must not be in the answer.
                .expectBody().json("""
                        {
                          "id": "4",
                          "name": "Sand Crawler",
                          "model": "Digger Crawler",
                          "vehicleClass": "wheeled",
                          "manufacturer": "Corellia Mining Corporation",
                          "costInCredits": "150000",
                          "length": "36.8",
                          "crew": "46",
                          "passengers": "30",
                          "maxAtmospheringSpeed": "30",
                          "cargoCapacity": "50000",
                          "consumables": "2 months",
                          "filmIds": ["1", "5"],
                          "pilotIds": []
                        }
                        """, JsonCompareMode.STRICT);

        // The vehicle was asked to SWAPI by its identifier. The list was not walked to find it.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_VEHICLES + "/4")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-06.7: the length of vehicle 4, which SWAPI gives as \"36.8 \" with a space at the end, is answered as \"36.8\"")
    void textWithASpaceAtTheEndIsAnsweredWithoutTheSpace() throws IOException {
        // The given of the criterion: the answer that the simulated SWAPI serves for vehicle 4
        // does bring the space. Without this check, the test would keep passing if the recording
        // were ever "fixed", and it would prove nothing.
        String recorded = new ClassPathResource(RECORDING_OF_VEHICLE_4).getContentAsString(StandardCharsets.UTF_8);
        String lengthInSwapi = JsonPath.read(recorded, "$.result.properties.length");
        assertThat(lengthInSwapi).isEqualTo("36.8 ");

        client.get().uri(VEHICLES + "/4")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length").isEqualTo("36.8");

        // That answer came from that recording: SWAPI was asked for vehicle 4, once.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_VEHICLES + "/4")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

}
