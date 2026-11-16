package io.github.erickvaernet.starwars.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checks how the errors that are not about the session are answered: an invalid body, an invalid
 * parameter, a failure of the data source and a route that does not exist.
 *
 * <p>Only the web layer is loaded, with the real error format and without the access rules,
 * which have their own tests. The requests that fail exist only here.
 */
@WebMvcTest(ApiExceptionHandlerTest.FailingRequests.class)
@Import(ApiExceptionHandlerTest.FailingRequests.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiExceptionHandlerTest {

    /** A text that only the cause of the failure has, to check that it does not get out. */
    private static final String INTERNAL_TEXT = "http://internal-host:1234/api/people";

    private static final String BODY = "/test/body";

    /** The texts of the three fields of the body that exists only here, one for each field. */
    private static final String NAME_RULE = "must not be blank, and must have at most 5 characters";

    private static final String CODE_RULE = "must have lowercase letters only";

    private static final String ALIAS_RULE = "must not be blank";

    /** A body that follows the rules of its three fields. */
    private static final Map<String, String> VALID_BODY = Map.of("name", "luke", "code", "abc", "alias", "red-five");

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc).build();
    }

    @Test
    @DisplayName("An invalid parameter is answered with 400, and the detail says which one and what it must be")
    void invalidParameterIsAnsweredWithBadRequest() {
        String body = expectProblem("/test/invalid-parameter", 400, "Bad Request");

        assertThat(detailOf(body)).isEqualTo("Parameter 'size' must be an integer between 1 and 100.");
    }

    @Test
    @DisplayName("A failure of the data source is answered with 502 and a fixed text")
    void failureOfTheDataSourceIsAnsweredWithBadGateway() {
        String body = expectProblem("/test/bad-gateway", 502, "Bad Gateway");

        assertThat(detailOf(body)).isEqualTo("The data source failed or returned an unexpected response.");
    }

    @Test
    @DisplayName("A data source that does not answer in time is answered with 504 and a fixed text")
    void slowDataSourceIsAnsweredWithGatewayTimeout() {
        String body = expectProblem("/test/gateway-timeout", 504, "Gateway Timeout");

        assertThat(detailOf(body)).isEqualTo("The data source did not respond in time.");
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {"/api/v1/no-such-route", "/api/v1/people", "/no-such-file.html"})
    @DisplayName("A route that does not exist is answered with 404 and a fixed text, not the one Spring writes")
    void routeThatDoesNotExistIsAnsweredWithNotFound(String path) {
        String body = expectProblem(path, 404, "Not Found");

        assertThat(detailOf(body)).isEqualTo("The requested resource was not found.");
        // Spring would write "No static resource api/v1/people.", which names an internal detail.
        assertThat(body).doesNotContain("static resource");
    }

    @Test
    @DisplayName("An element that does not exist is answered with 404 and the same text as a route that does not exist")
    void elementThatDoesNotExistIsAnsweredWithNotFound() {
        String body = expectProblem("/test/not-found", 404, "Not Found");

        assertThat(detailOf(body)).isEqualTo("The requested resource was not found.");
    }

    @Test
    @DisplayName("A request that the user is not allowed to make is answered with 403 and a fixed text, without saying how to authenticate")
    void requestThatIsNotAllowedIsAnsweredWithForbidden() {
        client.get().uri("/test/forbidden")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                // Whoever asked is already authenticated: doing it again changes nothing.
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Forbidden",
                          "status": 403,
                          "detail": "You do not have permission to perform this request.",
                          "instance": "/test/forbidden"
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("A user name that is already taken is answered with 409 and a fixed text")
    void userNameThatIsTakenIsAnsweredWithConflict() {
        client.get().uri("/test/username-taken")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Conflict",
                          "status": 409,
                          "detail": "A user with that username already exists.",
                          "instance": "/test/username-taken"
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("A login from an address with too many failed attempts is answered with 429 and a fixed text, and Retry-After says how many seconds to wait")
    void tooManyLoginAttemptsAreAnsweredWithTooManyRequests() {
        client.get().uri("/test/too-many-login-attempts")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "847")
                // Unlike a 401, it does not say how to authenticate: doing it again changes nothing.
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Too Many Requests",
                          "status": 429,
                          "detail": "Too many failed login attempts. Try again later.",
                          "instance": "/test/too-many-login-attempts"
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("A method that a route does not have is answered with 405 and a fixed text, and the answer names the ones it has")
    void methodThatARouteDoesNotHaveIsAnsweredWithMethodNotAllowed() {
        client.post().uri("/test/not-found")
                .exchange()
                .expectStatus().isEqualTo(405)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals(HttpHeaders.ALLOW, "GET")
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Method Not Allowed")
                .jsonPath("$.status").isEqualTo(405)
                // Spring would write "Method 'POST' is not supported.", with what the client sent.
                .jsonPath("$.detail").isEqualTo("The method of the request is not supported.")
                .jsonPath("$.instance").isEqualTo("/test/not-found");
    }

    @Test
    @DisplayName("A parameter that is not correctly encoded is answered with 400 and a fixed text")
    void parameterThatIsNotCorrectlyEncodedIsAnsweredWithBadRequest() {
        String body = expectProblem("/test/undecodable-parameter", 400, "Bad Request");

        assertThat(detailOf(body)).isEqualTo("The request has a parameter that is not correctly encoded.");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("A parameter that is not correctly encoded leaves in the log neither a stack trace nor what the client sent")
    void parameterThatIsNotCorrectlyEncodedLeavesNoTraceInTheLog(CapturedOutput log) {
        expectProblem("/test/undecodable-parameter", 400, "Bad Request");

        // It is a mistake of the client, like any other invalid parameter: not an event to
        // investigate, and its value does not belong in the log.
        assertThat(log.getAll()).doesNotContain("InvalidParameterException").doesNotContain(INTERNAL_TEXT);
    }

    @Test
    @DisplayName("A body that follows the rules of its fields is not an error")
    void validBodyIsAccepted() {
        post(VALID_BODY).expectStatus().isOk();
    }

    @Test
    @DisplayName("A body with one invalid field is answered with 400, and errors has that field with its text")
    void bodyWithOneInvalidFieldSaysWhichOne() {
        post(with("code", "ABC"))
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                // Strict: the fields of every error and errors, and in errors that element only.
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Invalid request content.",
                          "instance": "/test/body",
                          "errors": [
                            {"field": "code", "message": "must have lowercase letters only"}
                          ]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("A body with several invalid fields has them all in errors, sorted by the name of the field")
    void bodyWithSeveralInvalidFieldsHasThemAllSortedByName() {
        // The body declares its fields as name, code and alias: the order is not that one.
        post(Map.of("name", "", "code", "ABC", "alias", ""))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Invalid request content.")
                .jsonPath("$.errors").value(List.class, errors -> assertThat(errors).containsExactly(
                        Map.of("field", "alias", "message", ALIAS_RULE),
                        Map.of("field", "code", "message", CODE_RULE),
                        Map.of("field", "name", "message", NAME_RULE)));
    }

    @Test
    @DisplayName("A body without any of its fields has them all in errors")
    void bodyWithoutItsFieldsHasThemAll() {
        post(Map.of())
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[*].field").value(List.class,
                        fields -> assertThat(fields).containsExactly("alias", "code", "name"));
    }

    @Test
    @DisplayName("A field that breaks two of its rules at once is in errors only once")
    void fieldThatBreaksTwoRulesIsInErrorsOnlyOnce() {
        // Six spaces: blank, and longer than five characters.
        post(with("name", " ".repeat(6)))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors").value(List.class, errors -> assertThat(errors)
                        .containsExactly(Map.of("field", "name", "message", NAME_RULE)));
    }

    @Test
    @DisplayName("The answer to an invalid body never repeats the value that was received")
    void invalidBodyDoesNotRepeatTheValueThatWasReceived() {
        byte[] bytes = post(Map.of("name", INTERNAL_TEXT, "code", "ZX-81-ZX", "alias", "red-five"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(2)
                .returnResult().getResponseBody();

        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .doesNotContain(INTERNAL_TEXT).doesNotContain("ZX-81").doesNotContain("red-five");
    }

    @Test
    @DisplayName("A body that cannot be read is answered with 400 and has no errors: no field failed")
    void bodyThatCannotBeReadHasNoErrors() {
        byte[] bytes = client.post().uri(BODY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("this is not JSON")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().returnResult().getResponseBody();

        assertThat(fieldsOf(bytes)).containsOnlyKeys("type", "title", "status", "detail", "instance");
    }

    @Test
    @DisplayName("An invalid parameter has errors with one element: the parameter and the rule it must follow")
    void invalidParameterHasErrorsWithThatParameter() {
        client.get().uri("/test/invalid-parameter")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Parameter 'size' must be an integer between 1 and 100.",
                          "instance": "/test/invalid-parameter",
                          "errors": [
                            {"field": "size", "message": "must be an integer between 1 and 100"}
                          ]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/test/undecodable-parameter",
        "/test/not-found",
        "/test/bad-gateway",
        "/test/gateway-timeout",
        "/api/v1/no-such-route"
    })
    @DisplayName("An error that does not come from a field or a parameter has no errors")
    void errorThatDoesNotComeFromAFieldHasNoErrors(String path) {
        byte[] bytes = client.get().uri(path).exchange().expectBody().returnResult().getResponseBody();

        assertThat(fieldsOf(bytes)).containsOnlyKeys("type", "title", "status", "detail", "instance");
    }

    @ParameterizedTest(name = "Content-Type: {0}")
    @ValueSource(strings = {"text/plain", "application/xml", "application/x-www-form-urlencoded"})
    @DisplayName("A body sent as something other than JSON is answered with 415 and a fixed text, which does not repeat the content type")
    void bodyThatIsNotSentAsJsonIsAnsweredWithUnsupportedMediaType(String contentType) {
        byte[] bytes = client.post().uri(BODY)
                .contentType(MediaType.parseMediaType(contentType))
                .body("name=luke")
                .exchange()
                .expectStatus().isEqualTo(415)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Unsupported Media Type",
                          "status": 415,
                          "detail": "The content type of the request is not supported.",
                          "instance": "/test/body"
                        }
                        """, JsonCompareMode.STRICT)
                .returnResult().getResponseBody();

        // Spring would write "Content-Type 'text/plain' is not supported.", with what the client sent.
        assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotContain(contentType);
    }

    @Test
    @DisplayName("The 415 still names, in a header, the content types that the request does accept")
    void unsupportedMediaTypeNamesTheContentTypesThatAreAccepted() {
        client.post().uri(BODY)
                .contentType(MediaType.TEXT_PLAIN)
                .body("name=luke")
                .exchange()
                .expectStatus().isEqualTo(415)
                // Written by the server, not by the client: it is what HTTP suggests for a 415.
                .expectHeader().value(HttpHeaders.ACCEPT, accepted -> assertThat(accepted)
                        .contains(MediaType.APPLICATION_JSON_VALUE)
                        .doesNotContain(MediaType.TEXT_PLAIN_VALUE));
    }

    private RestTestClient.ResponseSpec post(Map<String, String> body) {
        return client.post().uri(BODY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    /** The valid body, with one of its fields changed. */
    private static Map<String, String> with(String field, String value) {
        Map<String, String> body = new HashMap<>(VALID_BODY);
        body.put(field, value);
        return body;
    }

    private static Map<String, Object> fieldsOf(byte[] body) {
        return JsonPath.read(new String(body, StandardCharsets.UTF_8), "$");
    }

    /**
     * Checks an error in Problem Details format that shows nothing of its cause, and returns its
     * body.
     */
    private String expectProblem(String path, int status, String title) {
        byte[] bytes = client.get().uri(path)
                .exchange()
                .expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo(title)
                .jsonPath("$.status").isEqualTo(status)
                .jsonPath("$.instance").isEqualTo(path)
                .returnResult().getResponseBody();
        String body = new String(bytes, StandardCharsets.UTF_8);

        assertThat(body).doesNotContain(INTERNAL_TEXT).doesNotContain("Exception");
        return body;
    }

    private static String detailOf(String body) {
        return JsonPath.read(body, "$.detail");
    }

    /** Requests that fail the way the real ones can. */
    @RestController
    static class FailingRequests {

        @GetMapping("/test/invalid-parameter")
        String invalidParameter() {
            throw new InvalidParameterException("size", "must be an integer between 1 and 100");
        }

        @GetMapping("/test/bad-gateway")
        String badGateway() {
            throw new BadGatewayException("GET " + INTERNAL_TEXT + " answered 500", new IOException(INTERNAL_TEXT));
        }

        @GetMapping("/test/gateway-timeout")
        String gatewayTimeout() {
            throw new GatewayTimeoutException("GET " + INTERNAL_TEXT + " timed out", new IOException(INTERNAL_TEXT));
        }

        @GetMapping("/test/not-found")
        String notFound() {
            throw new NotFoundException();
        }

        @GetMapping("/test/forbidden")
        String forbidden() {
            throw new AccessDeniedException("the token has the role USER and the request needs ADMIN");
        }

        @GetMapping("/test/username-taken")
        String usernameTaken() {
            throw new UsernameAlreadyExistsException();
        }

        @GetMapping("/test/too-many-login-attempts")
        String tooManyLoginAttempts() {
            throw new TooManyLoginAttemptsException(847);
        }

        @PostMapping("/test/body")
        String body(@Valid @RequestBody SampleBody body) {
            return "accepted";
        }

        @GetMapping("/test/undecodable-parameter")
        String undecodableParameter() {
            // What Tomcat throws when a parameter is read and its bytes are not valid text. Its
            // message repeats what the client sent.
            throw new org.apache.tomcat.util.http.InvalidParameterException(
                    "Character decoding failed. Parameter [name] with value [" + INTERNAL_TEXT + "] has been ignored.");
        }

    }

    /**
     * A body with rules, the way a real one declares them: every rule of a field carries the
     * text of that field. The name has two rules, so that both can fail at once.
     */
    record SampleBody(
            @NotBlank(message = NAME_RULE) @Size(max = 5, message = NAME_RULE) String name,
            @NotNull(message = CODE_RULE) @Pattern(regexp = "[a-z]+", message = CODE_RULE) String code,
            @NotBlank(message = ALIAS_RULE) String alias) {
    }

}
