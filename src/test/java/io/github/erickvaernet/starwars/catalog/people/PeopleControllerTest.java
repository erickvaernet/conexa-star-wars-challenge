package io.github.erickvaernet.starwars.catalog.people;

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
 * Checks the requests about people, the list and the detail: what each one passes to the core,
 * how it answers and how it is documented.
 *
 * <p>Only the web layer is loaded, with the real error format and the generated API
 * documentation; the core is replaced. The access rules are left out, because they have their
 * own tests. It needs neither the database, nor SWAPI, nor Docker.
 */
@WebMvcTest(PeopleController.class)
@AutoConfigureMockMvc(addFilters = false)
// The API documentation is not part of what a test of the web layer loads by default.
@ImportAutoConfiguration({
    SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
class PeopleControllerTest {

    private static final String PEOPLE = "/api/v1/people";

    private static final CatalogEntity PEOPLE_ENTITY = new CatalogEntity("people", "name");

    private static final PageResult<ItemSummary> A_PAGE = new PageResult<>(
            List.of(new ItemSummary("1", "Luke Skywalker"), new ItemSummary("2", "C-3PO")), 1, 2, 82, 41);

    private static final String ID_RULE = "Parameter 'id' must be a number of 1 to 10 digits, without leading zeros.";

    private static final PersonDetail LUKE = new PersonDetail(
            "1", "Luke Skywalker", "19BBY", "male", "172", "77", "blond", "fair", "blue",
            List.of("1", "2", "3", "6"), List.of("12", "22"), List.of("14", "30"));

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
    @DisplayName("The page is answered as JSON with exactly the fields of the contract")
    void pageIsAnsweredWithTheFieldsOfTheContract() {
        when(catalog.list(any(), any())).thenReturn(A_PAGE);

        client.get().uri(PEOPLE + "?page=1&size=2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("""
                        {
                          "content": [
                            { "id": "1", "name": "Luke Skywalker" },
                            { "id": "2", "name": "C-3PO" }
                          ],
                          "page": 1,
                          "size": 2,
                          "totalElements": 82,
                          "totalPages": 41
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("Without parameters, the core is asked for the first page of 10 people")
    void withoutParametersTheFirstPageOfTenIsAskedFor() {
        when(catalog.list(any(), any())).thenReturn(A_PAGE);

        client.get().uri(PEOPLE).exchange().expectStatus().isOk();

        verify(catalog).list(PEOPLE_ENTITY, new ListQuery(new PageQuery(1, 10), null));
    }

    @Test
    @DisplayName("The page and the size of the request are passed to the core")
    void pageAndSizeArePassedToTheCore() {
        when(catalog.list(any(), any())).thenReturn(A_PAGE);

        client.get().uri(PEOPLE + "?page=9&size=100").exchange().expectStatus().isOk();

        verify(catalog).list(PEOPLE_ENTITY, new ListQuery(new PageQuery(9, 100), null));
    }

    @Test
    @DisplayName("A parameter that is not in the specification is ignored")
    void unknownParameterIsIgnored() {
        when(catalog.list(any(), any())).thenReturn(A_PAGE);

        client.get().uri(PEOPLE + "?size=5&sort=name&limit=50&unknown=").exchange().expectStatus().isOk();

        verify(catalog).list(PEOPLE_ENTITY, new ListQuery(new PageQuery(1, 5), null));
    }

    @ParameterizedTest(name = "?{0} is rejected for {1}")
    @CsvSource({
        "page=0, page",
        "page=abc, page",
        "page=, page",
        "page=1&page=2, page",
        "page=99999999999, page",
        "size=0, size",
        "size=101, size",
        "size=abc, size",
        "size=, size",
        "size=10&size=20, size",
        "page=2&size=1000, size"
    })
    @DisplayName("An invalid page or size is answered with 400 naming the parameter, and the core is not asked")
    void invalidPageOrSizeIsRejected(String query, String parameter) {
        client.get().uri(PEOPLE + "?" + query)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Bad Request")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.instance").isEqualTo(PEOPLE)
                .jsonPath("$.detail").value(String.class,
                        detail -> assertThat(detail).startsWith("Parameter '" + parameter + "' must be an integer"));

        verifyNoInteractions(catalog);
    }

    @Test
    @DisplayName("The detail of an invalid size is the text that the specification shows")
    void detailOfAnInvalidSizeIsTheTextOfTheSpecification() {
        client.get().uri(PEOPLE + "?size=101")
                .exchange()
                .expectBody().jsonPath("$.detail").isEqualTo("Parameter 'size' must be an integer between 1 and 100.");
    }

    @Test
    @DisplayName("The documentation shows page and size as optional integers, with their limits and default values")
    void documentationShowsPageAndSizeAsIntegers() {
        Map<String, Object> page = documentedParameter("page");
        Map<String, Object> size = documentedParameter("size");

        assertThat(page).containsEntry("in", "query").containsEntry("required", false);
        assertThat(schemaOf(page))
                .containsEntry("type", "integer")
                .containsEntry("minimum", 1)
                .containsEntry("default", 1)
                .doesNotContainKey("maximum");
        assertThat((String) page.get("description")).isNotBlank();

        assertThat(size).containsEntry("in", "query").containsEntry("required", false);
        assertThat(schemaOf(size))
                .containsEntry("type", "integer")
                .containsEntry("minimum", 1)
                .containsEntry("maximum", 100)
                .containsEntry("default", 10);
        assertThat((String) size.get("description")).isNotBlank();
    }

    @Test
    @DisplayName("The documentation shows no other parameter for the list")
    void documentationShowsNoOtherParameter() {
        List<String> names = JsonPath.read(apiDocumentation(), "$.paths['/api/v1/people'].get.parameters[*].name");

        assertThat(names).containsExactlyInAnyOrder("page", "size", "id");
    }

    @Test
    @DisplayName("The documentation shows the answer and every error of the list")
    void documentationShowsTheAnswerAndTheErrors() {
        String documentation = apiDocumentation();
        Map<String, Object> responses = JsonPath.read(documentation, "$.paths['/api/v1/people'].get.responses");

        assertThat(responses).containsOnlyKeys("200", "400", "401", "502", "504");
        // The page, with its five fields, and the two fields of each element.
        Map<String, Object> pageSchema = JsonPath.read(documentation,
                "$.paths['/api/v1/people'].get.responses['200'].content['application/json'].schema");
        String pageSchemaName = ((String) pageSchema.get("$ref")).replace("#/components/schemas/", "");
        Map<String, Object> pageFields = JsonPath.read(documentation,
                "$.components.schemas['" + pageSchemaName + "'].properties");
        assertThat(pageFields).containsOnlyKeys("content", "page", "size", "totalElements", "totalPages");
        Map<String, Object> elementFields = JsonPath.read(documentation,
                "$.components.schemas['ItemSummary'].properties");
        assertThat(elementFields).containsOnlyKeys("id", "name");
        // The errors are Problem Details.
        for (String error : List.of("400", "401", "502", "504")) {
            Map<String, Object> content = JsonPath.read(documentation,
                    "$.paths['/api/v1/people'].get.responses['" + error + "'].content");
            assertThat(content).containsOnlyKeys("application/problem+json");
        }
    }

    @Test
    @DisplayName("The id of the request is passed to the core as the filter of the list, next to the page and the size")
    void idIsPassedToTheCoreAsAFilter() {
        when(catalog.list(any(), any())).thenReturn(A_PAGE);

        client.get().uri(PEOPLE + "?id=1").exchange().expectStatus().isOk();
        client.get().uri(PEOPLE + "?id=83&page=2&size=5").exchange().expectStatus().isOk();

        verify(catalog).list(PEOPLE_ENTITY, new ListQuery(new PageQuery(1, 10), new ItemId("1")));
        verify(catalog).list(PEOPLE_ENTITY, new ListQuery(new PageQuery(2, 5), new ItemId("83")));
    }

    @ParameterizedTest(name = "?{0}")
    @ValueSource(strings = {
        "id=0",
        "id=01",
        "id=abc",
        "id=-3",
        "id=1.0",
        "id=12345678901",
        // Empty, with a space before it, and sent twice.
        "id=",
        "id=%201",
        "id=1&id=2",
        // The other parameters being valid does not save it.
        "page=1&size=10&id=x"
    })
    @DisplayName("An invalid id in the list is answered with 400 naming the parameter and its rule, and the core is not asked")
    void invalidIdInTheListIsRejected(String query) {
        client.get().uri(PEOPLE + "?" + query)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Bad Request")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.instance").isEqualTo(PEOPLE)
                .jsonPath("$.detail").isEqualTo(ID_RULE);

        verifyNoInteractions(catalog);
    }

    @Test
    @DisplayName("The documentation shows id as an optional text with the pattern of an identifier, not as an integer")
    void documentationShowsIdAsATextWithAPattern() {
        Map<String, Object> id = documentedParameter("id");

        assertThat(id).containsEntry("in", "query").containsEntry("required", false);
        assertThat(schemaOf(id))
                .containsEntry("type", "string")
                .containsEntry("pattern", "^[1-9][0-9]{0,9}$")
                .doesNotContainKeys("minimum", "maximum", "default");
        assertThat((String) id.get("description")).isNotBlank();
    }

    @Test
    @DisplayName("The detail is answered as JSON with exactly the fields of the table of People")
    void detailIsAnsweredWithTheFieldsOfTheContract() {
        when(catalog.detail(any(), any(), eq(PersonDetail.class))).thenReturn(LUKE);

        client.get().uri(PEOPLE + "/1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
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
    @DisplayName("A value that the source does not have is answered as null, and a relation without elements as an empty list")
    void missingValueIsAnsweredAsNull() {
        when(catalog.detail(any(), any(), eq(PersonDetail.class))).thenReturn(new PersonDetail(
                "1", "Luke Skywalker", null, null, null, null, null, null, null, List.of(), List.of(), List.of()));

        client.get().uri(PEOPLE + "/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                          "id": "1",
                          "name": "Luke Skywalker",
                          "birthYear": null,
                          "gender": null,
                          "height": null,
                          "mass": null,
                          "hairColor": null,
                          "skinColor": null,
                          "eyeColor": null,
                          "filmIds": [],
                          "starshipIds": [],
                          "vehicleIds": []
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("The core is asked for the detail of that person, by the identifier of the route")
    void identifierOfTheRouteIsPassedToTheCore() {
        when(catalog.detail(any(), any(), eq(PersonDetail.class))).thenReturn(LUKE);

        client.get().uri(PEOPLE + "/83").exchange().expectStatus().isOk();

        verify(catalog).detail(PEOPLE_ENTITY, new ItemId("83"), PersonDetail.class);
    }

    @Test
    @DisplayName("The detail of a person that does not exist is answered with 404")
    void detailOfAPersonThatDoesNotExistIsNotFound() {
        when(catalog.detail(any(), any(), eq(PersonDetail.class))).thenThrow(new NotFoundException());

        client.get().uri(PEOPLE + "/9999")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Not Found")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").isEqualTo("The requested resource was not found.")
                .jsonPath("$.instance").isEqualTo(PEOPLE + "/9999");
    }

    @ParameterizedTest(name = "GET /api/v1/people/{0}")
    @ValueSource(strings = {"abc", "0", "01", "-3", "1.0", "12345678901", "1,2", "%201"})
    @DisplayName("An invalid identifier in the route is answered with 400 naming id and its rule, and the core is not asked")
    void invalidIdentifierInTheRouteIsRejected(String id) {
        client.get().uri(URI.create(PEOPLE + "/" + id))
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
    @DisplayName("The documentation shows the identifier of the route as a required text with the pattern of an identifier")
    void documentationShowsTheIdentifierOfTheRoute() {
        List<Map<String, Object>> parameters = JsonPath.read(apiDocumentation(),
                "$.paths['/api/v1/people/{id}'].get.parameters");

        assertThat(parameters).hasSize(1);
        Map<String, Object> id = parameters.getFirst();
        assertThat(id).containsEntry("name", "id").containsEntry("in", "path").containsEntry("required", true);
        assertThat(schemaOf(id)).containsEntry("type", "string").containsEntry("pattern", "^[1-9][0-9]{0,9}$");
        assertThat((String) id.get("description")).isNotBlank();
    }

    @Test
    @DisplayName("The documentation shows the answer and every error of the detail")
    void documentationShowsTheAnswerAndTheErrorsOfTheDetail() {
        String documentation = apiDocumentation();
        Map<String, Object> responses = JsonPath.read(documentation,
                "$.paths['/api/v1/people/{id}'].get.responses");

        assertThat(responses).containsOnlyKeys("200", "400", "401", "404", "502", "504");
        // The detail, with the twelve fields of the table of People and nothing else.
        String schema = JsonPath.read(documentation,
                "$.paths['/api/v1/people/{id}'].get.responses['200'].content['application/json'].schema['$ref']");
        assertThat(schema).isEqualTo("#/components/schemas/PersonDetail");
        Map<String, Map<String, Object>> fields = JsonPath.read(documentation,
                "$.components.schemas['PersonDetail'].properties");
        assertThat(fields).containsOnlyKeys(
                "id", "name", "birthYear", "gender", "height", "mass", "hairColor", "skinColor", "eyeColor",
                "filmIds", "starshipIds", "vehicleIds");
        assertThat(fields.get("id")).containsEntry("type", "string");
        assertThat(fields.get("height")).containsEntry("type", "string");
        assertThat(fields.get("filmIds")).containsEntry("type", "array");
        // The errors are Problem Details.
        for (String error : List.of("400", "401", "404", "502", "504")) {
            Map<String, Object> content = JsonPath.read(documentation,
                    "$.paths['/api/v1/people/{id}'].get.responses['" + error + "'].content");
            assertThat(content).containsOnlyKeys("application/problem+json");
        }
    }

    private Map<String, Object> documentedParameter(String name) {
        List<Map<String, Object>> found = JsonPath.read(apiDocumentation(),
                "$.paths['/api/v1/people'].get.parameters[?(@.name=='" + name + "')]");
        assertThat(found).hasSize(1);
        return found.getFirst();
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
