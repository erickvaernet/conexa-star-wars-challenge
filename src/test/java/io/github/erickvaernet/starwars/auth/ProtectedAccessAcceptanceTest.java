package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-10: only a user with a session reaches the data.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, with the seed switched
 * on, and looks for SWAPI at the simulated one: a request that gets past the access rules reaches
 * a list, and the list needs SWAPI. A valid token is the one that the login gives to the test
 * user. The tokens that must be rejected are built here: the test alters a valid one, or signs
 * its own with the signing key of the tests.
 *
 * <p>The tests of a rejected token send the same request as the test of a valid token, and change
 * nothing but the token. That is what shows that the token is the reason for the rejection.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class ProtectedAccessAcceptanceTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String PEOPLE = "/api/v1/people";

    private static final String USERNAME = "demo";

    /** The password published in the README. */
    private static final String PASSWORD = "demo1234";

    /** A key of the right length that is not the one of the application. */
    private static final String ANOTHER_KEY = "another-signing-key-0123456789-abcdef";

    @LocalServerPort
    private int port;

    /** The signing key of the tests, from {@code src/test/resources/config/application.properties}. */
    @Value("${JWT_SECRET}")
    private String signingKey;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/api/v1/people",
        "/api/v1/films",
        "/api/v1/starships",
        "/api/v1/vehicles",
        "/api/v1/people/1",
        "/api/v1/films/1",
        "/api/v1/starships/9",
        "/api/v1/vehicles/4"
    })
    @DisplayName("CA-10.1: without a session, a list or a detail is answered with 401")
    void requestWithoutASessionIsRejected(String path) {
        expectNotAuthenticated(client.get().uri(path).exchange(), path);
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/api/v1/people",
        "/api/v1/films"
    })
    @DisplayName("CA-10.4: with a valid token, a list is answered with 200")
    void listWithAValidTokenIsAnswered(String path) {
        client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearer(login())).exchange()
                .expectStatus().isOk();

        // A token signed by this test is as good as one from the login. The tests of the rejected
        // tokens sign theirs the same way, so they are rejected only for what they change.
        Instant now = Instant.now();
        String signedHere = sign(signingKey, now, now.plus(1, ChronoUnit.HOURS));
        client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearer(signedHere)).exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("CA-10.2: a token with a tampered signature is rejected with 401")
    void tokenWithATamperedSignatureIsRejected() {
        String valid = login();
        Instant now = Instant.now();

        expectRejected(bearer(withAnotherSignature(valid)));
        // The content changes and the signature stays: it no longer matches.
        expectRejected(bearer(withAnotherRole(valid)));
        expectRejected(bearer(sign(ANOTHER_KEY, now, now.plus(1, ChronoUnit.HOURS))));
        expectRejected(bearer(withoutSignature(valid)));
    }

    @Test
    @DisplayName("CA-10.3: an expired token is rejected with 401")
    void expiredTokenIsRejected() {
        // Nobody can wait for a real token to expire: this one is signed as already expired. It
        // is what the login issued two hours ago, with its lifetime of one hour.
        Instant now = Instant.now();
        String expired = sign(signingKey, now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));

        expectRejected(bearer(expired));
    }

    @ParameterizedTest(name = "Authorization: {0}")
    @ValueSource(strings = {
        "Bearer not-a-token",
        "Bearer a.b.c",
        "Bearer not a token",
        "Bearer",
        // The right user name and password, sent with every request instead of a token.
        "Basic ZGVtbzpkZW1vMTIzNA=="
    })
    @DisplayName("A malformed token is rejected with 401, like a missing one")
    void malformedTokenIsRejected(String authorization) {
        expectRejected(authorization);
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/swagger-ui.html",
        "/v3/api-docs",
        "/actuator/health",
        // What the documentation page loads once it is open.
        "/swagger-ui/index.html",
        "/v3/api-docs/swagger-config"
    })
    @DisplayName("CA-10.5: without a session, the API documentation and the health check answer 200")
    void documentationAndHealthCheckArePublic(String path) {
        // The documentation page redirects to its real address, and a browser follows it.
        RestTestClient browser = RestTestClient
                .bindToServer(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()))
                .baseUrl("http://localhost:" + port)
                .build();

        browser.get().uri(path).exchange().expectStatus().isOk();
    }

    /** Logs in as the test user and returns the token. */
    private String login() {
        String body = client.post().uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", USERNAME, "password", PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        return JsonPath.read(body, "$.accessToken");
    }

    private RestTestClient.ResponseSpec getPeopleWith(String authorization) {
        return client.get().uri(PEOPLE).header(HttpHeaders.AUTHORIZATION, authorization).exchange();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * Checks that a request for the list of people with this {@code Authorization} header is
     * rejected, with the very same answer as a request without the header.
     */
    private void expectRejected(String authorization) {
        String withoutToken = expectNotAuthenticated(client.get().uri(PEOPLE).exchange(), PEOPLE);
        String rejected = expectNotAuthenticated(getPeopleWith(authorization), PEOPLE);

        // Nothing in the answer may tell why the token was rejected, nor show internal details.
        assertThat(rejected).isEqualTo(withoutToken);
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
                .jsonPath("$.instance").isEqualTo(path)
                .returnResult().getResponseBody();
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * Signs a token the way the application does: HS256, with the user name, the role and the
     * times of issue and expiry.
     */
    private static String sign(String key, Instant issuedAt, Instant expiresAt) {
        JwtEncoder encoder = NimbusJwtEncoder
                .withSecretKey(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256)
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(USERNAME)
                .claim("role", "USER")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // A JWT is three Base64URL parts separated by dots: header, claims and signature.

    /** Changes the first character of the signature and leaves the rest as it was. */
    private static String withAnotherSignature(String token) {
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        return parts[0] + "." + parts[1] + "." + first + parts[2].substring(1);
    }

    /** Changes the role in the claims and keeps the signature of the original token. */
    private static String withAnotherRole(String token) {
        String[] parts = token.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String changed = claims.replace("\"USER\"", "\"ADMIN\"");
        assertThat(changed).isNotEqualTo(claims);
        return parts[0] + "." + encode(changed) + "." + parts[2];
    }

    /** Keeps the claims, declares in the header that the token is not signed and drops the signature. */
    private static String withoutSignature(String token) {
        String[] parts = token.split("\\.");
        return encode("{\"alg\":\"none\"}") + "." + parts[1] + ".";
    }

    private static String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

}
