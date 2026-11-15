package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checks what a token opens: a request that has no access rule of its own needs a valid one.
 *
 * <p>Only the web layer is loaded, with the real access rules, the real validation and the real
 * issuing of tokens. It needs neither the database nor Docker. The request under test exists only
 * here: it stands for one that is added later without touching the access rules.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfiguration.class, TokenIssuer.class, TokenAccessTest.RouteAddedLater.class})
class TokenAccessTest {

    private static final String ROUTE = "/api/v1/a-route-added-later";

    private static final String LOGIN = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenIssuer issuer;

    @MockitoBean
    private LoginService loginService;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc).build();
    }

    @Test
    @DisplayName("Without a token, a request with no rule of its own is answered with 401")
    void requestWithoutATokenIsClosed() {
        expectNotAuthenticated(client.get().uri(ROUTE).exchange());
    }

    @Test
    @DisplayName("With a valid token, the request is answered, and it knows which user the token belongs to")
    void requestWithAValidTokenIsAnswered() {
        client.get().uri(ROUTE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issue("demo", "USER"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("demo");
    }

    @Test
    @DisplayName("A token signed with another key gets the same 401 as no token")
    void tokenSignedWithAnotherKeyGetsTheSameAnswerAsNoToken() {
        expectSameAnswerAsWithoutAToken("Bearer " + tokenSignedWithAnotherKey());
    }

    @ParameterizedTest(name = "Authorization: {0}")
    @ValueSource(strings = {"Bearer not-a-token", "Bearer not a token", "Bearer"})
    @DisplayName("A malformed token gets the same 401 as no token")
    void malformedTokenGetsTheSameAnswerAsNoToken(String authorization) {
        expectSameAnswerAsWithoutAToken(authorization);
    }

    @Test
    @DisplayName("Without a token, the 401 names the way to authenticate, as HTTP requires of every 401")
    void answerToAMissingTokenNamesTheWayToAuthenticate() {
        client.get().uri(ROUTE)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }

    @ParameterizedTest(name = "Authorization: {0}")
    @ValueSource(strings = {"Bearer not-a-token", "Bearer not a token", "Bearer"})
    @DisplayName("With a malformed token, the 401 names the way to authenticate and nothing else")
    void answerToAMalformedTokenNamesTheWayToAuthenticate(String authorization) {
        // The header is the scheme alone, without the parameters that would say what was wrong.
        client.get().uri(ROUTE)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }

    @Test
    @DisplayName("With a token signed with another key, the 401 names the way to authenticate and nothing else")
    void answerToATokenSignedWithAnotherKeyNamesTheWayToAuthenticate() {
        client.get().uri(ROUTE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSignedWithAnotherKey())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }

    @Test
    @DisplayName("A token that is rejected is rejected on a public request too")
    void rejectedTokenIsRejectedOnAPublicRequestToo() {
        // The token is validated before the access rules are looked at. A public request needs no
        // token, but if it brings one, it has to be valid.
        expectNotAuthenticated(login("Bearer " + tokenSignedWithAnotherKey()), LOGIN);

        verifyNoInteractions(loginService);
    }

    @Test
    @DisplayName("A public request with a valid token is answered as usual")
    void publicRequestWithAValidTokenIsAnswered() {
        when(loginService.login(anyString(), anyString())).thenReturn(new LoginResponse("the-token", "Bearer", 3600));

        login("Bearer " + issuer.issue("demo", "USER")).expectStatus().isOk();
    }

    private RestTestClient.ResponseSpec login(String authorization) {
        return client.post().uri(LOGIN)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username": "demo", "password": "demo1234"}
                        """)
                .exchange();
    }

    /** A token with the right content and a valid lifetime, signed with a key that is not the application's. */
    private static String tokenSignedWithAnotherKey() {
        return new TokenIssuer(new JwtProperties("another-signing-key-0123456789-abcdef", 60)).issue("demo", "USER");
    }

    /** Nothing in the answer may tell why the token was rejected. */
    private void expectSameAnswerAsWithoutAToken(String authorization) {
        String withoutToken = expectNotAuthenticated(client.get().uri(ROUTE).exchange());
        String rejected = expectNotAuthenticated(
                client.get().uri(ROUTE).header(HttpHeaders.AUTHORIZATION, authorization).exchange());

        assertThat(rejected).isEqualTo(withoutToken);
    }

    private static String expectNotAuthenticated(RestTestClient.ResponseSpec response) {
        return expectNotAuthenticated(response, ROUTE);
    }

    /** Checks the 401 in Problem Details format and returns its body. */
    private static String expectNotAuthenticated(RestTestClient.ResponseSpec response, String path) {
        byte[] body = response
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Unauthorized")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.detail").isEqualTo("Authentication is required to access this resource.")
                .jsonPath("$.instance").isEqualTo(path)
                .returnResult().getResponseBody();
        return new String(body, StandardCharsets.UTF_8);
    }

    /** A request that someone adds later, without giving it an access rule. */
    @RestController
    static class RouteAddedLater {

        @GetMapping(ROUTE)
        String whoAsks(Principal user) {
            return user.getName();
        }

    }

}
