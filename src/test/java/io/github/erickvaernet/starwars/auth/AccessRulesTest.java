package io.github.erickvaernet.starwars.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Checks the access rules: which requests are public, and that every other one is closed.
 *
 * <p>Only the web layer is loaded, with the real access rules. It needs neither the database nor
 * Docker. The API documentation and the health check are not part of what is loaded here, so a
 * public request to them ends in "not found": that answer comes after the access rules, and it
 * proves that they let the request through.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfiguration.class)
class AccessRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginService loginService;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc).build();
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/",
        "/api/v1/people",
        "/api/v1/people/1",
        "/api/v1/a-route-added-later",
        "/api/v1/auth/login",
        "/api/v1/auth/users",
        "/actuator",
        "/actuator/env",
        "/actuator/health/db",
        "/error"
    })
    @DisplayName("A request without a rule of its own is answered with 401, as Problem Details")
    void requestWithoutARuleIsClosed(String path) {
        client.get().uri(path)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.title").isEqualTo("Unauthorized")
                .jsonPath("$.instance").isEqualTo(path);
    }

    @ParameterizedTest(name = "POST {0}")
    @ValueSource(strings = {"/api/v1/people", "/logout", "/actuator/health", "/v3/api-docs"})
    @DisplayName("Requests that change something are closed too")
    void writeRequestsAreClosed(String path) {
        client.post().uri(path).exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("The login is public")
    void loginIsPublic() {
        when(loginService.login(anyString(), anyString())).thenReturn(new LoginResponse("the-token", "Bearer", 3600));

        client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username": "demo", "password": "demo1234"}
                        """)
                .exchange()
                .expectStatus().isOk();
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/swagger-ui.html",
        "/swagger-ui/index.html",
        "/v3/api-docs",
        "/v3/api-docs/swagger-config"
    })
    @DisplayName("The API documentation is public")
    void apiDocumentationIsPublic(String path) {
        // "Not found" instead of "unauthorized": see the note on the class.
        client.get().uri(path).exchange().expectStatus().isNotFound();
    }

    @Test
    @DisplayName("The health check is public")
    void healthCheckIsPublic() {
        // "Not found" instead of "unauthorized": see the note on the class.
        client.get().uri("/actuator/health").exchange().expectStatus().isNotFound();
    }

}
