package io.github.erickvaernet.starwars;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-21: a validation error says which field or parameter is invalid.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, with the seed switched
 * on. The login is asked without a session; the lists and the detail, with the token of the test
 * user. Every request of a list or of a detail made here is rejected before anything is asked to
 * SWAPI, so no recording is needed.
 *
 * <p>The answers with {@code errors} are compared as a whole and strictly: the fields of every
 * error of the API and {@code errors}, nothing else; in {@code errors}, these elements, in this
 * order, each with {@code field} and {@code message} only. The texts are the ones of the table
 * of section 5 of the specification.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class ValidationErrorAcceptanceTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String PEOPLE = "/api/v1/people";

    /** The password of the test user: a valid one, so that only the user name is wrong. */
    private static final String PASSWORD = "demo1234";

    private static final String USERNAME_RULE =
            "must not be blank, and must have at most 50 characters and no control characters";

    private static final String PASSWORD_RULE = "must not be blank, and must have at most 72 characters";

    private static final List<String> LISTS = List.of(
            "GET /api/v1/people",
            "GET /api/v1/films",
            "GET /api/v1/starships",
            "GET /api/v1/vehicles");

    private static final List<String> DETAILS = List.of(
            "GET /api/v1/people/{id}",
            "GET /api/v1/films/{id}",
            "GET /api/v1/starships/{id}",
            "GET /api/v1/vehicles/{id}");

    @LocalServerPort
    private int port;

    /** A client without a session, for the login and the API documentation. */
    private RestTestClient anonymous;

    @BeforeEach
    void setUp() {
        anonymous = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("CA-21.1: a login with an empty user name is rejected with 400, and errors has only the user name, with its fixed text")
    void loginWithAnEmptyUserNameSaysThatTheUserNameIsInvalid() {
        login(Map.of("username", "", "password", PASSWORD))
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Invalid request content.",
                          "instance": "/api/v1/auth/login",
                          "errors": [
                            {"field": "username", "message": "%s"}
                          ]
                        }
                        """.formatted(USERNAME_RULE), JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("CA-21.2: a login without user name and without password is rejected with 400, and errors has both: password first, then username")
    void loginWithoutUserNameAndWithoutPasswordSaysThatBothAreInvalid() {
        login(Map.of())
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                // Strict: the order of the list is checked too.
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Invalid request content.",
                          "instance": "/api/v1/auth/login",
                          "errors": [
                            {"field": "password", "message": "%s"},
                            {"field": "username", "message": "%s"}
                          ]
                        }
                        """.formatted(PASSWORD_RULE, USERNAME_RULE), JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("CA-21.3: a login with a user name of 51 characters is rejected with 400; errors names the user name and the answer does not repeat it")
    void loginWithAUserNameThatIsTooLongDoesNotRepeatIt() {
        // 51 characters that no fixed text has, so that finding them means that they were repeated.
        String username = "q7".repeat(25) + "q";
        assertThat(username).hasSize(51);

        byte[] bytes = login(Map.of("username", username, "password", PASSWORD))
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Invalid request content.",
                          "instance": "/api/v1/auth/login",
                          "errors": [
                            {"field": "username", "message": "%s"}
                          ]
                        }
                        """.formatted(USERNAME_RULE), JsonCompareMode.STRICT)
                .returnResult().getResponseBody();

        // Neither the whole value nor a piece of it, and not the password either.
        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .doesNotContain(username)
                .doesNotContain("q7q7")
                .doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("CA-21.4: a size of 101 is rejected with 400; the detail is the one of CA-05.4 and errors has one element, the one of size")
    void sizeThatIsTooLargeSaysThatSizeIsInvalid() {
        session().get().uri(PEOPLE + "?size=101")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Parameter 'size' must be an integer between 1 and 100.",
                          "instance": "/api/v1/people",
                          "errors": [
                            {"field": "size", "message": "must be an integer between 1 and 100"}
                          ]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("CA-21.5: with an invalid page and an invalid size, errors has one element only: the one of page, the first in the order of the parameters")
    void invalidPageAndSizeSayOnlyThatPageIsInvalid() {
        session().get().uri(PEOPLE + "?page=0&size=0")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Parameter 'page' must be an integer greater than or equal to 1.",
                          "instance": "/api/v1/people",
                          "errors": [
                            {"field": "page", "message": "must be an integer greater than or equal to 1"}
                          ]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("CA-21.6: a detail with an identifier that is not a number is rejected with 400, and errors has one element, with the field id")
    void detailWithAnInvalidIdentifierSaysThatIdIsInvalid() {
        session().get().uri(PEOPLE + "/abc")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Parameter 'id' must be a number of 1 to 10 digits, without leading zeros.",
                          "instance": "/api/v1/people/abc",
                          "errors": [
                            {"field": "id", "message": "must be a number of 1 to 10 digits, without leading zeros"}
                          ]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("CA-21.7: a login with a body that is not JSON is rejected with 400, as Problem Details and without errors")
    void loginWithABodyThatIsNotJsonHasNoErrors() {
        byte[] bytes = anonymous.post().uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("this is not JSON")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Bad Request")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.instance").isEqualTo(LOGIN)
                .returnResult().getResponseBody();

        // No field failed: the body could not even be read. So there is no list, not even an
        // empty one.
        Map<String, Object> problem = JsonPath.read(new String(bytes, StandardCharsets.UTF_8), "$");
        assertThat(problem).containsOnlyKeys("type", "title", "status", "detail", "instance");
    }

    @Test
    @DisplayName("CA-21.8: a login sent as text/plain is rejected with 415, as Problem Details, with a fixed text that does not repeat the content type")
    void loginWithAContentTypeThatIsNotJsonHasAFixedText() {
        byte[] bytes = anonymous.post().uri(LOGIN)
                .contentType(MediaType.TEXT_PLAIN)
                .body("""
                        {"username": "demo", "password": "demo1234"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(415)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Unsupported Media Type",
                          "status": 415,
                          "detail": "The content type of the request is not supported.",
                          "instance": "/api/v1/auth/login"
                        }
                        """, JsonCompareMode.STRICT)
                .returnResult().getResponseBody();

        // Nothing of what the client wrote, anywhere in the body.
        assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotContain("text/plain").doesNotContain("plain");
    }

    @Test
    @DisplayName("CA-21.9: in the API documentation, the 400 of the login, of the lists and of the details declares errors, with field and message")
    void documentationDeclaresErrorsInTheBadRequestOfTheLoginTheListsAndTheDetails() {
        String documentation = anonymous.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();

        List<String> requests = new ArrayList<>();
        requests.add("POST " + LOGIN);
        requests.addAll(LISTS);
        // The criterion names the login and the lists. The details are here because their 400 has
        // errors too (CA-21.6), and the documentation has to say what the API answers.
        requests.addAll(DETAILS);

        for (String request : requests) {
            Map<String, Object> badRequest = fieldsOfTheBadRequest(documentation, request);

            // It is still Problem Details: the fields of every error, and errors.
            assertThat(badRequest).as(request)
                    .containsKeys("type", "title", "status", "detail", "instance", "errors");

            Map<String, Object> errors = resolve(documentation, badRequest.get("errors"));
            assertThat(errors).as(request + ", errors").containsEntry("type", "array");
            Map<String, Object> element = fieldsOf(documentation, errors.get("items"));
            assertThat(element).as(request + ", an element of errors").containsOnlyKeys("field", "message");
            assertThat(resolve(documentation, element.get("field")))
                    .as(request + ", field").containsEntry("type", "string");
            assertThat(resolve(documentation, element.get("message")))
                    .as(request + ", message").containsEntry("type", "string");
        }
    }

    private RestTestClient.ResponseSpec login(Map<String, String> credentials) {
        return anonymous.post().uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(credentials)
                .exchange();
    }

    /** A client that sends the token of the test user with every request. */
    private RestTestClient session() {
        return TestSession.open(anonymous);
    }

    /**
     * The fields that the documentation declares for the 400 of a request, written as its method
     * and its route: {@code GET /api/v1/people}.
     */
    private static Map<String, Object> fieldsOfTheBadRequest(String documentation, String request) {
        String method = request.substring(0, request.indexOf(' ')).toLowerCase(Locale.ROOT);
        String path = request.substring(request.indexOf(' ') + 1);

        Map<String, Object> content = JsonPath.read(documentation,
                "$.paths['" + path + "']." + method + ".responses['400'].content");
        assertThat(content).as(request).containsOnlyKeys(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        @SuppressWarnings("unchecked")
        Map<String, Object> mediaType = (Map<String, Object>) content.get(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        return fieldsOf(documentation, mediaType.get("schema"));
    }

    /**
     * The fields of a schema, by name. OpenAPI lets a schema be written in several ways that mean
     * the same: in place or as a reference to a named one, and with its fields listed or as the
     * sum of other schemas ({@code allOf}). All of them are read here, so that the test does not
     * depend on which one the documentation uses.
     */
    private static Map<String, Object> fieldsOf(String documentation, Object schema) {
        Map<String, Object> resolved = resolve(documentation, schema);
        Map<String, Object> fields = new LinkedHashMap<>();
        if (resolved.get("allOf") instanceof List<?> parts) {
            for (Object part : parts) {
                fields.putAll(fieldsOf(documentation, part));
            }
        }
        if (resolved.get("properties") instanceof Map<?, ?> properties) {
            properties.forEach((name, field) -> fields.put((String) name, field));
        }
        return fields;
    }

    /** A schema as it is, or the named schema it refers to. */
    private static Map<String, Object> resolve(String documentation, Object schema) {
        assertThat(schema).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) schema;
        if (map.get("$ref") instanceof String reference) {
            String name = reference.replace("#/components/schemas/", "");
            return JsonPath.read(documentation, "$.components.schemas['" + name + "']");
        }
        return map;
    }

}
