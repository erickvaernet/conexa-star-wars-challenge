package io.github.erickvaernet.starwars.catalog.films;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
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
 * Acceptance tests for REQ-02: films are listed one page at a time, searched and detailed like
 * every other entity, although SWAPI treats them in its own way.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and looks for SWAPI at
 * the simulated one, which answers with real recorded responses: the list of films, the search
 * for the title "hope" and film 1. Every request carries the token of the test user.
 *
 * <p>SWAPI does not cut the list of films in pages: it answers the six of them whatever the page
 * and the size it is asked for, and the recording answers the same way. It searches them by
 * their title, not by a name. The API hides both things: here a film is paged and filtered like
 * a character, and its title is its name in a list.
 *
 * <p>The simulated SWAPI answers 404 to a request that was not recorded, and the application
 * would turn that into an error or into "it does not exist". So the tests also check what SWAPI
 * was asked. For that, every test starts with the cache empty and with the count of requests of
 * the simulated SWAPI at zero.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class FilmsAcceptanceTest {

    private static final String FILMS = "/api/v1/films";

    /** The path of the list of films in SWAPI. One film is at this path plus its identifier. */
    private static final String SWAPI_FILMS = "/api/films";

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
    @DisplayName("CA-02.1: the second page of 2 films has the 3rd and the 4th in the order of SWAPI, out of 6 films in 3 pages")
    void listOfFilmsIsCutInPagesHere() {
        client.get().uri(FILMS + "?page=2&size=2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict: the same shape as any other list. The order of SWAPI is the one of its
                // identifiers, which is the order of release and not the one of the episodes.
                .expectBody().json("""
                        {
                          "content": [
                            { "id": "3", "name": "Return of the Jedi" },
                            { "id": "4", "name": "The Phantom Menace" }
                          ],
                          "page": 2,
                          "size": 2,
                          "totalElements": 6,
                          "totalPages": 3
                        }
                        """, JsonCompareMode.STRICT);

        // SWAPI was asked for that page and that size, like for any entity, and answered the
        // six films anyway: the page was cut out here, and the totals were counted here.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_FILMS))
                .withQueryParam("page", equalTo("2"))
                .withQueryParam("limit", equalTo("2")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-02.2: every element of the list of films has id and name, and the name is the title of the film")
    void nameOfAFilmInTheListIsItsTitle() {
        client.get().uri(FILMS)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict: each element has its identifier and its name and nothing else. SWAPI
                // sends every film with all its data, and none of it may reach a list.
                .expectBody().json("""
                        {
                          "content": [
                            { "id": "1", "name": "A New Hope" },
                            { "id": "2", "name": "The Empire Strikes Back" },
                            { "id": "3", "name": "Return of the Jedi" },
                            { "id": "4", "name": "The Phantom Menace" },
                            { "id": "5", "name": "Attack of the Clones" },
                            { "id": "6", "name": "Revenge of the Sith" }
                          ],
                          "page": 1,
                          "size": 10,
                          "totalElements": 6,
                          "totalPages": 1
                        }
                        """, JsonCompareMode.STRICT);

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_FILMS))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("limit", equalTo("10")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-07.4: the list of films filtered by name=hope has the film whose title contains it, \"A New Hope\"")
    void listFilteredByAPartOfTheTitleHasTheMatches() {
        client.get().uri(FILMS + "?name=hope")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // The text is in lower case and the title is "A New Hope": the search does not
                // tell them apart.
                .expectBody().json("""
                        {
                          "content": [
                            { "id": "1", "name": "A New Hope" }
                          ],
                          "page": 1,
                          "size": 10,
                          "totalElements": 1,
                          "totalPages": 1
                        }
                        """, JsonCompareMode.STRICT);

        // The parameter of the API is "name" for every entity. SWAPI searches the films by
        // their title, and that is what it was asked: one search, and not by a name.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_FILMS)).withQueryParam("title", equalTo("hope")));
        swapi.verify(0, getRequestedFor(urlPathEqualTo(SWAPI_FILMS)).withQueryParam("name", matching(".*")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-06.6: the detail of film 1 has the fields of the table of Films, with its relations as lists of identifiers, in text")
    void detailOfAnExistingFilmHasItsCompleteData() {
        client.get().uri(FILMS + "/1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict: these ten fields and no other, and each list in the order of SWAPI.
                // - The episode is a number, the only value that is not text.
                // - The opening crawl keeps its line breaks (\r\n) as SWAPI sends them: only the
                //   spaces at the start and at the end of a text are removed.
                // - The planets and the species of the film, and the internal data of SWAPI
                //   (created, edited, url, description), are in the recording and must not be
                //   in the answer.
                .expectBody().json("""
                        {
                          "id": "1",
                          "title": "A New Hope",
                          "episodeId": 4,
                          "openingCrawl": "It is a period of civil war.\\r\\nRebel spaceships, striking\\r\\nfrom a hidden base, have won\\r\\ntheir first victory against\\r\\nthe evil Galactic Empire.\\r\\n\\r\\nDuring the battle, Rebel\\r\\nspies managed to steal secret\\r\\nplans to the Empire's\\r\\nultimate weapon, the DEATH\\r\\nSTAR, an armored space\\r\\nstation with enough power\\r\\nto destroy an entire planet.\\r\\n\\r\\nPursued by the Empire's\\r\\nsinister agents, Princess\\r\\nLeia races home aboard her\\r\\nstarship, custodian of the\\r\\nstolen plans that can save her\\r\\npeople and restore\\r\\nfreedom to the galaxy....",
                          "director": "George Lucas",
                          "producer": "Gary Kurtz, Rick McCallum",
                          "releaseDate": "1977-05-25",
                          "characterIds": ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "12", "13", "14", "15", "16", "18", "19", "81"],
                          "starshipIds": ["2", "3", "5", "9", "10", "11", "12", "13"],
                          "vehicleIds": ["4", "6", "7", "8"]
                        }
                        """, JsonCompareMode.STRICT);

        // The film was asked to SWAPI by its identifier. The list was not walked to find it.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_FILMS + "/1")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("CA-24.4: the second page of 2 films with expanded=true has the 3rd and the 4th with the fields of the table of Films, out of 6 films in 3 pages")
    void expandedListOfFilmsIsCutInPagesHere() {
        client.get().uri(FILMS + "?expanded=true&page=2&size=2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict: each element is the detail of the film, with its ten fields and no
                // other.
                // - It has "title" and no "name": the summary of a film calls its title "name",
                //   and its detail does not.
                // - The episode is a number, the only value that is not text.
                // - The planets and the species of each film, and the internal data of SWAPI,
                //   are in the recording and must not be in the answer.
                .expectBody().json("""
                        {
                          "content": [
                            {
                              "id": "3",
                              "title": "Return of the Jedi",
                              "episodeId": 6,
                              "openingCrawl": "Luke Skywalker has returned to\\r\\nhis home planet of Tatooine in\\r\\nan attempt to rescue his\\r\\nfriend Han Solo from the\\r\\nclutches of the vile gangster\\r\\nJabba the Hutt.\\r\\n\\r\\nLittle does Luke know that the\\r\\nGALACTIC EMPIRE has secretly\\r\\nbegun construction on a new\\r\\narmored space station even\\r\\nmore powerful than the first\\r\\ndreaded Death Star.\\r\\n\\r\\nWhen completed, this ultimate\\r\\nweapon will spell certain doom\\r\\nfor the small band of rebels\\r\\nstruggling to restore freedom\\r\\nto the galaxy...",
                              "director": "Richard Marquand",
                              "producer": "Howard G. Kazanjian, George Lucas, Rick McCallum",
                              "releaseDate": "1983-05-25",
                              "characterIds": ["1", "2", "3", "4", "5", "10", "13", "14", "16", "18", "20", "21", "22", "25", "27", "28", "29", "30", "31", "45"],
                              "starshipIds": ["2", "3", "10", "11", "12", "15", "17", "22", "23", "27", "28", "29"],
                              "vehicleIds": ["8", "16", "18", "19", "24", "25", "26", "30"]
                            },
                            {
                              "id": "4",
                              "title": "The Phantom Menace",
                              "episodeId": 1,
                              "openingCrawl": "Turmoil has engulfed the\\r\\nGalactic Republic. The taxation\\r\\nof trade routes to outlying star\\r\\nsystems is in dispute.\\r\\n\\r\\nHoping to resolve the matter\\r\\nwith a blockade of deadly\\r\\nbattleships, the greedy Trade\\r\\nFederation has stopped all\\r\\nshipping to the small planet\\r\\nof Naboo.\\r\\n\\r\\nWhile the Congress of the\\r\\nRepublic endlessly debates\\r\\nthis alarming chain of events,\\r\\nthe Supreme Chancellor has\\r\\nsecretly dispatched two Jedi\\r\\nKnights, the guardians of\\r\\npeace and justice in the\\r\\ngalaxy, to settle the conflict....",
                              "director": "George Lucas",
                              "producer": "Rick McCallum",
                              "releaseDate": "1999-05-19",
                              "characterIds": ["2", "3", "10", "11", "16", "20", "21", "32", "33", "34", "35", "36", "37", "38", "39", "40", "41", "42", "43", "44", "46", "47", "48", "49", "50", "51", "52", "53", "54", "55", "56", "57", "58", "59"],
                              "starshipIds": ["31", "32", "39", "40", "41"],
                              "vehicleIds": ["33", "34", "35", "36", "37", "38", "42"]
                            }
                          ],
                          "page": 2,
                          "size": 2,
                          "totalElements": 6,
                          "totalPages": 3
                        }
                        """, JsonCompareMode.STRICT);

        // SWAPI was asked for that page and that size with expanded=true, like for any entity.
        // It ignores the three in the list of films and answers the six of them, as it does
        // without the parameter: the page was cut out here, and the totals were counted here.
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_FILMS))
                .withQueryParam("page", equalTo("2"))
                .withQueryParam("limit", equalTo("2"))
                .withQueryParam("expanded", equalTo("true")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

}
