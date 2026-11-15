package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Checks the login request: which bodies are accepted, how each outcome is answered and how the
 * request is documented.
 *
 * <p>Only the web layer is loaded, with the real access rules and error format and the generated
 * API documentation; the login rules are replaced. It needs neither the database nor Docker.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfiguration.class)
// The API documentation is not part of what a test of the web layer loads by default.
@ImportAutoConfiguration({
    SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
class AuthControllerTest {

    private static final String LOGIN = "/api/v1/auth/login";

    /** The text of each field in the list of errors, as the specification writes it. */
    private static final String USERNAME_RULE =
            "must not be blank, and must have at most 50 characters and no control characters";

    private static final String PASSWORD_RULE = "must not be blank, and must have at most 72 characters";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginService loginService;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc).build();
    }

    @Test
    @DisplayName("A successful login answers 200 with the token, its type and its lifetime")
    void successfulLoginReturnsTheToken() {
        when(loginService.login("demo", "demo1234")).thenReturn(new LoginResponse("the-token", "Bearer", 3600));

        login("""
                {"username": "demo", "password": "demo1234"}
                """)
                .expectStatus().isOk()
                .expectBody().json("""
                        {"accessToken": "the-token", "tokenType": "Bearer", "expiresIn": 3600}
                        """);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "{\"password\": \"demo1234\"}",
        "{\"username\": \"demo\"}",
        "{\"username\": \"\", \"password\": \"demo1234\"}",
        "{\"username\": \"demo\", \"password\": \"\"}",
        "{\"username\": \"   \", \"password\": \"demo1234\"}",
        "{\"username\": \"demo\", \"password\": \"   \"}",
        "{\"username\": null, \"password\": null}",
        "{}",
        "",
        "this is not JSON"
    })
    @DisplayName("A body without a user name or a password is answered with 400, before any credential is checked")
    void incompleteBodyIsRejected(String body) {
        login(body)
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.title").isEqualTo("Bad Request")
                .jsonPath("$.instance").isEqualTo(LOGIN);

        verifyNoInteractions(loginService);
    }

    @Test
    @DisplayName("A user name longer than the column that stores it is answered with 400, before any credential is checked")
    void userNameThatIsTooLongIsRejected() {
        expectInvalidBody(loginAs("u".repeat(51), "demo1234"));
    }

    @Test
    @DisplayName("A password longer than what BCrypt reads is answered with 400, before any credential is checked")
    void passwordThatIsTooLongIsRejected() {
        expectInvalidBody(loginAs("demo", "p".repeat(73)));
    }

    @ParameterizedTest(name = "user name with the character U+{0}")
    @ValueSource(strings = {"0000", "0007", "000A", "001F", "007F", "0085"})
    @DisplayName("A user name with a control character is answered with 400: the database cannot even look it up")
    void userNameWithAControlCharacterIsRejected(String codePoint) {
        String control = Character.toString(Integer.parseInt(codePoint, 16));

        expectInvalidBody(loginAs("de" + control + "mo", "demo1234"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUserNames")
    @DisplayName("An invalid user name is in errors once, with the same text whichever of its rules it breaks")
    void invalidUserNameHasTheSameTextWhicheverRuleItBreaks(String what, String body) {
        login(body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Invalid request content.")
                .jsonPath("$.errors").value(List.class, errors -> assertThat(errors)
                        .containsExactly(Map.of("field", "username", "message", USERNAME_RULE)));

        verifyNoInteractions(loginService);
    }

    /** One case for each rule of the user name, and two that break more than one rule at once. */
    static Stream<Arguments> invalidUserNames() {
        return Stream.of(
                Arguments.of("missing", "{\"password\": \"demo1234\"}"),
                Arguments.of("null", "{\"username\": null, \"password\": \"demo1234\"}"),
                Arguments.of("empty", withUserName("")),
                Arguments.of("only spaces", withUserName("   ")),
                Arguments.of("51 characters", withUserName("u".repeat(51))),
                // Written as JSON writes a control character: a backslash, "u" and its code.
                Arguments.of("a control character", withUserName("de\\u0007mo")),
                Arguments.of("51 characters, one of them a control character",
                        withUserName("u".repeat(50) + "\\u0007")),
                Arguments.of("51 spaces", withUserName(" ".repeat(51))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPasswords")
    @DisplayName("An invalid password is in errors once, with the same text whichever of its rules it breaks")
    void invalidPasswordHasTheSameTextWhicheverRuleItBreaks(String what, String body) {
        login(body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Invalid request content.")
                .jsonPath("$.errors").value(List.class, errors -> assertThat(errors)
                        .containsExactly(Map.of("field", "password", "message", PASSWORD_RULE)));

        verifyNoInteractions(loginService);
    }

    /** One case for each rule of the password, and one that breaks two rules at once. */
    static Stream<Arguments> invalidPasswords() {
        return Stream.of(
                Arguments.of("missing", "{\"username\": \"demo\"}"),
                Arguments.of("null", "{\"username\": \"demo\", \"password\": null}"),
                Arguments.of("empty", withPassword("")),
                Arguments.of("only spaces", withPassword("   ")),
                Arguments.of("73 characters", withPassword("p".repeat(73))),
                Arguments.of("73 spaces", withPassword(" ".repeat(73))));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "{}",
        "{\"username\": null, \"password\": null}",
        "{\"username\": \"\", \"password\": \"\"}"
    })
    @DisplayName("A body with an invalid user name and an invalid password has both in errors: password first, then username")
    void invalidUserNameAndPasswordAreBothInErrors(String body) {
        login(body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors").value(List.class, errors -> assertThat(errors).containsExactly(
                        Map.of("field", "password", "message", PASSWORD_RULE),
                        Map.of("field", "username", "message", USERNAME_RULE)));
    }

    @Test
    @DisplayName("The answer to an invalid body repeats neither the user name nor the password that were received")
    void invalidBodyRepeatsNothingOfWhatWasReceived() {
        String username = "x9".repeat(26);
        String password = "k4".repeat(37);

        String body = loginAs(username, password)
                .expectStatus().isBadRequest()
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).doesNotContain("x9x9").doesNotContain("k4k4");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "this is not JSON", "[]", "{\"username\": {}, \"password\": \"demo1234\"}"})
    @DisplayName("A body that cannot be read as a login has no errors: no field was checked")
    void bodyThatCannotBeReadHasNoErrors(String body) {
        String answer = login(body)
                .expectStatus().isBadRequest()
                .expectBody(String.class).returnResult().getResponseBody();
        Map<String, Object> problem = JsonPath.read(answer, "$");

        assertThat(problem).containsOnlyKeys("type", "title", "status", "detail", "instance");
    }

    @Test
    @DisplayName("A user name and a password of the longest size accepted reach the login rules")
    void longestUserNameAndPasswordAreAccepted() {
        String username = "u".repeat(50);
        String password = "p".repeat(72);
        when(loginService.login(username, password)).thenReturn(new LoginResponse("the-token", "Bearer", 3600));

        loginAs(username, password).expectStatus().isOk();
    }

    @Test
    @DisplayName("A rejected login is answered with 401, and the answer says nothing about the cause")
    void rejectedLoginIsAnsweredWith401() {
        when(loginService.login(anyString(), anyString()))
                .thenThrow(new BadCredentialsException("internal message"));

        login("""
                {"username": "demo", "password": "not-the-password"}
                """)
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.title").isEqualTo("Unauthorized")
                .jsonPath("$.instance").isEqualTo(LOGIN)
                .jsonPath("$.detail").value(String.class, detail -> assertThat(detail).doesNotContain("internal"));
    }

    @Test
    @DisplayName("A rejected login names the way to authenticate, as HTTP requires of every 401")
    void rejectedLoginNamesTheWayToAuthenticate() {
        when(loginService.login(anyString(), anyString()))
                .thenThrow(new BadCredentialsException("internal message"));

        login("""
                {"username": "demo", "password": "not-the-password"}
                """)
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }

    @Test
    @DisplayName("An unexpected failure is answered with 500, without internal details")
    void unexpectedFailureIsAnsweredWith500() {
        when(loginService.login(anyString(), anyString()))
                .thenThrow(new IllegalStateException("connection to 10.0.0.7 refused"));

        login("""
                {"username": "demo", "password": "demo1234"}
                """)
                .expectStatus().isEqualTo(500)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.status").isEqualTo(500)
                .jsonPath("$.detail").value(String.class, detail -> assertThat(detail).doesNotContain("10.0.0.7"));
    }

    @Test
    @DisplayName("A rejected login says exactly this, whatever the cause: the text is fixed")
    void rejectedLoginHasAFixedText() {
        when(loginService.login(anyString(), anyString()))
                .thenThrow(new BadCredentialsException("internal message"));

        login("""
                {"username": "demo", "password": "not-the-password"}
                """)
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Invalid username or password.");
    }

    @Test
    @DisplayName("An unexpected failure says exactly this, whatever failed: the text is fixed")
    void unexpectedFailureHasAFixedText() {
        when(loginService.login(anyString(), anyString()))
                .thenThrow(new IllegalStateException("connection to 10.0.0.7 refused"));

        login("""
                {"username": "demo", "password": "demo1234"}
                """)
                .expectStatus().isEqualTo(500)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Internal Server Error")
                .jsonPath("$.detail").isEqualTo("An unexpected error occurred.")
                .jsonPath("$.instance").isEqualTo(LOGIN);
    }

    @Test
    @DisplayName("A login that does not accept JSON as the answer is answered with 406, before any credential is checked")
    void loginThatDoesNotAcceptJsonIsRejected() {
        client.post().uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_PLAIN)
                .body("""
                        {"username": "demo", "password": "demo1234"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(406)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.status").isEqualTo(406)
                .jsonPath("$.title").isEqualTo("Not Acceptable")
                .jsonPath("$.detail").isEqualTo("Acceptable representations: [application/json].")
                .jsonPath("$.instance").isEqualTo(LOGIN);

        verifyNoInteractions(loginService);
    }

    @Test
    @DisplayName("The documentation shows the login in its own group, with a summary and a description")
    void documentationShowsTheLoginInItsGroup() {
        String documentation = apiDocumentation();
        List<String> tags = JsonPath.read(documentation, "$.paths['/api/v1/auth/login'].post.tags");
        String summary = JsonPath.read(documentation, "$.paths['/api/v1/auth/login'].post.summary");
        String description = JsonPath.read(documentation, "$.paths['/api/v1/auth/login'].post.description");
        List<String> descriptionOfTheGroup = JsonPath.read(documentation,
                "$.tags[?(@.name=='Authentication')].description");

        assertThat(tags).containsExactly("Authentication");
        assertThat(descriptionOfTheGroup).hasSize(1).allSatisfy(text -> assertThat(text).isNotBlank());
        assertThat(summary).isNotBlank();
        // It tells where the token goes next, and that the example is the test user.
        assertThat(description).contains("Authorize", "test user");
    }

    @Test
    @DisplayName("The documentation of the login names the test administrator, which is the user that can create users")
    void documentationOfTheLoginNamesTheTestAdministrator() {
        String description = JsonPath.read(apiDocumentation(), "$.paths['/api/v1/auth/login'].post.description");

        assertThat(description).contains("`admin`", "`admin1234`", "create users");
    }

    @Test
    @DisplayName("The documentation shows the body of the login, with the test user as its example")
    void documentationShowsTheBodyWithTheTestUserAsExample() {
        String documentation = apiDocumentation();
        Map<String, Object> body = JsonPath.read(documentation, "$.paths['/api/v1/auth/login'].post.requestBody");
        String schema = JsonPath.read(documentation,
                "$.paths['/api/v1/auth/login'].post.requestBody.content['application/json'].schema['$ref']");
        Map<String, Map<String, Object>> fields = JsonPath.read(documentation,
                "$.components.schemas['LoginRequest'].properties");
        List<String> required = JsonPath.read(documentation, "$.components.schemas['LoginRequest'].required");

        assertThat(body).containsEntry("required", true);
        assertThat(schema).isEqualTo("#/components/schemas/LoginRequest");
        assertThat(fields).containsOnlyKeys("username", "password");
        assertThat(required).containsExactlyInAnyOrder("username", "password");
        // With these examples, "Try it out" logs in without typing anything.
        assertThat(fields.get("username")).containsEntry("type", "string").containsEntry("example", "demo");
        assertThat(fields.get("password")).containsEntry("type", "string").containsEntry("example", "demo1234");
        // Each field says that it is the test user, which exists only where the seed is on.
        assertThat((String) fields.get("username").get("description")).contains("test user", "seed");
        assertThat((String) fields.get("password").get("description")).contains("test user", "seed");
    }

    @Test
    @DisplayName("The documentation shows the answer of the login as JSON and its two errors as Problem Details")
    void documentationShowsTheAnswerAndTheErrors() {
        String documentation = apiDocumentation();
        Map<String, Map<String, Object>> responses = JsonPath.read(documentation,
                "$.paths['/api/v1/auth/login'].post.responses");

        assertThat(responses).containsOnlyKeys("200", "400", "401");
        for (Map<String, Object> response : responses.values()) {
            assertThat((String) response.get("description")).isNotBlank().isNotEqualTo("OK");
        }
        // The token, with its three fields, each with its description.
        Map<String, Map<String, Object>> content = JsonPath.read(documentation,
                "$.paths['/api/v1/auth/login'].post.responses['200'].content");
        assertThat(content).containsOnlyKeys("application/json");
        String schema = JsonPath.read(documentation,
                "$.paths['/api/v1/auth/login'].post.responses['200'].content['application/json'].schema['$ref']");
        assertThat(schema).isEqualTo("#/components/schemas/LoginResponse");
        Map<String, Map<String, Object>> fields = JsonPath.read(documentation,
                "$.components.schemas['LoginResponse'].properties");
        assertThat(fields).containsOnlyKeys("accessToken", "tokenType", "expiresIn");
        for (Map<String, Object> field : fields.values()) {
            assertThat((String) field.get("description")).isNotBlank();
        }
        assertThat(fields.get("tokenType")).containsEntry("example", "Bearer");
        assertThat(fields.get("expiresIn")).containsEntry("type", "integer").containsEntry("example", 3600);
        // The errors are Problem Details.
        for (String error : List.of("400", "401")) {
            Map<String, Object> errorContent = JsonPath.read(documentation,
                    "$.paths['/api/v1/auth/login'].post.responses['" + error + "'].content");
            assertThat(errorContent).containsOnlyKeys("application/problem+json");
        }
    }

    @Test
    @DisplayName("The documentation shows that the 400 of the login can say which fields are invalid, and that the 401 cannot")
    void documentationShowsTheListOfErrorsInTheBadRequestOnly() {
        String documentation = apiDocumentation();
        String badRequest = JsonPath.read(documentation, "$.paths['/api/v1/auth/login'].post.responses['400']"
                + ".content['application/problem+json'].schema['$ref']");
        String unauthorized = JsonPath.read(documentation, "$.paths['/api/v1/auth/login'].post.responses['401']"
                + ".content['application/problem+json'].schema['$ref']");
        String description = JsonPath.read(documentation,
                "$.paths['/api/v1/auth/login'].post.responses['400'].description");

        assertThat(badRequest).isEqualTo("#/components/schemas/ValidationProblemDetail");
        assertThat(unauthorized).isEqualTo("#/components/schemas/ProblemDetail");
        // It tells where to look for the field that failed.
        assertThat(description).contains("errors");

        // The schema of the 400 is the one of every error, plus the list.
        List<Map<String, Object>> parts = JsonPath.read(documentation,
                "$.components.schemas['ValidationProblemDetail'].allOf");
        assertThat(parts).hasSize(2);
        assertThat(parts.getFirst()).containsEntry("$ref", "#/components/schemas/ProblemDetail");
        Map<String, Object> errors = JsonPath.read(documentation,
                "$.components.schemas['ValidationProblemDetail'].allOf[1].properties.errors");
        assertThat(errors).containsEntry("type", "array")
                .containsEntry("items", Map.of("$ref", "#/components/schemas/InvalidField"));
        assertThat((String) errors.get("description")).isNotBlank();
        // Each element says which field and what it must be, and nothing else.
        Map<String, Map<String, Object>> element = JsonPath.read(documentation,
                "$.components.schemas['InvalidField'].properties");
        assertThat(element).containsOnlyKeys("field", "message");
        for (Map<String, Object> field : element.values()) {
            assertThat(field).containsEntry("type", "string");
            assertThat((String) field.get("description")).isNotBlank();
        }
        // The schema of the other errors is not touched: they have no list.
        Map<String, Object> everyError = JsonPath.read(documentation,
                "$.components.schemas['ProblemDetail'].properties");
        assertThat(everyError).doesNotContainKey("errors");
    }

    @Test
    @DisplayName("The documentation shows that the login requires no token, even though every other request does")
    void documentationShowsThatTheLoginRequiresNoToken() {
        Map<String, Object> login = JsonPath.read(apiDocumentation(), "$.paths['/api/v1/auth/login'].post");

        // An empty list, written out: it is what overrides the requirement of the whole document.
        assertThat(login).containsEntry("security", List.of());
    }

    private String apiDocumentation() {
        return client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    private RestTestClient.ResponseSpec login(String body) {
        return client.post().uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    /** A login with a valid password and this user name, which is written into the JSON as it is. */
    private static String withUserName(String username) {
        return "{\"username\": \"" + username + "\", \"password\": \"demo1234\"}";
    }

    /** A login with a valid user name and this password, which is written into the JSON as it is. */
    private static String withPassword(String password) {
        return "{\"username\": \"demo\", \"password\": \"" + password + "\"}";
    }

    /** Sends the two values as they are: the client writes the JSON and escapes what needs it. */
    private RestTestClient.ResponseSpec loginAs(String username, String password) {
        return client.post().uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .exchange();
    }

    /** Checks the 400 of a body that is not accepted, and that no credential was checked. */
    private void expectInvalidBody(RestTestClient.ResponseSpec response) {
        response
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.title").isEqualTo("Bad Request")
                .jsonPath("$.detail").isEqualTo("Invalid request content.")
                .jsonPath("$.instance").isEqualTo(LOGIN);

        verifyNoInteractions(loginService);
    }

}
