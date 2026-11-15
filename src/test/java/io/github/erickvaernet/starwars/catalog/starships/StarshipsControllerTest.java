package io.github.erickvaernet.starwars.catalog.starships;

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
import java.util.stream.Stream;
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
 * Checks the requests about starships, the list and the detail: what each one passes to the
 * core, how it answers and how it is documented.
 *
 * <p>Only the web layer is loaded, with the real error format and the generated API
 * documentation; the core is replaced. The access rules are left out, because they have their
 * own tests. It needs neither the database, nor SWAPI, nor Docker.
 *
 * <p>The parameters of a list and their rules are the same for every entity and are checked one
 * by one in the tests of people. Here there is one case of each, to show that starships take
 * them from the same place, and everything that is particular to starships.
 */
@WebMvcTest(StarshipsController.class)
@AutoConfigureMockMvc(addFilters = false)
// The API documentation is not part of what a test of the web layer loads by default.
@ImportAutoConfiguration({
    SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
class StarshipsControllerTest {

    private static final String STARSHIPS = "/api/v1/starships";

    /** In SWAPI the entity is called starships, and it is searched by its name. */
    private static final CatalogEntity STARSHIPS_ENTITY = new CatalogEntity("starships", "name");

    private static final PageResult<ItemSummary> A_PAGE = new PageResult<>(
            List.of(new ItemSummary("2", "CR90 corvette"), new ItemSummary("3", "Star Destroyer")), 1, 2, 36, 18);

    private static final String ID_RULE = "Parameter 'id' must be a number of 1 to 10 digits, without leading zeros.";

    /** A different value in every field, so that two that are answered in each other's place show. */
    private static final StarshipDetail X_WING = new StarshipDetail(
            "12", "X-wing", "T-65 X-wing", "Starfighter", "Incom Corporation", "149999", "12.5", "1", "0",
            "1050", "1.0", "100", "110", "1 week", List.of("1", "2", "3"), List.of("1", "9", "18"));

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
    @DisplayName("The page is answered as JSON with exactly the fields of the contract, and each starship with its id and its name")
    void pageIsAnsweredWithTheFieldsOfTheContract() {
        when(catalog.list(any(), any())).thenReturn(A_PAGE);

        client.get().uri(STARSHIPS + "?page=1&size=2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("""
                        {
                          "content": [
                            { "id": "2", "name": "CR90 corvette" },
                            { "id": "3", "name": "Star Destroyer" }
                          ],
                          "page": 1,
                          "size": 2,
                          "totalElements": 36,
                          "totalPages": 18
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("Without parameters, the core is asked for the first page of 10 starships, by the name and the search field of starships")
    void withoutParametersTheFirstPageOfTenStarshipsIsAskedFor() {
        when(catalog.list(any(), any())).thenReturn(A_PAGE);

        client.get().uri(STARSHIPS).exchange().expectStatus().isOk();

        verify(catalog).list(STARSHIPS_ENTITY, new ListQuery(new PageQuery(1, 10), null, null));
    }

    @Test
    @DisplayName("The page, the size, the id and the name of the request are passed to the core, the name in lower case")
    void parametersArePassedToTheCore() {
        when(catalog.list(any(), any())).thenReturn(A_PAGE);

        client.get().uri(STARSHIPS + "?page=8&size=5").exchange().expectStatus().isOk();
        client.get().uri(STARSHIPS + "?id=9").exchange().expectStatus().isOk();
        client.get().uri(STARSHIPS + "?name={name}", " WING ").exchange().expectStatus().isOk();
        client.get().uri(STARSHIPS + "?id=9&name=death").exchange().expectStatus().isOk();

        verify(catalog).list(STARSHIPS_ENTITY, new ListQuery(new PageQuery(8, 5), null, null));
        verify(catalog).list(STARSHIPS_ENTITY, new ListQuery(new PageQuery(1, 10), new ItemId("9"), null));
        verify(catalog).list(STARSHIPS_ENTITY, new ListQuery(new PageQuery(1, 10), null, new NameFilter("wing")));
        verify(catalog).list(STARSHIPS_ENTITY,
                new ListQuery(new PageQuery(1, 10), new ItemId("9"), new NameFilter("death")));
    }

    @ParameterizedTest(name = "?{0} is rejected for {1}")
    @CsvSource({
        "page=0, page",
        "size=101, size",
        "id=abc, id",
        "name=, name"
    })
    @DisplayName("An invalid parameter of the list is answered with 400 naming it, and the core is not asked")
    void invalidParameterOfTheListIsRejected(String query, String parameter) {
        client.get().uri(STARSHIPS + "?" + query)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Bad Request")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.instance").isEqualTo(STARSHIPS)
                .jsonPath("$.detail").value(String.class,
                        detail -> assertThat(detail).startsWith("Parameter '" + parameter + "' "));

        verifyNoInteractions(catalog);
    }

    @Test
    @DisplayName("The documentation shows the four parameters of the list, its answer and every error")
    void documentationShowsTheList() {
        String documentation = apiDocumentation();
        List<String> parameters = JsonPath.read(documentation, "$.paths['/api/v1/starships'].get.parameters[*].name");
        Map<String, Object> responses = JsonPath.read(documentation, "$.paths['/api/v1/starships'].get.responses");
        String summary = JsonPath.read(documentation, "$.paths['/api/v1/starships'].get.summary");
        String description = JsonPath.read(documentation, "$.paths['/api/v1/starships'].get.description");
        List<String> tags = JsonPath.read(documentation, "$.paths['/api/v1/starships'].get.tags");

        assertThat(parameters).containsExactlyInAnyOrder("page", "size", "id", "name");
        assertThat(responses).containsOnlyKeys("200", "400", "401", "502", "504");
        assertThat(summary).isNotBlank();
        assertThat(description).isNotBlank();
        assertThat(tags).containsExactly("Starships");
        // The errors are Problem Details.
        for (String error : List.of("400", "401", "502", "504")) {
            Map<String, Object> content = JsonPath.read(documentation,
                    "$.paths['/api/v1/starships'].get.responses['" + error + "'].content");
            assertThat(content).containsOnlyKeys("application/problem+json");
        }
    }

    @Test
    @DisplayName("The detail is answered as JSON with exactly the fields of the table of Starships, every value as text")
    void detailIsAnsweredWithTheFieldsOfTheContract() {
        when(catalog.detail(any(), any(), eq(StarshipDetail.class))).thenReturn(X_WING);

        client.get().uri(STARSHIPS + "/12")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // Strict: a number written as text ("1") is not the same as the number 1.
                .expectBody().json("""
                        {
                          "id": "12",
                          "name": "X-wing",
                          "model": "T-65 X-wing",
                          "starshipClass": "Starfighter",
                          "manufacturer": "Incom Corporation",
                          "costInCredits": "149999",
                          "length": "12.5",
                          "crew": "1",
                          "passengers": "0",
                          "maxAtmospheringSpeed": "1050",
                          "hyperdriveRating": "1.0",
                          "mglt": "100",
                          "cargoCapacity": "110",
                          "consumables": "1 week",
                          "filmIds": ["1", "2", "3"],
                          "pilotIds": ["1", "9", "18"]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("The fields of the detail are answered in the order of the table of Starships")
    void fieldsOfTheDetailAreInTheOrderOfTheTable() {
        when(catalog.detail(any(), any(), eq(StarshipDetail.class))).thenReturn(X_WING);

        String body = client.get().uri(STARSHIPS + "/12")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();

        // The comparison of the JSON does not look at the order of the fields; this one does.
        Map<String, Object> fields = JsonPath.read(body, "$");
        assertThat(fields.keySet()).containsExactly(
                "id", "name", "model", "starshipClass", "manufacturer", "costInCredits", "length", "crew",
                "passengers", "maxAtmospheringSpeed", "hyperdriveRating", "mglt", "cargoCapacity", "consumables",
                "filmIds", "pilotIds");
    }

    @Test
    @DisplayName("A value that the source does not have is answered as null, and a relation without elements as an empty list")
    void missingValueIsAnsweredAsNull() {
        when(catalog.detail(any(), any(), eq(StarshipDetail.class))).thenReturn(new StarshipDetail(
                "9", "Death Star", null, null, null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of()));

        client.get().uri(STARSHIPS + "/9")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                          "id": "9",
                          "name": "Death Star",
                          "model": null,
                          "starshipClass": null,
                          "manufacturer": null,
                          "costInCredits": null,
                          "length": null,
                          "crew": null,
                          "passengers": null,
                          "maxAtmospheringSpeed": null,
                          "hyperdriveRating": null,
                          "mglt": null,
                          "cargoCapacity": null,
                          "consumables": null,
                          "filmIds": [],
                          "pilotIds": []
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("The core is asked for the detail of that starship, by the identifier of the route")
    void identifierOfTheRouteIsPassedToTheCore() {
        when(catalog.detail(any(), any(), eq(StarshipDetail.class))).thenReturn(X_WING);

        client.get().uri(STARSHIPS + "/12").exchange().expectStatus().isOk();

        verify(catalog).detail(STARSHIPS_ENTITY, new ItemId("12"), StarshipDetail.class);
    }

    @Test
    @DisplayName("The detail of a starship that does not exist is answered with 404")
    void detailOfAStarshipThatDoesNotExistIsNotFound() {
        when(catalog.detail(any(), any(), eq(StarshipDetail.class))).thenThrow(new NotFoundException());

        client.get().uri(STARSHIPS + "/9999")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Not Found")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").isEqualTo("The requested resource was not found.")
                .jsonPath("$.instance").isEqualTo(STARSHIPS + "/9999");
    }

    @ParameterizedTest(name = "GET /api/v1/starships/{0}")
    @ValueSource(strings = {"abc", "0", "01", "12345678901"})
    @DisplayName("An invalid identifier in the route is answered with 400 naming id and its rule, and the core is not asked")
    void invalidIdentifierInTheRouteIsRejected(String id) {
        client.get().uri(URI.create(STARSHIPS + "/" + id))
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
    @DisplayName("The documentation shows the detail: the identifier of the route, the sixteen fields of a starship and every error")
    void documentationShowsTheDetail() {
        String documentation = apiDocumentation();
        List<Map<String, Object>> parameters = JsonPath.read(documentation,
                "$.paths['/api/v1/starships/{id}'].get.parameters");
        Map<String, Object> responses = JsonPath.read(documentation,
                "$.paths['/api/v1/starships/{id}'].get.responses");

        assertThat(parameters).hasSize(1);
        Map<String, Object> id = parameters.getFirst();
        assertThat(id).containsEntry("name", "id").containsEntry("in", "path").containsEntry("required", true);
        assertThat(schemaOf(id)).containsEntry("type", "string").containsEntry("pattern", "^[1-9][0-9]{0,9}$");
        assertThat((String) id.get("description")).isNotBlank();

        assertThat(responses).containsOnlyKeys("200", "400", "401", "404", "502", "504");
        String schema = JsonPath.read(documentation,
                "$.paths['/api/v1/starships/{id}'].get.responses['200'].content['application/json'].schema['$ref']");
        assertThat(schema).isEqualTo("#/components/schemas/StarshipDetail");
        // The sixteen fields of the table of Starships and nothing else.
        Map<String, Map<String, Object>> fields = JsonPath.read(documentation,
                "$.components.schemas['StarshipDetail'].properties");
        List<String> texts = List.of(
                "id", "name", "model", "starshipClass", "manufacturer", "costInCredits", "length", "crew",
                "passengers", "maxAtmospheringSpeed", "hyperdriveRating", "mglt", "cargoCapacity", "consumables");
        List<String> relations = List.of("filmIds", "pilotIds");
        assertThat(fields.keySet()).containsExactlyInAnyOrderElementsOf(
                Stream.concat(texts.stream(), relations.stream()).toList());
        // Every value is text, the quantities too: no field is a number.
        assertThat(texts).allSatisfy(field -> {
            assertThat(fields.get(field)).containsEntry("type", "string");
            assertThat((String) fields.get(field).get("description")).isNotBlank();
        });
        assertThat(relations).allSatisfy(field -> {
            assertThat(fields.get(field)).containsEntry("type", "array");
            assertThat((String) fields.get(field).get("description")).isNotBlank();
        });
        // The errors are Problem Details.
        for (String error : List.of("400", "401", "404", "502", "504")) {
            Map<String, Object> content = JsonPath.read(documentation,
                    "$.paths['/api/v1/starships/{id}'].get.responses['" + error + "'].content");
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
