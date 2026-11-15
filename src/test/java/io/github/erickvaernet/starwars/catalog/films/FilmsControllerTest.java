package io.github.erickvaernet.starwars.catalog.films;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.JsonPath;
import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.CatalogService;
import io.github.erickvaernet.starwars.catalog.ItemId;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.ListQuery;
import io.github.erickvaernet.starwars.catalog.NameFilter;
import io.github.erickvaernet.starwars.catalog.PageQuery;
import io.github.erickvaernet.starwars.catalog.PageResult;
import io.github.erickvaernet.starwars.shared.NotFoundException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Checks the requests about films, the list and the detail: what each one passes to the core,
 * how it answers and how it is documented.
 *
 * <p>Only the web layer is loaded, with the real error format and the generated API
 * documentation; the core is replaced. The access rules are left out, because they have their
 * own tests. It needs neither the database, nor SWAPI, nor Docker.
 *
 * <p>The parameters of a list and their rules are the same for every entity and are checked one
 * by one in the tests of people. Here there is one case of each, to show that films take them
 * from the same place, and everything that is particular to films.
 */
@WebMvcTest(FilmsController.class)
@AutoConfigureMockMvc(addFilters = false)
// The API documentation is not part of what a test of the web layer loads by default.
@ImportAutoConfiguration({
    SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
class FilmsControllerTest {

    private static final String FILMS = "/api/v1/films";

    /** In SWAPI the entity is called films, and it is searched by its title. */
    private static final CatalogEntity FILMS_ENTITY = new CatalogEntity("films", "title");

    private static final PageResult<ItemSummary> A_PAGE = new PageResult<>(
            List.of(new ItemSummary("3", "Return of the Jedi"), new ItemSummary("4", "The Phantom Menace")), 2, 2, 6, 3);

    private static final String ID_RULE = "Parameter 'id' must be a number of 1 to 10 digits, without leading zeros.";

    private static final FilmDetail A_NEW_HOPE = new FilmDetail(
            "1", "A New Hope", 4, "It is a period of civil war.\r\nRebel spaceships, striking\r\nfrom a hidden base...",
            "George Lucas", "Gary Kurtz, Rick McCallum", "1977-05-25",
            List.of("1", "2", "81"), List.of("2", "13"), List.of("4", "8"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService catalog;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc).build();
    }

    @Test
    @DisplayName("The page is answered as JSON with exactly the fields of the contract, and each film with its id and its name")
    void pageIsAnsweredWithTheFieldsOfTheContract() {
        when(catalog.<ItemSummary>list(any(), any())).thenReturn(A_PAGE);

        client.get().uri(FILMS + "?page=2&size=2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
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
    }

    @Test
    @DisplayName("Without parameters, the core is asked for the first page of 10 films, by the name and the search field of films")
    void withoutParametersTheFirstPageOfTenFilmsIsAskedFor() {
        when(catalog.<ItemSummary>list(any(), any())).thenReturn(A_PAGE);

        client.get().uri(FILMS).exchange().expectStatus().isOk();

        verify(catalog).list(FILMS_ENTITY, new ListQuery(new PageQuery(1, 10), null, null, false));
    }

    @Test
    @DisplayName("The page, the size, the id and the name of the request are passed to the core, the name in lower case")
    void parametersArePassedToTheCore() {
        when(catalog.<ItemSummary>list(any(), any())).thenReturn(A_PAGE);

        client.get().uri(FILMS + "?page=3&size=2").exchange().expectStatus().isOk();
        client.get().uri(FILMS + "?id=1").exchange().expectStatus().isOk();
        client.get().uri(FILMS + "?name={name}", " HOPE ").exchange().expectStatus().isOk();
        client.get().uri(FILMS + "?id=1&name=jedi").exchange().expectStatus().isOk();

        verify(catalog).list(FILMS_ENTITY, new ListQuery(new PageQuery(3, 2), null, null, false));
        verify(catalog).list(FILMS_ENTITY, new ListQuery(new PageQuery(1, 10), new ItemId("1"), null, false));
        verify(catalog).list(FILMS_ENTITY, new ListQuery(new PageQuery(1, 10), null, new NameFilter("hope"), false));
        verify(catalog).list(FILMS_ENTITY,
                new ListQuery(new PageQuery(1, 10), new ItemId("1"), new NameFilter("jedi"), false));
    }

    @ParameterizedTest(name = "?{0} is rejected for {1}")
    @CsvSource({
        "page=0, page",
        "size=101, size",
        "id=abc, id",
        "name=, name",
        "expanded=TRUE, expanded"
    })
    @DisplayName("An invalid parameter of the list is answered with 400 naming it, and the core is not asked")
    void invalidParameterOfTheListIsRejected(String query, String parameter) {
        client.get().uri(FILMS + "?" + query)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Bad Request")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.instance").isEqualTo(FILMS)
                .jsonPath("$.detail").value(String.class,
                        detail -> assertThat(detail).startsWith("Parameter '" + parameter + "' "));

        verifyNoInteractions(catalog);
    }

    @Test
    @DisplayName("The documentation shows the five parameters of the list, its answer and every error")
    void documentationShowsTheList() {
        String documentation = apiDocumentation();
        List<String> parameters = JsonPath.read(documentation, "$.paths['/api/v1/films'].get.parameters[*].name");
        Map<String, Object> responses = JsonPath.read(documentation, "$.paths['/api/v1/films'].get.responses");
        String summary = JsonPath.read(documentation, "$.paths['/api/v1/films'].get.summary");
        String description = JsonPath.read(documentation, "$.paths['/api/v1/films'].get.description");

        assertThat(parameters).containsExactlyInAnyOrder("page", "size", "id", "name", "expanded");
        assertThat(responses).containsOnlyKeys("200", "400", "401", "502", "504");
        assertThat(summary).isNotBlank();
        // Who reads the documentation has to find out that the name of a film is its title.
        assertThat(description).contains("title");
        // The errors are Problem Details.
        for (String error : List.of("400", "401", "502", "504")) {
            Map<String, Object> content = JsonPath.read(documentation,
                    "$.paths['/api/v1/films'].get.responses['" + error + "'].content");
            assertThat(content).containsOnlyKeys("application/problem+json");
        }
    }

    @Test
    @DisplayName("The expanded of the request is passed to the core, next to the page, the size and the filters")
    void expandedIsPassedToTheCore() {
        when(catalog.<ItemSummary>list(any(), any())).thenReturn(A_PAGE);

        client.get().uri(FILMS + "?expanded=true").exchange().expectStatus().isOk();
        client.get().uri(FILMS + "?expanded=false&page=2&size=2").exchange().expectStatus().isOk();

        verify(catalog).list(FILMS_ENTITY, new ListQuery(new PageQuery(1, 10), null, null, true));
        verify(catalog).list(FILMS_ENTITY, new ListQuery(new PageQuery(2, 2), null, null, false));
    }

    @Test
    @DisplayName("The documentation shows an element of the list as one of two: the summary or the detail of a film")
    void documentationShowsAnElementOfTheListAsOneOfTwo() {
        String documentation = apiDocumentation();
        String page = JsonPath.read(documentation,
                "$.paths['/api/v1/films'].get.responses['200'].content['application/json'].schema['$ref']");
        String element = JsonPath.read(documentation,
                "$.components.schemas['" + page.replace("#/components/schemas/", "") + "'].properties.content.items['$ref']");
        Map<String, Object> oneOfTwo = JsonPath.read(documentation,
                "$.components.schemas['" + element.replace("#/components/schemas/", "") + "']");

        assertThat(oneOfTwo.get("oneOf")).isEqualTo(List.of(
                Map.of("$ref", "#/components/schemas/ItemSummary"),
                Map.of("$ref", "#/components/schemas/FilmDetail")));
        // It says when each one arrives, which the two names alone do not.
        assertThat((String) oneOfTwo.get("description")).contains("expanded");
    }

    @Test
    @DisplayName("The detail is answered as JSON with exactly the fields of the table of Films, the episode as a number")
    void detailIsAnsweredWithTheFieldsOfTheContract() {
        when(catalog.detail(any(), any(), eq(FilmDetail.class))).thenReturn(A_NEW_HOPE);

        client.get().uri(FILMS + "/1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("""
                        {
                          "id": "1",
                          "title": "A New Hope",
                          "episodeId": 4,
                          "openingCrawl": "It is a period of civil war.\\r\\nRebel spaceships, striking\\r\\nfrom a hidden base...",
                          "director": "George Lucas",
                          "producer": "Gary Kurtz, Rick McCallum",
                          "releaseDate": "1977-05-25",
                          "characterIds": ["1", "2", "81"],
                          "starshipIds": ["2", "13"],
                          "vehicleIds": ["4", "8"]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("A value that the source does not have is answered as null, the episode too, and a relation without elements as an empty list")
    void missingValueIsAnsweredAsNull() {
        when(catalog.detail(any(), any(), eq(FilmDetail.class))).thenReturn(new FilmDetail(
                "1", "A New Hope", null, null, null, null, null, List.of(), List.of(), List.of()));

        client.get().uri(FILMS + "/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                          "id": "1",
                          "title": "A New Hope",
                          "episodeId": null,
                          "openingCrawl": null,
                          "director": null,
                          "producer": null,
                          "releaseDate": null,
                          "characterIds": [],
                          "starshipIds": [],
                          "vehicleIds": []
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("The core is asked for the detail of that film, by the identifier of the route")
    void identifierOfTheRouteIsPassedToTheCore() {
        when(catalog.detail(any(), any(), eq(FilmDetail.class))).thenReturn(A_NEW_HOPE);

        client.get().uri(FILMS + "/6").exchange().expectStatus().isOk();

        verify(catalog).detail(FILMS_ENTITY, new ItemId("6"), FilmDetail.class);
    }

    @Test
    @DisplayName("The detail of a film that does not exist is answered with 404")
    void detailOfAFilmThatDoesNotExistIsNotFound() {
        when(catalog.detail(any(), any(), eq(FilmDetail.class))).thenThrow(new NotFoundException());

        client.get().uri(FILMS + "/9999")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Not Found")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").isEqualTo("The requested resource was not found.")
                .jsonPath("$.instance").isEqualTo(FILMS + "/9999");
    }

    @ParameterizedTest(name = "GET /api/v1/films/{0}")
    @ValueSource(strings = {"abc", "0", "01", "12345678901"})
    @DisplayName("An invalid identifier in the route is answered with 400 naming id and its rule, and the core is not asked")
    void invalidIdentifierInTheRouteIsRejected(String id) {
        client.get().uri(URI.create(FILMS + "/" + id))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Bad Request")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.detail").isEqualTo(ID_RULE);

        verifyNoInteractions(catalog);
    }

    @Test
    @DisplayName("The documentation shows the detail: the identifier of the route, the ten fields of a film and every error")
    void documentationShowsTheDetail() {
        String documentation = apiDocumentation();
        List<Map<String, Object>> parameters = JsonPath.read(documentation,
                "$.paths['/api/v1/films/{id}'].get.parameters");
        Map<String, Object> responses = JsonPath.read(documentation, "$.paths['/api/v1/films/{id}'].get.responses");

        assertThat(parameters).hasSize(1);
        Map<String, Object> id = parameters.getFirst();
        assertThat(id).containsEntry("name", "id").containsEntry("in", "path").containsEntry("required", true);
        assertThat(schemaOf(id)).containsEntry("type", "string").containsEntry("pattern", "^[1-9][0-9]{0,9}$");
        assertThat((String) id.get("description")).isNotBlank();

        assertThat(responses).containsOnlyKeys("200", "400", "401", "404", "502", "504");
        String schema = JsonPath.read(documentation,
                "$.paths['/api/v1/films/{id}'].get.responses['200'].content['application/json'].schema['$ref']");
        assertThat(schema).isEqualTo("#/components/schemas/FilmDetail");
        // The ten fields of the table of Films and nothing else: the title, and no name.
        Map<String, Map<String, Object>> fields = JsonPath.read(documentation,
                "$.components.schemas['FilmDetail'].properties");
        assertThat(fields).containsOnlyKeys(
                "id", "title", "episodeId", "openingCrawl", "director", "producer", "releaseDate",
                "characterIds", "starshipIds", "vehicleIds");
        assertThat(fields.get("id")).containsEntry("type", "string");
        assertThat(fields.get("title")).containsEntry("type", "string");
        // The only value that is not text.
        assertThat(fields.get("episodeId")).containsEntry("type", "integer");
        assertThat(fields.get("releaseDate")).containsEntry("type", "string");
        assertThat(fields.get("characterIds")).containsEntry("type", "array");
        // The errors are Problem Details.
        for (String error : List.of("400", "401", "404", "502", "504")) {
            Map<String, Object> content = JsonPath.read(documentation,
                    "$.paths['/api/v1/films/{id}'].get.responses['" + error + "'].content");
            assertThat(content).containsOnlyKeys("application/problem+json");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaOf(Map<String, Object> parameter) {
        return (Map<String, Object>) parameter.get("schema");
    }

    private String apiDocumentation() {
        return client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

}
