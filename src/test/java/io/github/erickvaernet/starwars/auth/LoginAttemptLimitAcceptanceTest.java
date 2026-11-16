package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import io.github.erickvaernet.starwars.swapi.ManualClock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-29: the failed logins are counted by source address and, at the fifth
 * one within fifteen minutes, every login from that address is answered with 429 until the
 * window ends.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, with the seed switched
 * on and the limit in its default values. Every request of a test comes from the same address,
 * the one of this machine.
 *
 * <p>No test waits fifteen minutes. The limit reads the time from the clock that this class gives
 * it, which only moves when a test tells it to. So the seconds of {@code Retry-After} are exact:
 * between two requests of a test, no time goes by.
 *
 * <p>Every test starts and ends with the count empty, so none depends on the failed logins of
 * another one. "A failed login" is one as {@code demo} with a wrong password.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class LoginAttemptLimitAcceptanceTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String PEOPLE = "/api/v1/people";

    private static final String DEMO = "demo";

    /** The password published in the README. */
    private static final String DEMO_PASSWORD = "demo1234";

    private static final String ADMIN = "admin";

    private static final String WRONG_PASSWORD = "not-the-password";

    /** The header a proxy uses to tell the address of the client. Here the client writes it. */
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    /** The 401 of a failed login, whole: the one of CA-09.2. */
    private static final String WRONG_CREDENTIALS = """
            {
              "type": "about:blank",
              "title": "Unauthorized",
              "status": 401,
              "detail": "Invalid username or password.",
              "instance": "/api/v1/auth/login"
            }
            """;

    /** The 429 of a blocked login, whole: the one of the specification. */
    private static final String BLOCKED = """
            {
              "type": "about:blank",
              "title": "Too Many Requests",
              "status": 429,
              "detail": "Too many failed login attempts. Try again later.",
              "instance": "/api/v1/auth/login"
            }
            """;

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext application;

    /** The clock of the limit, in the hands of the tests. */
    @Autowired
    private ManualClock clock;

    /** A client without a session. */
    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        FailedLogins.forget(application);
    }

    @AfterEach
    void leaveTheCountEmpty() {
        FailedLogins.forget(application);
    }

    @Test
    @DisplayName("CA-29.1: five failed logins in a row are answered with 401, each one identical to the one of CA-09.2")
    void fiveFailedLoginsAreAnsweredAsAlways() {
        List<String> answers = new ArrayList<>();
        for (int attempt = 1; attempt <= 5; attempt++) {
            answers.add(expectWrongCredentials(failedLogin()));
        }

        // The fifth one is the same as the first: nothing in it warns that the next one is blocked.
        assertThat(answers).hasSize(5).containsOnly(answers.getFirst());
    }

    @Test
    @DisplayName("CA-29.2: after five failed logins, another failed login is answered with 429, as Problem Details with the fixed text, with Retry-After as a whole number from 1 to 900 and without WWW-Authenticate")
    void sixthFailedLoginIsBlocked() {
        failLogin(5);

        int retryAfter = expectBlocked(failedLogin());

        assertThat(retryAfter).isBetween(1, 900);
        // The clock did not move since the first failure, so the whole window is left.
        assertThat(retryAfter).isEqualTo(900);
    }

    @Test
    @DisplayName("CA-29.3: after five failed logins, a login as demo with the right password is answered with 429, with the same body as CA-29.2: no token is given")
    void rightCredentialsAreBlockedToo() {
        failLogin(5);

        // The body is compared whole and strictly: it is the one of CA-29.2, and it has no token.
        expectBlocked(correctLogin());
    }

    @Test
    @DisplayName("CA-29.4: after four failed logins, a correct login, a failed one and another correct one are answered with 200, 401 and 429: the correct login did not clear the count")
    void correctLoginDoesNotClearTheCount() {
        failLogin(4);

        expectToken(correctLogin());
        expectWrongCredentials(failedLogin());
        expectBlocked(correctLogin());
    }

    @Test
    @DisplayName("CA-29.5: after two failed logins as demo, two as admin and one as a user that does not exist, a correct login as demo is answered with 429: the failures are counted by address, not by user")
    void failuresAreCountedByAddressNotByUser() {
        expectWrongCredentials(login(DEMO, WRONG_PASSWORD));
        expectWrongCredentials(login(DEMO, WRONG_PASSWORD));
        expectWrongCredentials(login(ADMIN, WRONG_PASSWORD));
        expectWrongCredentials(login(ADMIN, WRONG_PASSWORD));
        expectWrongCredentials(login("nadie", WRONG_PASSWORD));

        expectBlocked(correctLogin());
    }

    @Test
    @DisplayName("CA-29.6: six logins without password are answered with 400 and the correct login after them with 200: a 400 does not count as a failure")
    void invalidBodyDoesNotCountAsAFailure() {
        for (int attempt = 1; attempt <= 6; attempt++) {
            loginWithoutPassword().expectStatus().isBadRequest();
        }

        expectToken(correctLogin());
    }

    @Test
    @DisplayName("CA-29.7: after five failed logins, a login without password is answered with 400, with errors, the same as without the block: the form of the request is checked before the limit")
    void formOfTheRequestIsCheckedBeforeTheLimit() {
        String withoutTheBlock = bodyOf(loginWithoutPassword().expectStatus().isBadRequest());
        failLogin(5);
        // The address is blocked: this is what the 400 below comes before.
        expectBlocked(correctLogin());

        RestTestClient.ResponseSpec response = loginWithoutPassword();

        response.expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().doesNotExist(HttpHeaders.RETRY_AFTER);
        String withTheBlock = bodyOf(response);
        assertThat(withTheBlock).isEqualTo(withoutTheBlock);
        List<String> invalidFields = JsonPath.read(withTheBlock, "$.errors[*].field");
        assertThat(invalidFields).containsExactly("password");
    }

    @Test
    @DisplayName("CA-29.8: fifteen minutes after the first of five failed logins, a correct login is answered with 200")
    void blockEndsFifteenMinutesAfterTheFirstFailure() {
        failLogin(5);

        // One second before the fifteen minutes, the address is still blocked.
        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThat(expectBlocked(correctLogin())).isEqualTo(1);

        // At exactly fifteen minutes, the window is over.
        clock.advance(Duration.ofSeconds(1));
        expectToken(correctLogin());
    }

    @Test
    @DisplayName("CA-29.9: once the window of a blocked address is over, four failed logins are answered with 401 and the correct login after them with 200: the count went back to zero")
    void countGoesBackToZeroWhenTheWindowEnds() {
        failLogin(5);
        expectBlocked(correctLogin());
        clock.advance(Duration.ofMinutes(15));

        // With the old count, the first of these would already be blocked.
        failLogin(4);

        expectToken(correctLogin());
    }

    @Test
    @DisplayName("CA-29.10: with one failed login and, ten minutes later, four more, a login at that moment is answered with 429 and Retry-After of 300 or less, and a correct one five minutes later with 200: the window started with the first failure")
    void windowStartsWithTheFirstFailure() {
        failLogin(1);
        clock.advance(Duration.ofMinutes(10));
        failLogin(4);

        int retryAfter = expectBlocked(correctLogin());

        // Five minutes are left of the window of the first failure, not fifteen of the last one.
        assertThat(retryAfter).isLessThanOrEqualTo(300);
        assertThat(retryAfter).isEqualTo(300);

        clock.advance(Duration.ofMinutes(5));
        expectToken(correctLogin());
    }

    @Test
    @DisplayName("CA-29.11: with five failed logins and, fourteen minutes later, one more login that is answered with 429, a correct login one minute after that is answered with 200: a blocked request does not make the window longer")
    void blockedRequestDoesNotMakeTheWindowLonger() {
        failLogin(5);
        clock.advance(Duration.ofMinutes(14));
        assertThat(expectBlocked(failedLogin())).isEqualTo(60);

        clock.advance(Duration.ofMinutes(1));

        expectToken(correctLogin());
    }

    @Test
    @DisplayName("CA-29.12: with a session opened as demo and then five failed logins from the same address, the list of people with that token and the health check without a token are answered with 200: the limit is only of the login")
    void limitIsOnlyOfTheLogin() {
        String token = TestSession.login(client);
        failLogin(5);
        // The login of this address is blocked: this is what the two requests below are not.
        expectBlocked(correctLogin());

        client.get().uri(PEOPLE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.content[0].name").isEqualTo("Luke Skywalker");
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("CA-29.15: started as in local, without reading the headers of a proxy, five failed logins with X-Forwarded-For: 203.0.113.7 block a correct login with X-Forwarded-For: 203.0.113.8: the header is ignored and every request counts for the address of the connection")
    void forwardedHeaderIsIgnoredWithoutAProxy() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            expectWrongCredentials(loginFrom("203.0.113.7", WRONG_PASSWORD));
        }

        expectBlocked(loginFrom("203.0.113.8", DEMO_PASSWORD));
    }

    @Test
    @DisplayName("CA-29.17: in the API documentation, the login declares the answers 200, 400, 401 and 429; the 429 uses the common error schema, declares the Retry-After header and has an example; the documentation still has exactly ten requests")
    void documentationDeclaresTheBlockedLogin() {
        String documentation = bodyOf(client.get().uri("/v3/api-docs").exchange().expectStatus().isOk());
        String login = "$.paths['" + LOGIN + "'].post";

        Map<String, Object> responses = JsonPath.read(documentation, login + ".responses");
        assertThat(responses).containsOnlyKeys("200", "400", "401", "429");

        // The 429 is Problem Details, with the schema that every error of the API uses.
        String blocked = login + ".responses['429']";
        Map<String, Object> content = JsonPath.read(documentation, blocked + ".content");
        assertThat(content).containsOnlyKeys(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String problem = blocked + ".content['" + MediaType.APPLICATION_PROBLEM_JSON_VALUE + "']";
        Map<String, Object> schema = JsonPath.read(documentation, problem + ".schema");
        assertThat(schema).isEqualTo(Map.of("$ref", "#/components/schemas/ProblemDetail"));

        // The header that says how long to wait: a whole number of seconds.
        Map<String, Object> headers = JsonPath.read(documentation, blocked + ".headers");
        assertThat(headers).containsOnlyKeys(HttpHeaders.RETRY_AFTER);
        String typeOfTheHeader = JsonPath.read(documentation, blocked + ".headers['Retry-After'].schema.type");
        assertThat(typeOfTheHeader).isEqualTo("integer");
        String descriptionOfTheHeader = JsonPath.read(documentation, blocked + ".headers['Retry-After'].description");
        assertThat(descriptionOfTheHeader).isNotBlank();

        // Its example. That it is the real answer is CA-29.18.
        Map<String, Object> example = JsonPath.read(documentation, problem + ".example");
        assertThat(example).containsEntry("status", 429);

        Map<String, Map<String, Object>> paths = JsonPath.read(documentation, "$.paths");
        List<String> requests = new ArrayList<>();
        paths.forEach((path, methods) -> methods.keySet()
                .forEach(method -> requests.add(method.toUpperCase(Locale.ROOT) + " " + path)));
        assertThat(requests).contains("POST " + LOGIN).hasSize(10);
    }

    /** Makes that many failed logins, and checks that each one is answered with the 401 of always. */
    private void failLogin(int times) {
        for (int attempt = 1; attempt <= times; attempt++) {
            expectWrongCredentials(failedLogin());
        }
    }

    private RestTestClient.ResponseSpec failedLogin() {
        return login(DEMO, WRONG_PASSWORD);
    }

    private RestTestClient.ResponseSpec correctLogin() {
        return login(DEMO, DEMO_PASSWORD);
    }

    private RestTestClient.ResponseSpec login(String username, String password) {
        return client.post().uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .exchange();
    }

    /** A login as demo that says, in the header of a proxy, which address it comes from. */
    private RestTestClient.ResponseSpec loginFrom(String address, String password) {
        return client.post().uri(LOGIN)
                .header(FORWARDED_FOR, address)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", DEMO, "password", password))
                .exchange();
    }

    private RestTestClient.ResponseSpec loginWithoutPassword() {
        return client.post().uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", DEMO))
                .exchange();
    }

    /**
     * Checks the 401 of a failed login, as a whole and strictly, and returns its body. It says
     * how to authenticate, as every 401, and nothing about the limit: neither how many attempts
     * are left nor how long to wait.
     */
    private static String expectWrongCredentials(RestTestClient.ResponseSpec response) {
        byte[] body = response
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectHeader().doesNotExist(HttpHeaders.RETRY_AFTER)
                .expectBody().json(WRONG_CREDENTIALS, JsonCompareMode.STRICT)
                .returnResult().getResponseBody();
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * Checks the 429 of a blocked login, as a whole and strictly, and returns the seconds of its
     * {@code Retry-After}. Unlike a 401, it does not say how to authenticate: doing it again
     * changes nothing until the window ends.
     */
    private static int expectBlocked(RestTestClient.ResponseSpec response) {
        EntityExchangeResult<byte[]> result = response
                .expectStatus().isEqualTo(429)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectBody().json(BLOCKED, JsonCompareMode.STRICT)
                .returnResult();

        // A whole number of seconds: neither a date, which the header also allows, nor decimals.
        String retryAfter = result.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotNull().matches("[1-9][0-9]*");
        return Integer.parseInt(retryAfter);
    }

    /** Checks that a login is answered with 200 and gives a token. */
    private static void expectToken(RestTestClient.ResponseSpec response) {
        response.expectStatus().isOk()
                .expectBody().jsonPath("$.accessToken").value(String.class, token -> assertThat(token).isNotBlank());
    }

    private static String bodyOf(RestTestClient.ResponseSpec response) {
        return response.expectBody(String.class).returnResult().getResponseBody();
    }

    /**
     * Puts the clock of the limit in the hands of the tests: the application uses this clock
     * instead of the one of the system.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {

        @Bean
        ManualClock loginClock() {
            return new ManualClock();
        }

    }

}
