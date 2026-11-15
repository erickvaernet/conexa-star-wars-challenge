package io.github.erickvaernet.starwars;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance test for REQ-24 in the API documentation: every list shows the parameter
 * {@code expanded}, and that an element of its answer is one of two things.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and the test reads the
 * generated documentation the way Swagger UI does: {@code GET /v3/api-docs}, without a session.
 * No test asks for data, so SWAPI is not needed.
 *
 * <p>The documentation is read as OpenAPI defines it, without depending on the names that the
 * generator gives to its schemas: where a schema is a reference ({@code $ref}), the test follows
 * it. The two forms of an element are recognised by what they are. The detail is the schema that
 * the detail of the same entity answers with, and the summary is the one that has exactly an
 * identifier and a name.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ExpandedListDocumentationAcceptanceTest {

    private static final String JSON = "application/json";

    @LocalServerPort
    private int port;

    private String documentation;

    @BeforeEach
    void setUp() {
        documentation = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/api/v1/people",
        "/api/v1/films",
        "/api/v1/starships",
        "/api/v1/vehicles"
    })
    @DisplayName("CA-24.15: every list declares expanded as a boolean, false by default, and its answer 200 documents the two forms of an element: the summary and the detail of the entity")
    void listDocumentsTheParameterAndTheTwoFormsOfAnElement(String list) {
        Map<String, Object> expanded = parameterOf(list, "expanded");
        assertThat(expanded).containsEntry("in", "query");
        // Optional: without it, the list is the one of always.
        assertThat(expanded.get("required")).isIn(null, false);
        assertThat(schemaOf(expanded))
                .containsEntry("type", "boolean")
                // The boolean false, not the text "false".
                .containsEntry("default", false);
        // It says what the parameter does, which the type alone does not.
        assertThat((String) expanded.get("description")).isNotBlank();

        Map<String, Object> page = resolved(answerOf(list));
        Map<String, Object> content = property(page, "content");
        assertThat(content).containsEntry("type", "array");
        Map<String, Object> element = resolved(schemaIn(content, "items"));

        // One of two: exactly the summary and the detail of this entity, as references to the
        // schemas that the documentation already has for them.
        assertThat(element).containsKey("oneOf");
        List<Map<String, Object>> forms = JsonPath.read(element, "$.oneOf");
        assertThat(forms).hasSize(2).allSatisfy(form -> assertThat(form).containsOnlyKeys("$ref"));
        String detail = (String) answerOf(list + "/{id}").get("$ref");
        assertThat(detail).as("the answer of the detail is a schema with a name").isNotNull();
        assertThat(forms).contains(Map.of("$ref", detail));
        Map<String, Object> summary = forms.stream()
                .filter(form -> !form.equals(Map.of("$ref", detail)))
                .findFirst()
                .map(this::resolved)
                .orElseThrow();
        assertThat(properties(summary)).containsOnlyKeys("id", "name");
        // The detail of the entity is not the summary under another name: it has more data.
        assertThat(properties(resolved(Map.of("$ref", detail))).keySet())
                .contains("id")
                .hasSizeGreaterThan(2);

        // The rest of the page is documented as before.
        assertThat(properties(page)).containsOnlyKeys("content", "page", "size", "totalElements", "totalPages");
    }

    /** One query parameter of a list, by its name. The test fails if the list does not declare it. */
    private Map<String, Object> parameterOf(String path, String name) {
        List<Map<String, Object>> parameters = JsonPath.read(documentation, "$.paths['" + path + "'].get.parameters");
        return parameters.stream()
                .filter(parameter -> name.equals(parameter.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("GET " + path + " does not declare the parameter " + name));
    }

    /** The schema of the body of the answer 200 of a request, as JSON. */
    private Map<String, Object> answerOf(String path) {
        return JsonPath.read(documentation,
                "$.paths['" + path + "'].get.responses['200'].content['" + JSON + "'].schema");
    }

    /** The schema itself, or the one it refers to if it is a reference to a schema with a name. */
    private Map<String, Object> resolved(Map<String, Object> schema) {
        if (!(schema.get("$ref") instanceof String reference)) {
            return schema;
        }
        String prefix = "#/components/schemas/";
        assertThat(reference).startsWith(prefix);
        return JsonPath.read(documentation, "$.components.schemas['" + reference.substring(prefix.length()) + "']");
    }

    private Map<String, Object> schemaOf(Map<String, Object> parameter) {
        return schemaIn(parameter, "schema");
    }

    private Map<String, Object> property(Map<String, Object> schema, String name) {
        return schemaIn(properties(schema), name);
    }

    private Map<String, Object> properties(Map<String, Object> schema) {
        return schemaIn(schema, "properties");
    }

    /** A schema that is inside another piece of the documentation, under that key. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> schemaIn(Map<String, Object> container, String key) {
        assertThat(container).containsKey(key);
        return (Map<String, Object>) container.get(key);
    }

}
