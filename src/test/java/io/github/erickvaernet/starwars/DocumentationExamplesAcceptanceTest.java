package io.github.erickvaernet.starwars;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance test of the examples of the API documentation: what Swagger UI shows as the answer
 * of a request is an answer that the API really gives.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL and the simulated SWAPI.
 * The test reads the generated documentation the way Swagger UI does, {@code GET /v3/api-docs},
 * and then makes the request that each example stands for: the example and the answer have to be
 * the same, field by field.
 *
 * <p>Two things made it necessary. An element of a list is "one of two", its summary or its
 * detail, and the example that the page builds by itself from that mixes both in one page, which
 * the API never answers. And the schema of an error that comes with Spring has a field,
 * {@code properties}, that no answer has.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class DocumentationExamplesAcceptanceTest {

    private static final String JSON = "application/json";

    private static final String PROBLEM = "application/problem+json";

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String USERS = "/api/v1/users";

    @LocalServerPort
    private int port;

    @Autowired
    private WireMockServer swapi;

    /** Every cache of the application, however many there are. */
    @Autowired
    private ObjectProvider<Cache<?, ?>> caches;

    /** A client without a session. */
    private RestTestClient anonymous;

    private String documentation;

    @BeforeEach
    void setUp() {
        anonymous = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        caches.forEach(Cache::invalidateAll);
        documentation = anonymous.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    @ParameterizedTest(name = "GET {0}")
    @CsvSource({
        "/api/v1/people, name",
        "/api/v1/films, title",
        "/api/v1/starships, name",
        "/api/v1/vehicles, name"
    })
    @DisplayName("Every list documents two examples of its answer, the page of summaries and the page with the complete data, and each one is what the API answers")
    void listDocumentsTheTwoPagesThatTheApiAnswers(String list, String nameOfADetail) {
        Map<String, Map<String, Object>> examples = JsonPath.read(documentation,
                "$.paths['" + list + "'].get.responses['200'].content['" + JSON + "'].examples");
        // Never a single example that mixes a summary and a detail in one page.
        assertThat(examples).containsOnlyKeys("summary", "expanded");

        RestTestClient demo = TestSession.open(anonymous);
        Map<String, Object> answer = JsonPath.read(body(demo.get().uri(list + "?expanded=true&size=2"), 200), "$");

        assertThat(examples.get("expanded").get("value")).isEqualTo(answer);
        assertThat(examples.get("summary").get("value")).isEqualTo(withSummaries(answer, nameOfADetail));
    }

    @Test
    @DisplayName("The schema of an error has the fields that an error of the API has, and no other")
    void schemaOfAnErrorHasTheFieldsOfAnErrorOfTheApi() {
        Map<String, Object> everyError = JsonPath.read(documentation,
                "$.components.schemas['ProblemDetail'].properties");
        // Without "properties", which the schema that comes with Spring has and no answer does.
        assertThat(everyError).containsOnlyKeys("type", "title", "status", "detail", "instance");

        // The list of invalid fields is next to the detail, and it is not always there.
        Map<String, Object> onlyOfAValidationError = JsonPath.read(documentation,
                "$.components.schemas['ValidationProblemDetail'].allOf[1].properties");
        assertThat(onlyOfAValidationError).containsOnlyKeys("errors");
        List<Map<String, Object>> parts = JsonPath.read(documentation,
                "$.components.schemas['ValidationProblemDetail'].allOf");
        assertThat(parts.getFirst()).containsEntry("$ref", "#/components/schemas/ProblemDetail");
        assertThat(parts).noneMatch(part -> part.containsKey("required"));
    }

    @Test
    @DisplayName("Every error of every request has an example with the form of an error of the API, its own code and its own address")
    void everyErrorHasAnExampleWithTheFormOfAnErrorOfTheApi() {
        Map<String, Map<String, Map<String, Object>>> paths = JsonPath.read(documentation, "$.paths");
        List<String> checked = new ArrayList<>();

        paths.forEach((path, requests) -> requests.forEach((method, request) -> {
            Map<String, Object> responses = JsonPath.read(request, "$.responses");
            for (String code : responses.keySet()) {
                if (code.startsWith("2")) {
                    continue;
                }
                String where = method + " " + path + " " + code;
                Map<String, Object> example = exampleOf(method, path, code);
                HttpStatus status = HttpStatus.valueOf(Integer.parseInt(code));

                List<String> fields = new ArrayList<>(List.of("type", "title", "status", "detail", "instance"));
                if (status == HttpStatus.BAD_REQUEST) {
                    fields.add("errors");
                }
                assertThat(example.keySet()).as(where).containsExactlyElementsOf(fields);
                assertThat(example).as(where)
                        .containsEntry("type", "about:blank")
                        .containsEntry("title", status.getReasonPhrase())
                        .containsEntry("status", status.value());
                assertThat((String) example.get("detail")).as(where).isNotBlank();
                // The address of the request itself, with an identifier where the route has one.
                assertThat((String) example.get("instance")).as(where)
                        .matches(Pattern.quote(path).replace("{id}", "\\E[a-z0-9]+\\Q"));
                checked.add(where);
            }
        }));

        // Ten requests, each with at least its 400 and its 401.
        assertThat(checked).hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    @DisplayName("The examples of the errors of a list and of a detail are what the API answers")
    void errorExamplesOfAListAndOfADetailAreRealAnswers() {
        RestTestClient demo = TestSession.open(anonymous);

        assertThat(problem(demo.get().uri("/api/v1/people?id=abc"), 400))
                .isEqualTo(exampleOf("get", "/api/v1/people", "400"));
        assertThat(problem(anonymous.get().uri("/api/v1/films"), 401))
                .isEqualTo(exampleOf("get", "/api/v1/films", "401"));
        assertThat(problem(demo.get().uri("/api/v1/films/abc"), 400))
                .isEqualTo(exampleOf("get", "/api/v1/films/{id}", "400"));
        assertThat(problem(anonymous.get().uri("/api/v1/vehicles/1"), 401))
                .isEqualTo(exampleOf("get", "/api/v1/vehicles/{id}", "401"));
        assertThat(problem(demo.get().uri("/api/v1/people/9999"), 404))
                .isEqualTo(exampleOf("get", "/api/v1/people/{id}", "404"));

        // SWAPI fails: the recording is covered, for this request only, by an error.
        StubMapping failure = swapi.stubFor(get(urlPathEqualTo("/api/starships")).atPriority(1)
                .willReturn(aResponse().withStatus(500)));
        try {
            assertThat(problem(demo.get().uri("/api/v1/starships"), 502))
                    .isEqualTo(exampleOf("get", "/api/v1/starships", "502"));
        } finally {
            swapi.removeStub(failure);
        }
        // The 504 is not provoked here: it would take the longest wait for SWAPI. Its text is
        // the one that SwapiFailureAcceptanceTest gets.
        assertThat(exampleOf("get", "/api/v1/starships", "504"))
                .containsEntry("detail", "The data source did not respond in time.");
    }

    @Test
    @DisplayName("The examples of the errors of the login are what the API answers")
    void errorExamplesOfTheLoginAreRealAnswers() {
        assertThat(problem(json(anonymous.post().uri(LOGIN), "{\"username\": \"\", \"password\": \"demo1234\"}"), 400))
                .isEqualTo(exampleOf("post", LOGIN, "400"));
        assertThat(problem(json(anonymous.post().uri(LOGIN), "{\"username\": \"demo\", \"password\": \"not-it\"}"), 401))
                .isEqualTo(exampleOf("post", LOGIN, "401"));
    }

    @Test
    @DisplayName("The examples of the errors of the creation of users are what the API answers")
    void errorExamplesOfTheCreationOfUsersAreRealAnswers() {
        RestTestClient demo = TestSession.open(anonymous);
        RestTestClient admin = TestSession.openAsAdmin(anonymous);
        String valid = "{\"username\": \"leia\", \"password\": \"alderaan-1977\"}";

        assertThat(problem(json(admin.post().uri(USERS),
                "{\"username\": \"L\", \"password\": \"x\", \"role\": \"admin\"}"), 400))
                .isEqualTo(exampleOf("post", USERS, "400"));
        assertThat(problem(json(anonymous.post().uri(USERS), valid), 401))
                .isEqualTo(exampleOf("post", USERS, "401"));
        assertThat(problem(json(demo.post().uri(USERS), valid), 403))
                .isEqualTo(exampleOf("post", USERS, "403"));
        assertThat(problem(json(admin.post().uri(USERS),
                "{\"username\": \"demo\", \"password\": \"alderaan-1977\"}"), 409))
                .isEqualTo(exampleOf("post", USERS, "409"));
    }

    @Test
    @DisplayName("The documentation says once, for every request, the two errors that do not depend on the request: 406 and 415")
    void documentationSaysOnceTheErrorsThatAreTheSameForEveryRequest() {
        String description = JsonPath.read(documentation, "$.info.description");

        // They are answered whichever the request, so no request repeats them among its answers.
        assertThat(description)
                .contains("`Accept`").contains("406")
                .contains("`Content-Type`").contains("415");
    }

    /** The same page with the summary of each element in its place: its identifier and its name. */
    private static Map<String, Object> withSummaries(Map<String, Object> page, String nameOfADetail) {
        List<Map<String, Object>> elements = JsonPath.read(page, "$.content");
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Map<String, Object> element : elements) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", element.get("id"));
            summary.put("name", element.get(nameOfADetail));
            summaries.add(summary);
        }
        Map<String, Object> withSummaries = new LinkedHashMap<>(page);
        withSummaries.put("content", summaries);
        return withSummaries;
    }

    /** The example that the documentation shows for an error of a request. */
    private Map<String, Object> exampleOf(String method, String path, String code) {
        return JsonPath.read(documentation,
                "$.paths['" + path + "']." + method + ".responses['" + code + "'].content['" + PROBLEM + "'].example");
    }

    private static RestTestClient.RequestHeadersSpec<?> json(RestTestClient.RequestBodySpec request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /** Makes a request, checks its status and that it is an error of the API, and returns its body. */
    private static Map<String, Object> problem(RestTestClient.RequestHeadersSpec<?> request, int status) {
        return JsonPath.read(request.exchange()
                .expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class).returnResult().getResponseBody(), "$");
    }

    /** Makes a request, checks its status and returns its body. */
    private static String body(RestTestClient.RequestHeadersSpec<?> request, int status) {
        return request.exchange()
                .expectStatus().isEqualTo(status)
                .expectBody(String.class).returnResult().getResponseBody();
    }

}
