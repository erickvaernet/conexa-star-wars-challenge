package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

}
