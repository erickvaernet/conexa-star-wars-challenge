package io.github.erickvaernet.starwars;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Acceptance tests for REQ-28: the collection of requests that the repository brings is the one
 * of this API, and it can be followed in order with the token already loaded.
 *
 * <p>The tests read the file of the collection as it is in the repository and compare it with
 * the generated documentation, {@code GET /v3/api-docs}, in both directions: a request that is
 * added to the API has to be added to the collection, and a request of the collection that the
 * API no longer has, or that sends a parameter that was renamed, is caught here.
 *
 * <p>No test sends the requests of the collection, so SWAPI is not needed. The application runs
 * with the same configuration as in {@link ApiDocumentationAcceptanceTest}, on purpose: both
 * classes share one running application.
 *
 * <p>A request is written here as its method and its route, {@code GET /api/v1/people}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RequestCollectionAcceptanceTest {

    /** Relative to the root of the repository, which is where the tests run. */
    private static final Path COLLECTION = Path.of("docs", "api", "star-wars-api.postman_collection.json");

    /** What a collection declares to say that its format is the 2.1 of Postman. */
    private static final String POSTMAN_2_1 = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";

    /** The variable with the address of the server, as a request of the collection writes it. */
    private static final String BASE_URL = "{{baseUrl}}";

    private static final String LOGIN = "POST /api/v1/auth/login";

    private static final String USER_CREATION = "POST /api/v1/users";

    private static final List<String> LISTS = List.of(
            "GET /api/v1/people",
            "GET /api/v1/films",
            "GET /api/v1/starships",
            "GET /api/v1/vehicles");

    /** The line of the script of a login that saves the token in the variable of the collection. */
    private static final String SAVES_THE_TOKEN = "pm.collectionVariables.set(\"token\", pm.response.json().accessToken)";

    /** It rejects whatever is not strict JSON: comments, single quotes, a comma or text left over. */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    private JsonNode collection;

    /** Every request of the collection, in the order in which a tool runs them. */
    private List<Sent> requests;

    /** Every folder of the collection. */
    private List<JsonNode> folders;

    private JsonNode documentation;

    @BeforeEach
    void setUp() throws IOException {
        assertThat(COLLECTION).as("the file of the collection").isRegularFile();
        collection = JSON.readTree(Files.readString(COLLECTION));
        requests = new ArrayList<>();
        folders = new ArrayList<>();
        collect(collection.path("item"));

        RestTestClient client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        documentation = JSON.readTree(client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody());
    }

    @Test
    @DisplayName("CA-28.1: the file is a Postman 2.1 collection with exactly two variables, baseUrl and an empty token")
    void fileIsAPostmanCollectionWithItsTwoVariables() {
        // That the file is valid JSON is checked when it is read, before each test.
        assertThat(collection.path("info").path("schema").asString()).isEqualTo(POSTMAN_2_1);

        assertThat(collection.path("variable")).hasSize(2);
        assertThat(keysAndValues(collection.path("variable"))).containsOnly(
                Map.entry("baseUrl", "http://localhost:8080"),
                Map.entry("token", ""));
    }

    @Test
    @DisplayName("CA-28.2: every documented request has at least one request in the collection, all ten")
    void everyDocumentedRequestIsInTheCollection() {
        List<String> inTheCollection = requests.stream()
                .map(this::documentedRequestOf)
                .flatMap(Optional::stream)
                .distinct()
                .toList();

        // Ten today: with an empty documentation the comparison would pass without checking anything.
        assertThat(documentedRequests()).hasSize(10);
        assertThat(inTheCollection).containsExactlyInAnyOrderElementsOf(documentedRequests());
    }

    @Test
    @DisplayName("CA-28.3: every request of the collection starts with {{baseUrl}}, is a documented request and sends only parameters which that request declares")
    void everyRequestOfTheCollectionIsADocumentedRequest() {
        assertThat(requests).isNotEmpty();

        for (Sent sent : requests) {
            assertThat(sent.address()).as(sent.name()).startsWith(BASE_URL + "/");

            // A part like "1" takes the place of "{id}".
            Optional<String> documented = documentedRequestOf(sent);
            assertThat(documented).as(sent.name() + ": " + sent.method() + " " + sent.route()).isPresent();

            assertThat(declaredParametersOf(documented.get()))
                    .as(sent.name() + ": the parameters that " + documented.get() + " declares")
                    .containsAll(sent.parameters().keySet());
        }
    }

    @Test
    @DisplayName("CA-28.4: the collection declares the Bearer token once, only the logins and the list that shows the 401 go without it, and no request writes the Authorization header")
    void collectionDeclaresTheBearerTokenOnce() {
        JsonNode common = collection.path("auth");
        assertThat(common.path("type").asString()).isEqualTo("bearer");
        assertThat(keysAndValues(common.path("bearer"))).containsEntry("token", "{{token}}");

        // Once: a folder that declared its own would replace the one of the collection.
        assertThat(folders).noneMatch(folder -> folder.has("auth"));

        List<Sent> withTheirOwn = requests.stream().filter(sent -> sent.request().has("auth")).toList();
        for (Sent sent : withTheirOwn) {
            assertThat(sent.request().path("auth").path("type").asString()).as(sent.name()).isEqualTo("noauth");
        }

        List<Sent> logins = requests.stream().filter(sent -> sent.is(LOGIN)).toList();
        assertThat(logins).isNotEmpty();
        assertThat(withTheirOwn).containsAll(logins);

        // Besides the logins, one request: a GET to a list, the one that shows the 401.
        List<Sent> others = withTheirOwn.stream().filter(sent -> !sent.is(LOGIN)).toList();
        assertThat(others).hasSize(1);
        assertThat(LISTS).contains(others.getFirst().method() + " " + others.getFirst().route());

        // A header written by hand would win over the token of the collection, or repeat it.
        for (Sent sent : requests) {
            assertThat(sent.headers().keySet()).as(sent.name())
                    .noneMatch(header -> header.equalsIgnoreCase("Authorization"));
        }
    }

    @Test
    @DisplayName("CA-28.5: the logins are two, demo and admin with the published test credentials, and each one has a script that saves the accessToken in the variable token")
    void loginsUseTheTestCredentialsAndSaveTheToken() {
        List<Sent> logins = requests.stream().filter(sent -> sent.is(LOGIN)).toList();

        assertThat(logins.stream().map(Sent::body).toList()).containsExactlyInAnyOrder(
                JSON.readTree("{\"username\": \"demo\", \"password\": \"demo1234\"}"),
                JSON.readTree("{\"username\": \"admin\", \"password\": \"admin1234\"}"));

        for (Sent login : logins) {
            assertThat(login.scriptAfterTheAnswer()).as(login.name()).contains(SAVES_THE_TOKEN);
        }
    }

    @Test
    @DisplayName("CA-28.6: the first request is the login of demo, and there is a POST /api/v1/users before the login of admin, the 403, and another after it, the creation")
    void requestsAreInTheOrderOfTheFlow() {
        assertThat(requests).isNotEmpty();
        Sent first = requests.getFirst();
        assertThat(first.is(LOGIN)).as("the first request is a login: " + first.name()).isTrue();
        assertThat(first.body().path("username").asString()).isEqualTo("demo");

        List<Sent> loginsOfAdmin = requests.stream()
                .filter(sent -> sent.is(LOGIN) && sent.body().path("username").asString().equals("admin"))
                .toList();
        assertThat(loginsOfAdmin).hasSize(1);
        int loginOfAdmin = requests.indexOf(loginsOfAdmin.getFirst());

        // There is one variable for the token: what comes before this login goes as demo.
        assertThat(requests.subList(0, loginOfAdmin)).as("before the login of admin")
                .anyMatch(sent -> sent.is(USER_CREATION));
        assertThat(requests.subList(loginOfAdmin + 1, requests.size())).as("after the login of admin")
                .anyMatch(sent -> sent.is(USER_CREATION));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"people", "films", "starships", "vehicles"})
    @DisplayName("CA-28.7: each entity has a list with page and size, one with id, one with name, one with expanded=true and a detail")
    void eachEntityHasItsFiveRequests(String entity) {
        String list = "GET /api/v1/" + entity;
        List<Map<String, String>> lists = requests.stream()
                .filter(sent -> sent.is(list))
                .map(Sent::parameters)
                .toList();

        assertThat(lists).as("a list with page and size")
                .anyMatch(parameters -> parameters.containsKey("page") && parameters.containsKey("size"));
        assertThat(lists).as("a list with id").anyMatch(parameters -> parameters.containsKey("id"));
        assertThat(lists).as("a list with name").anyMatch(parameters -> parameters.containsKey("name"));
        assertThat(lists).as("a list with expanded=true")
                .anyMatch(parameters -> "true".equals(parameters.get("expanded")));

        assertThat(requests).as("a detail")
                .anyMatch(sent -> sent.method().equals("GET") && sent.route().matches("/api/v1/" + entity + "/[^/]+"));
    }

    @Test
    @DisplayName("CA-28.8: every body is valid JSON and is sent with Content-Type: application/json")
    void everyBodyIsValidJsonAndIsSentAsJson() {
        List<Sent> withBody = requests.stream().filter(sent -> sent.request().has("body")).toList();
        // The two logins and the two requests to create a user, at least.
        assertThat(withBody).hasSizeGreaterThanOrEqualTo(4);

        for (Sent sent : withBody) {
            assertThat(sent.request().path("body").path("mode").asString()).as(sent.name()).isEqualTo("raw");
            // Reading it fails if it is not valid JSON.
            assertThat(sent.body().isObject()).as(sent.name()).isTrue();

            Map<String, String> headers = new LinkedHashMap<>();
            sent.headers().forEach((name, value) -> headers.put(name.toLowerCase(Locale.ROOT), value));
            assertThat(headers).as(sent.name()).containsEntry("content-type", "application/json");
        }
    }

    @Test
    @DisplayName("Every request with POST carries a body: none is sent empty")
    void everyPostCarriesABody() {
        for (Sent sent : requests) {
            if (sent.method().equals("POST")) {
                assertThat(sent.request().has("body")).as(sent.name()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("Each address is written whole and in parts, and both say the same: a tool that reads either one sends the same request")
    void partsOfEachAddressSayTheSameAsTheWholeAddress() {
        for (Sent sent : requests) {
            JsonNode url = sent.request().path("url");

            StringBuilder fromItsParts = new StringBuilder();
            for (JsonNode part : url.path("host")) {
                fromItsParts.append(fromItsParts.isEmpty() ? "" : ".").append(part.asString());
            }
            for (JsonNode part : url.path("path")) {
                fromItsParts.append('/').append(part.asString());
            }
            String separator = "?";
            for (JsonNode parameter : url.path("query")) {
                fromItsParts.append(separator)
                        .append(parameter.path("key").asString())
                        .append('=')
                        .append(parameter.path("value").asString());
                separator = "&";
            }

            assertThat(fromItsParts.toString()).as(sent.name()).isEqualTo(sent.address());
        }
    }

    @Test
    @DisplayName("Every request and every folder has a name of its own: it is what a tool shows in its list")
    void everyRequestAndEveryFolderHasANameOfItsOwn() {
        List<String> names = new ArrayList<>(requests.stream().map(Sent::name).toList());
        folders.forEach(folder -> names.add(folder.path("name").asString()));

        assertThat(names).noneMatch(String::isBlank);
        assertThat(names).doesNotHaveDuplicates();
    }

    /**
     * One request of the collection, as the element that the file has for it: the name that a
     * tool shows it by, what it sends and, next to that, its scripts.
     */
    private record Sent(JsonNode element) {

        String name() {
            return element.path("name").asString();
        }

        JsonNode request() {
            return element.path("request");
        }

        String method() {
            return request().path("method").asString();
        }

        /** The address as it is written, with the variable of the server and the parameters. */
        String address() {
            return request().path("url").path("raw").asString();
        }

        /** The address without the variable of the server and without the parameters. */
        String route() {
            String withoutServer = address().startsWith(BASE_URL) ? address().substring(BASE_URL.length()) : address();
            int parameters = withoutServer.indexOf('?');
            return parameters < 0 ? withoutServer : withoutServer.substring(0, parameters);
        }

        /** The parameters that the address sends, by name. */
        Map<String, String> parameters() {
            Map<String, String> parameters = new LinkedHashMap<>();
            int start = address().indexOf('?');
            if (start < 0) {
                return parameters;
            }
            for (String parameter : address().substring(start + 1).split("&")) {
                int equals = parameter.indexOf('=');
                if (equals < 0) {
                    parameters.put(parameter, "");
                } else {
                    parameters.put(parameter.substring(0, equals), parameter.substring(equals + 1));
                }
            }
            return parameters;
        }

        /** Whether it is that request, written as its method and its route, without placeholders. */
        boolean is(String methodAndRoute) {
            return (method() + " " + route()).equals(methodAndRoute);
        }

        /** The headers that the request writes by itself, by name. */
        Map<String, String> headers() {
            return keysAndValues(request().path("header"));
        }

        /** The body, read as JSON. It fails if the request has none or if it is not valid JSON. */
        JsonNode body() {
            return JSON.readTree(request().path("body").path("raw").stringValue());
        }

        /** The text of the script that a tool runs when the answer arrives, or nothing. */
        String scriptAfterTheAnswer() {
            StringBuilder script = new StringBuilder();
            for (JsonNode event : element.path("event")) {
                if (event.path("listen").asString().equals("test")) {
                    for (JsonNode line : event.path("script").path("exec")) {
                        script.append(line.asString()).append('\n');
                    }
                }
            }
            return script.toString();
        }

    }

    /**
     * Walks the elements of the collection in order. An element with elements of its own is a
     * folder, and a tool runs what is inside it before going on with the next element.
     */
    private void collect(JsonNode items) {
        for (JsonNode item : items) {
            if (item.has("item")) {
                folders.add(item);
                collect(item.path("item"));
            } else {
                requests.add(new Sent(item));
            }
        }
    }

    /** A list of pairs of the collection, each with its {@code key} and its {@code value}, as a map. */
    private static Map<String, String> keysAndValues(JsonNode pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (JsonNode pair : pairs) {
            values.put(pair.path("key").asString(), pair.path("value").asString());
        }
        return values;
    }

    /** Every request of the documentation, as its method and its route. */
    private List<String> documentedRequests() {
        List<String> documented = new ArrayList<>();
        documentation.path("paths").properties().forEach(path -> path.getValue().propertyNames()
                .forEach(method -> documented.add(method.toUpperCase(Locale.ROOT) + " " + path.getKey())));
        return documented;
    }

    /**
     * The documented request that a request of the collection is, if there is one. The route as
     * it is written goes first; if no request has it, a part of the route can take the place of
     * a placeholder: {@code /api/v1/people/1} is {@code /api/v1/people/{id}}.
     */
    private Optional<String> documentedRequestOf(Sent sent) {
        String written = sent.method() + " " + sent.route();
        List<String> documented = documentedRequests();
        if (documented.contains(written)) {
            return Optional.of(written);
        }
        return documented.stream().filter(request -> takesThePlaceOf(written, request)).findFirst();
    }

    private static boolean takesThePlaceOf(String written, String documented) {
        String[] parts = written.split("/");
        String[] expected = documented.split("/");
        if (parts.length != expected.length) {
            return false;
        }
        for (int i = 0; i < parts.length; i++) {
            boolean placeholder = expected[i].startsWith("{") && expected[i].endsWith("}");
            if (!placeholder && !expected[i].equals(parts[i])) {
                return false;
            }
        }
        return true;
    }

    /** The names of the parameters that a documented request declares for its address. */
    private List<String> declaredParametersOf(String documented) {
        String method = documented.substring(0, documented.indexOf(' ')).toLowerCase(Locale.ROOT);
        String path = documented.substring(documented.indexOf(' ') + 1);

        List<String> names = new ArrayList<>();
        for (JsonNode parameter : documentation.path("paths").path(path).path(method).path("parameters")) {
            if (parameter.path("in").asString().equals("query")) {
                names.add(parameter.path("name").asString());
            }
        }
        return names;
    }

}
