package io.github.erickvaernet.starwars.catalog.people;

import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-01: listing people one page at a time.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and looks for SWAPI at
 * the simulated one, which answers with real recorded responses. A recording answers one page
 * and one page size only, so each test also shows that the application asked SWAPI for the page
 * and the size it was asked for.
 *
 * <p>Every request carries the token of the test user.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class PeopleListAcceptanceTest {

    private static final String PEOPLE = "/api/v1/people";

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = TestSession.open(RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build());
    }

    @Test
    @DisplayName("CA-01.1: the list of people without parameters is the first page of 10, out of 82 people in 9 pages")
    void listWithoutParametersIsTheFirstPageOfTen() {
        client.get().uri(PEOPLE)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.page").isEqualTo(1)
                .jsonPath("$.size").isEqualTo(10)
                .jsonPath("$.totalElements").isEqualTo(82)
                .jsonPath("$.totalPages").isEqualTo(9)
                .jsonPath("$.content.length()").isEqualTo(10)
                // In the order SWAPI gives them.
                .jsonPath("$.content[0].id").isEqualTo("1")
                .jsonPath("$.content[0].name").isEqualTo("Luke Skywalker")
                .jsonPath("$.content[9].id").isEqualTo("10")
                .jsonPath("$.content[9].name").isEqualTo("Obi-Wan Kenobi");
    }

    @Test
    @DisplayName("CA-01.2: the ninth page of 10 has the last 2 people")
    void lastPageHasTheRemainingPeople() {
        client.get().uri(PEOPLE + "?page=9&size=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page").isEqualTo(9)
                .jsonPath("$.size").isEqualTo(10)
                .jsonPath("$.totalElements").isEqualTo(82)
                .jsonPath("$.totalPages").isEqualTo(9)
                .jsonPath("$.content.length()").isEqualTo(2)
                // The identifiers of SWAPI have gaps: the last of the 82 people is number 83.
                .jsonPath("$.content[0].id").isEqualTo("82")
                .jsonPath("$.content[0].name").isEqualTo("Sly Moore")
                .jsonPath("$.content[1].id").isEqualTo("83")
                .jsonPath("$.content[1].name").isEqualTo("Tion Medon");
    }

}
