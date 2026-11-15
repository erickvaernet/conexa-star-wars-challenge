package io.github.erickvaernet.starwars.catalog.people;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.JsonPath;
import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.CatalogService;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.ListQuery;
import io.github.erickvaernet.starwars.catalog.PageQuery;
import io.github.erickvaernet.starwars.catalog.PageResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * Checks the request that lists people: what it passes to the core, how it answers and how it is
 * documented.
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

    private static final CatalogEntity PEOPLE_ENTITY = new CatalogEntity("people");

    private static final PageResult<ItemSummary> A_PAGE = new PageResult<>(
            List.of(new ItemSummary("1", "Luke Skywalker"), new ItemSummary("2", "C-3PO")), 1, 2, 82, 41);

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

        assertThat(names).containsExactlyInAnyOrder("page", "size");
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
