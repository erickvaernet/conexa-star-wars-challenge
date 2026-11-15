package io.github.erickvaernet.starwars;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-12: the API documentation shows every request and how to authenticate.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, and the tests read the
 * generated documentation the way Swagger UI does: {@code GET /v3/api-docs}, without a session.
 * No test asks for data, so SWAPI is not needed.
 *
 * <p>A request is written here as its method and its route, {@code GET /api/v1/people}. What a
 * request requires is read as OpenAPI defines it: its own {@code security} if it has one, or
 * else the {@code security} of the whole document. That way the tests do not depend on where the
 * requirement is declared.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApiDocumentationAcceptanceTest {

    private static final String LOGIN = "POST /api/v1/auth/login";

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

    /** The request that creates a user, which only an administrator can make. */
    private static final String USER_CREATION = "POST /api/v1/users";

    @LocalServerPort
    private int port;

    private RestTestClient client;

    private String documentation;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        documentation = client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    @Test
    @DisplayName("CA-12.1: the documentation includes the login, the four lists and the four details")
    void documentationIncludesEveryRequest() {
        List<String> expected = new ArrayList<>();
        expected.add(LOGIN);
        expected.addAll(LISTS);
        expected.addAll(DETAILS);
        expected.add(USER_CREATION);

        // Exactly these ten: a request that is added later has to be added here too.
        assertThat(documentedRequests()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("CA-12.2: the documentation declares the Bearer security scheme and applies it to the lists and the details")
    void documentationDeclaresTheBearerSchemeAndAppliesItToTheListsAndTheDetails() {
        String scheme = nameOfTheBearerScheme();

        for (String request : LISTS) {
            assertThat(securityOf(request)).as(request).containsExactly(Map.of(scheme, List.of()));
        }
        for (String request : DETAILS) {
            assertThat(securityOf(request)).as(request).containsExactly(Map.of(scheme, List.of()));
        }
    }

    @Test
    @DisplayName("The login requires no token: it is the request that gives it")
    void loginRequiresNoToken() {
        assertThat(documentedRequests()).contains(LOGIN);

        // Swagger UI shows a padlock on a request that requires the token, and sends it the token.
        assertThat(securityOf(LOGIN)).isEmpty();
    }

    @Test
    @DisplayName("Every request other than the login requires the token, whichever requests there are")
    void everyRequestOtherThanTheLoginRequiresTheToken() {
        String scheme = nameOfTheBearerScheme();

        // It reads the requests from the documentation, not from the lists above: a request that
        // is added later and does not require the token is caught here.
        for (String request : documentedRequests()) {
            if (!request.equals(LOGIN)) {
                assertThat(securityOf(request)).as(request).containsExactly(Map.of(scheme, List.of()));
            }
        }
    }

    @Test
    @DisplayName("Swagger UI shows the login first: it sorts the groups by name, and the group of the login is the first one")
    void swaggerUiShowsTheLoginFirst() {
        // Without a sorter, Swagger UI shows the groups in the order in which the documentation
        // happens to list them, and whoever tries the API has to start by the login.
        String settings = client.get().uri("/v3/api-docs/swagger-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        Map<String, Object> settingsOfThePage = JsonPath.read(settings, "$");
        assertThat(settingsOfThePage).containsEntry("tagsSorter", "alpha");

        List<String> groupsOfTheLogin = JsonPath.read(documentation, "$.paths['/api/v1/auth/login'].post.tags");
        assertThat(groupsOfTheLogin).hasSize(1);
        List<String> everyGroup = JsonPath.read(documentation, "$.paths.*.*.tags[*]");
        assertThat(everyGroup.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).findFirst())
                .contains(groupsOfTheLogin.getFirst());
    }

    @Test
    @DisplayName("Each request has a name of its own in the documentation, which says what it does")
    void eachRequestHasANameOfItsOwn() {
        // The generated clients name their methods after these. Without a name of its own, each
        // request takes the one of its method in the code, and the repeated ones get a number:
        // "list_2", "detail_3".
        Map<String, String> expected = Map.of(
                "POST /api/v1/auth/login", "login",
                "GET /api/v1/people", "listPeople",
                "GET /api/v1/people/{id}", "getPerson",
                "GET /api/v1/films", "listFilms",
                "GET /api/v1/films/{id}", "getFilm",
                "GET /api/v1/starships", "listStarships",
                "GET /api/v1/starships/{id}", "getStarship",
                "GET /api/v1/vehicles", "listVehicles",
                "GET /api/v1/vehicles/{id}", "getVehicle",
                "POST /api/v1/users", "createUser");

        Map<String, String> documented = new HashMap<>();
        for (String request : documentedRequests()) {
            String method = request.substring(0, request.indexOf(' ')).toLowerCase(Locale.ROOT);
            String path = request.substring(request.indexOf(' ') + 1);
            documented.put(request, JsonPath.read(documentation, "$.paths['" + path + "']." + method + ".operationId"));
        }

        assertThat(documented).containsExactlyInAnyOrderEntriesOf(expected);
    }

    /** Every request of the documentation, as its method and its route. */
    private List<String> documentedRequests() {
        Map<String, Map<String, Object>> paths = JsonPath.read(documentation, "$.paths");
        List<String> requests = new ArrayList<>();
        paths.forEach((path, methods) -> methods.keySet()
                .forEach(method -> requests.add(method.toUpperCase(Locale.ROOT) + " " + path)));
        return requests;
    }

    /**
     * Checks that the documentation declares one security scheme, and that it is the one of this
     * API: HTTP, Bearer, with a JWT as the token. Returns the name the requests refer to it by.
     */
    private String nameOfTheBearerScheme() {
        Map<String, Object> components = JsonPath.read(documentation, "$.components");
        assertThat(components.keySet()).contains("securitySchemes");
        Map<String, Map<String, Object>> schemes = JsonPath.read(documentation, "$.components.securitySchemes");
        assertThat(schemes).hasSize(1);

        Map.Entry<String, Map<String, Object>> scheme = schemes.entrySet().iterator().next();
        assertThat(scheme.getValue())
                .containsEntry("type", "http")
                .containsEntry("scheme", "bearer")
                .containsEntry("bearerFormat", "JWT");
        return scheme.getKey();
    }

    /**
     * What a request requires: a list of security schemes by name, each with its scopes, which
     * Bearer does not have. An empty list means that the request requires nothing.
     */
    private List<Map<String, List<String>>> securityOf(String request) {
        String method = request.substring(0, request.indexOf(' ')).toLowerCase(Locale.ROOT);
        String path = request.substring(request.indexOf(' ') + 1);

        Map<String, Object> whole = JsonPath.read(documentation, "$");
        Map<String, Object> operation = JsonPath.read(documentation, "$.paths['" + path + "']." + method);
        Object security = operation.containsKey("security") ? operation.get("security") : whole.get("security");
        if (security == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Map<String, List<String>>> required = (List<Map<String, List<String>>>) security;
        return required;
    }

}
