package io.github.erickvaernet.starwars;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

/**
 * Acceptance tests for the requests that are rejected before they reach a controller: a parameter
 * that cannot be decoded, an address that the server does not accept and a method that the server
 * does not take.
 *
 * <p>The specification asks that every error is answered as Problem Details, with the status of
 * what went wrong (section 5). These requests are the ones that leave the usual path: the web
 * server or the access rules stop them, and the error is answered apart from the controllers. A
 * valid token is never the problem in any of them, so none may be answered with 401 when the
 * request brings one.
 *
 * <p>The application runs on a real HTTP port, because none of this happens without a real web
 * server. The requests are sent with the HTTP client of Java and their address is written out in
 * full, so that nothing encodes or tidies it on the way.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class RejectedRequestAcceptanceTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    private String token;

    @BeforeEach
    void setUp() {
        token = TestSession.login(RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build());
    }

    // A "%" alone, as in "name=100%", fails the same way, but the HTTP client of Java refuses to
    // send it. These are well-formed escapes of bytes that are not UTF-8 text.
    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/api/v1/people?name=%C3",
        "/api/v1/films?name=%FF%FE",
        "/api/v1/starships?page=%C3",
        // A parameter that the application ignores is read with the others.
        "/api/v1/vehicles?not-a-parameter=%C3"
    })
    @DisplayName("A parameter that is not correctly encoded is answered with 400, not with 500")
    void parameterThatIsNotCorrectlyEncodedIsABadRequest(String address) throws Exception {
        HttpResponse<String> response = get(address, token);

        expectProblem(response, 400, "Bad Request", pathOf(address));
        assertThat(detailOf(response)).isEqualTo("The request has a parameter that is not correctly encoded.");
    }

    @Test
    @DisplayName("The health check answers 400 to a parameter that is not correctly encoded, without a session")
    void healthCheckAnswersBadRequestToAParameterThatIsNotCorrectlyEncoded() throws Exception {
        HttpResponse<String> response = get("/actuator/health?x=%C3", null);

        expectProblem(response, 400, "Bad Request", "/actuator/health");
        assertThat(detailOf(response)).isEqualTo("The request has a parameter that is not correctly encoded.");
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/api/v1/people/1;x=1",
        "//api/v1/people/1",
        "/api/v1/people/../films/1",
        "/api/v1/people/1%3Bx",
        "/api/v1/people/%252F",
        // An encoded line break.
        "/api/v1/people/1%0D%0A"
    })
    @DisplayName("An address that the server does not accept is answered with 400, also with a valid token")
    void addressThatIsNotAcceptedIsABadRequest(String address) throws Exception {
        HttpResponse<String> response = get(address, token);

        // The address that was asked for, not the internal one that answers the error.
        expectProblem(response, 400, "Bad Request", address);
        assertThat(detailOf(response)).isEqualTo("The request was rejected.");
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {"/api/v1/people/1;x=1", "//api/v1/people/1", "/api/v1/people/../films/1"})
    @DisplayName("An address that the server does not accept is answered with 400 without a session too")
    void addressThatIsNotAcceptedIsABadRequestWithoutASession(String address) throws Exception {
        HttpResponse<String> response = get(address, null);

        expectProblem(response, 400, "Bad Request", address);
    }

    @Test
    @DisplayName("A method that the web server has switched off is answered with 405, not with 401")
    void methodThatIsSwitchedOffIsNotAllowed() throws Exception {
        HttpResponse<String> response = send("TRACE", "/api/v1/people", token);

        expectProblem(response, 405, "Method Not Allowed", "/api/v1/people");
        assertThat(detailOf(response)).isEqualTo("The method of the request is not supported.");
    }

    @Test
    @DisplayName("A method that the web server has switched off is answered with 405 without a session too")
    void methodThatIsSwitchedOffIsNotAllowedWithoutASession() throws Exception {
        // The web server answers it before the access rules see the request. It tells nothing
        // about the route: the method is switched off for every one.
        HttpResponse<String> response = send("TRACE", "/api/v1/people", null);

        expectProblem(response, 405, "Method Not Allowed", "/api/v1/people");
    }

    @Test
    @DisplayName("A method that is not one of HTTP is a rejected request like any other: 400, not 401")
    void methodThatIsNotOneOfHttpIsABadRequest() throws Exception {
        HttpResponse<String> response = send("FOO", "/api/v1/people", token);

        expectProblem(response, 400, "Bad Request", "/api/v1/people");
        assertThat(detailOf(response)).isEqualTo("The request was rejected.");
    }

    @Test
    @DisplayName("A method that is not one of HTTP is answered with 400 without a session too")
    void methodThatIsNotOneOfHttpIsABadRequestWithoutASession() throws Exception {
        HttpResponse<String> response = send("FOO", "/api/v1/people", null);

        expectProblem(response, 400, "Bad Request", "/api/v1/people");
    }

    @Test
    @DisplayName("The internal address that answers the errors is, for a client, a route that does not exist")
    void internalErrorAddressIsNotARoute() throws Exception {
        HttpResponse<String> response = get("/error", token);

        expectProblem(response, 404, "Not Found", "/error");
        assertThat(detailOf(response)).isEqualTo("The requested resource was not found.");
    }

    @Test
    @DisplayName("Without a session, the internal address that answers the errors is closed like any other")
    void internalErrorAddressNeedsASession() throws Exception {
        HttpResponse<String> response = get("/error", null);

        expectProblem(response, 401, "Unauthorized", "/error");
    }

    private HttpResponse<String> get(String address, String bearerToken) throws IOException, InterruptedException {
        return send("GET", address, bearerToken);
    }

    private HttpResponse<String> send(String method, String address, String bearerToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + address))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Checks an error in Problem Details format that shows nothing internal. */
    private static void expectProblem(HttpResponse<String> response, int status, String title, String instance) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                contentType -> assertThat(contentType).startsWith(PROBLEM_JSON));

        String body = response.body();
        assertThat(JsonPath.<String>read(body, "$.type")).isEqualTo("about:blank");
        assertThat(JsonPath.<String>read(body, "$.title")).isEqualTo(title);
        assertThat(JsonPath.<Integer>read(body, "$.status")).isEqualTo(status);
        assertThat(JsonPath.<String>read(body, "$.instance")).isEqualTo(instance);
        assertThat(body).doesNotContain("Exception").doesNotContain("timestamp");
    }

    private static String detailOf(HttpResponse<String> response) {
        return JsonPath.read(response.body(), "$.detail");
    }

    private static String pathOf(String address) {
        return address.substring(0, address.indexOf('?'));
    }

}
