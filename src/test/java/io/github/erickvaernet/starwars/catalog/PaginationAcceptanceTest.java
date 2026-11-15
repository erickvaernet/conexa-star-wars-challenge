package io.github.erickvaernet.starwars.catalog;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-05: every list is asked for and answered in the same way.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and looks for SWAPI at
 * the simulated one, which answers with real recorded responses. Every request carries the token
 * of the test user.
 *
 * <p>The criteria about "any of the four entities" are one test with one case per entity. Each
 * entity adds its case when its list arrives.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class PaginationAcceptanceTest {

    private static final String PEOPLE = "/api/v1/people";

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = TestSession.open(RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build());
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/api/v1/people",
        "/api/v1/films",
        "/api/v1/starships"
    })
    @DisplayName("CA-05.1: a list without parameters has exactly content, page, size, totalElements and totalPages")
    void listHasExactlyTheFieldsOfAPage(String path) {
        Map<String, Object> page = JsonPath.read(getOk(path), "$");

        assertThat(page).containsOnlyKeys("content", "page", "size", "totalElements", "totalPages");
        assertThat(page.get("content")).isInstanceOf(List.class);
        assertThat(page.get("page")).isInstanceOf(Integer.class);
        assertThat(page.get("size")).isInstanceOf(Integer.class);
        assertThat(page.get("totalElements")).isInstanceOf(Integer.class);
        assertThat(page.get("totalPages")).isInstanceOf(Integer.class);
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/api/v1/people",
        "/api/v1/films",
        "/api/v1/starships"
    })
    @DisplayName("CA-05.2: every element of a list without parameters has exactly id and name")
    void everyElementHasExactlyIdAndName(String path) {
        List<Map<String, Object>> content = JsonPath.read(getOk(path), "$.content");

        assertThat(content).isNotEmpty();
        assertThat(content).allSatisfy(element -> {
            assertThat(element).containsOnlyKeys("id", "name");
            // The identifier is text, the way SWAPI gives it.
            assertThat(element.get("id")).isInstanceOf(String.class);
            assertThat(element.get("name")).isInstanceOf(String.class);
        });
    }

    @Test
    @DisplayName("CA-05.3: a page after the last one is empty and keeps the right totals")
    void pageAfterTheLastOneIsEmptyWithTheRightTotals() {
        // SWAPI does not answer a page after the last one with an empty list: the recording
        // shows that it repeats its last page. The application must not pass those elements on.
        client.get().uri(PEOPLE + "?page=50")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.page").isEqualTo(50)
                .jsonPath("$.size").isEqualTo(10)
                .jsonPath("$.totalElements").isEqualTo(82)
                .jsonPath("$.totalPages").isEqualTo(9);
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
        "page, 0",
        "page, -1",
        "page, abc",
        "size, 0",
        "size, 101",
        "size, abc"
    })
    @DisplayName("CA-05.4: an invalid page or size is rejected with 400, and the detail names the parameter")
    void invalidPageOrSizeIsRejected(String parameter, String value) {
        String other = parameter.equals("page") ? "size" : "page";

        client.get().uri(PEOPLE + "?" + parameter + "=" + value)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Bad Request")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.instance").isEqualTo(PEOPLE)
                // It names the parameter that is wrong, and only that one.
                .jsonPath("$.detail").value(String.class,
                        detail -> assertThat(detail).contains(parameter).doesNotContain(other));
    }

    @Test
    @DisplayName("CA-05.5: the smallest and the largest page sizes are accepted: 1 and 100")
    void smallestAndLargestPageSizesAreAccepted() {
        client.get().uri(PEOPLE + "?size=1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.page").isEqualTo(1)
                .jsonPath("$.size").isEqualTo(1)
                .jsonPath("$.totalElements").isEqualTo(82)
                .jsonPath("$.totalPages").isEqualTo(82);

        client.get().uri(PEOPLE + "?size=100")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(82)
                .jsonPath("$.page").isEqualTo(1)
                .jsonPath("$.size").isEqualTo(100)
                .jsonPath("$.totalElements").isEqualTo(82)
                .jsonPath("$.totalPages").isEqualTo(1);
    }

    /** Asks for a list, checks that the answer is a 200 and returns its body. */
    private String getOk(String path) {
        return client.get().uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

}
