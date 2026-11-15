package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-09: logging in with a user name and a password.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, with the seed switched
 * on: the user that logs in is the test user that the seed loads.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import(TestcontainersConfiguration.class)
class LoginAcceptanceTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String USERNAME = "demo";

    /** The password published in the README. The database only holds its hash. */
    private static final String PASSWORD = "demo1234";

    private static final String WRONG_PASSWORD = "not-the-password";

    /** A BCrypt hash: version, cost, and 53 characters of salt and hash. */
    private static final String BCRYPT_HASH = "\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("CA-09.1: a login with the right user name and password returns a token")
    void loginWithTheRightCredentialsReturnsAToken() {
        login(Map.of("username", USERNAME, "password", PASSWORD))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").value(String.class, token -> assertThat(token).isNotBlank())
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.expiresIn").isEqualTo(3600);
    }

    @Test
    @DisplayName("The token is signed with HS256 and carries the user name, the role and a 60-minute lifetime")
    void tokenCarriesTheUserNameTheRoleAndItsLifetime() {
        String body = login(Map.of("username", USERNAME, "password", PASSWORD))
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        String token = JsonPath.read(body, "$.accessToken");

        // A JWT is three Base64URL parts separated by dots: header, claims and signature.
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        DocumentContext header = decode(parts[0]);
        DocumentContext claims = decode(parts[1]);

        assertThat(header.read("$.alg", String.class)).isEqualTo("HS256");
        assertThat(claims.read("$.sub", String.class)).isEqualTo(USERNAME);
        assertThat(claims.read("$.role", String.class)).isEqualTo("USER");
        assertThat(claims.read("$.exp", Long.class) - claims.read("$.iat", Long.class)).isEqualTo(3600);
    }

    @Test
    @DisplayName("CA-09.2: a login with a wrong password is rejected with 401")
    void loginWithAWrongPasswordIsRejected() {
        login(Map.of("username", USERNAME, "password", WRONG_PASSWORD))
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(401);
    }

    @Test
    @DisplayName("CA-09.3: a login with an unknown user gets the same 401 as a wrong password")
    void loginWithAnUnknownUserGetsTheSameAnswerAsAWrongPassword() {
        EntityExchangeResult<String> wrongPassword =
                login(Map.of("username", USERNAME, "password", WRONG_PASSWORD))
                        .expectBody(String.class).returnResult();
        EntityExchangeResult<String> unknownUser =
                login(Map.of("username", "nadie", "password", PASSWORD))
                        .expectBody(String.class).returnResult();

        assertThat(unknownUser.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Nothing in the answer may tell whether the user exists.
        assertThat(unknownUser.getStatus()).isEqualTo(wrongPassword.getStatus());
        assertThat(unknownUser.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .isEqualTo(wrongPassword.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertThat(unknownUser.getResponseBody()).isEqualTo(wrongPassword.getResponseBody());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incompleteCredentials")
    @DisplayName("CA-09.4: a login without user name or without password is rejected with 400")
    void loginWithoutUserNameOrPasswordIsRejected(Map<String, String> credentials) {
        login(credentials)
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(400);
    }

    /** The specification rejects a field that is missing and one that is empty. */
    static Stream<Map<String, String>> incompleteCredentials() {
        return Stream.of(
                Map.of("password", PASSWORD),
                Map.of("username", USERNAME),
                Map.of("username", "", "password", PASSWORD),
                Map.of("username", USERNAME, "password", ""));
    }

    @Test
    @DisplayName("CA-09.5: the database holds the BCrypt hash of the password, not the password")
    void databaseHoldsTheBcryptHashNotThePassword() {
        Map<String, Object> user = jdbc.queryForMap("SELECT * FROM users WHERE username = ?", USERNAME);

        assertThat((String) user.get("password_hash")).matches(BCRYPT_HASH);
        assertThat(user.toString()).doesNotContain(PASSWORD);
    }

    private RestTestClient.ResponseSpec login(Map<String, String> credentials) {
        return client.post().uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(credentials)
                .exchange();
    }

    /** Decodes one part of a JWT into its JSON content. */
    private static DocumentContext decode(String part) {
        return JsonPath.parse(new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8));
    }

}
