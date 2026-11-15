package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Checks the login request: which bodies are accepted, and how each outcome is answered.
 *
 * <p>Only the web layer is loaded, with the real access rules and error format; the login rules
 * are replaced. It needs neither the database nor Docker.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfiguration.class)
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

    private RestTestClient.ResponseSpec login(String body) {
        return client.post().uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

}
