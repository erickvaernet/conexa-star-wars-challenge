package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Acceptance tests for REQ-26: an administrator creates users.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL, with the seed switched
 * on, and looks for SWAPI at the simulated one. Unless a test says otherwise, the request is
 * {@code POST /api/v1/users} with a session of {@code admin}, the administrator that the seed
 * loads. Who else is let in, and who is not, is REQ-25: its tests are in
 * {@link RolesAcceptanceTest}.
 *
 * <p>Other test classes share this database. Before each test, the users that are not the two of
 * the seed are deleted, so that every test starts from "that user does not exist" whatever ran
 * before, and in whatever order.
 *
 * <p>The answers to an invalid body are compared as a whole and strictly: the fields of every
 * error of the API and {@code errors}, nothing else; in {@code errors}, these elements, in this
 * order, each with {@code field} and {@code message} only. The texts are the ones of the table
 * of section 5 of the specification.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class UserCreationAcceptanceTest {

    private static final String USERS = "/api/v1/users";

    private static final String PEOPLE = "/api/v1/people";

    /** A password that follows the rule. The one of the example of the specification. */
    private static final String PASSWORD = "alderaan-1977";

    private static final String USERNAME_RULE =
            "must have 3 to 50 characters: lowercase letters, digits, '.', '_' or '-'";

    private static final String PASSWORD_RULE =
            "must have 8 to 72 characters, not only spaces, and at most 72 bytes in UTF-8";

    private static final String ROLE_RULE = "must be USER or ADMIN";

    /** A BCrypt hash: version, cost, and 53 characters of salt and hash. */
    private static final String BCRYPT_HASH = "\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    /** A client without a session, for the logins and the API documentation. */
    private RestTestClient anonymous;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM users WHERE username NOT IN ('demo', 'admin')");
        anonymous = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("CA-26.1: creating \"leia\" without a role is answered with 201; the body has exactly username, role \"USER\" and createdAt, and there is no Location header")
    void creatingAUserWithoutARoleAnswersWhatWasCreated() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        byte[] bytes = create(admin(), Map.of("username", "leia", "password", PASSWORD))
                .expectStatus().isCreated()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                // There is no request to look a user up, so no address is announced.
                .expectHeader().doesNotExist(HttpHeaders.LOCATION)
                .expectBody()
                .jsonPath("$.username").isEqualTo("leia")
                .jsonPath("$.role").isEqualTo("USER")
                .returnResult().getResponseBody();
        String answer = new String(bytes, StandardCharsets.UTF_8);

        Map<String, Object> created = JsonPath.read(answer, "$");
        // Neither the password nor its hash, under any name.
        assertThat(created).containsOnlyKeys("username", "role", "createdAt");
        assertThat(answer).doesNotContain(PASSWORD).doesNotContain("$2");
        // When it was created: a moment in UTC, written as ISO 8601, which is the one that the
        // database holds.
        assertThat(created.get("createdAt")).isInstanceOf(String.class);
        String createdAt = (String) created.get("createdAt");
        assertThat(createdAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z");
        assertThat(Instant.parse(createdAt)).isBetween(before, Instant.now());
        Timestamp stored = jdbc.queryForObject(
                "SELECT created_at FROM users WHERE username = ?", Timestamp.class, "leia");
        assertThat(Instant.parse(createdAt)).isEqualTo(stored.toInstant());
    }

    @Test
    @DisplayName("CA-26.2: \"leia\", once created, logs in with her password and asks for the list of people: 200 in both, and her token carries the role USER")
    void createdUserLogsInAndUsesTheLists() {
        create(admin(), Map.of("username", "leia", "password", PASSWORD)).expectStatus().isCreated();

        // The login checks that the answer is 200.
        String token = TestSession.login(anonymous, "leia", PASSWORD);

        assertThat(claimsOf(token).read("$.sub", String.class)).isEqualTo("leia");
        assertThat(claimsOf(token).read("$.role", String.class)).isEqualTo("USER");
        TestSession.withToken(anonymous, token).get().uri(PEOPLE).exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("CA-26.3: the row of \"leia\" holds the BCrypt hash of her password, not the password, and the role USER")
    void databaseHoldsTheBcryptHashOfTheCreatedUser() {
        create(admin(), Map.of("username", "leia", "password", PASSWORD)).expectStatus().isCreated();

        Map<String, Object> user = jdbc.queryForMap("SELECT * FROM users WHERE username = ?", "leia");

        String hash = (String) user.get("password_hash");
        assertThat(hash).matches(BCRYPT_HASH);
        // It is the hash of that password, as it arrived.
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, hash)).isTrue();
        assertThat(user.toString()).doesNotContain(PASSWORD);
        assertThat(user.get("role")).isEqualTo("USER");
    }

    @Test
    @DisplayName("CA-26.4: creating \"mon.mothma\" with the role ADMIN is answered with 201 and that role; she logs in, and her own request to create a user is answered with 201")
    void createdAdministratorCanCreateUsers() {
        create(admin(), Map.of("username", "mon.mothma", "password", PASSWORD, "role", "ADMIN"))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.username").isEqualTo("mon.mothma")
                .jsonPath("$.role").isEqualTo("ADMIN");

        String token = TestSession.login(anonymous, "mon.mothma", PASSWORD);
        assertThat(claimsOf(token).read("$.role", String.class)).isEqualTo("ADMIN");

        create(TestSession.withToken(anonymous, token), Map.of("username", "bail.organa", "password", PASSWORD))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.username").isEqualTo("bail.organa")
                .jsonPath("$.role").isEqualTo("USER");
        assertThat(usernames()).containsExactly("admin", "bail.organa", "demo", "mon.mothma");
    }

    @Test
    @DisplayName("CA-26.5: creating \"demo\", which exists, is answered with 409, as Problem Details and with the fixed text; there is still one demo, with the password it had")
    void creatingAUserThatAlreadyExistsIsAConflict() {
        Map<String, Object> demoBefore = jdbc.queryForMap("SELECT * FROM users WHERE username = ?", "demo");
        // Another password and another role: neither may reach the user that exists.
        String anotherPassword = "k4".repeat(10);

        byte[] bytes = create(admin(), Map.of("username", "demo", "password", anotherPassword, "role", "ADMIN"))
                .expectStatus().isEqualTo(409)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Conflict",
                          "status": 409,
                          "detail": "A user with that username already exists.",
                          "instance": "/api/v1/users"
                        }
                        """, JsonCompareMode.STRICT)
                .returnResult().getResponseBody();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotContain("k4k4");
        assertThat(usernames()).containsExactly("admin", "demo");
        // Every column: its password, its role and the moment it was created.
        assertThat(jdbc.queryForMap("SELECT * FROM users WHERE username = ?", "demo")).isEqualTo(demoBefore);
        // The password published in the README still opens its session.
        TestSession.login(anonymous);
    }

    @ParameterizedTest(name = "username \"{0}\"")
    @ValueSource(strings = {"ab", "Leia", "leia organa", "leía"})
    @DisplayName("CA-26.6: a user name that is too short, has a capital letter, a space or an accent is rejected with 400; errors has only the user name, with its fixed text, and no user is created")
    void invalidUserNameIsRejected(String username) {
        create(admin(), Map.of("username", username, "password", PASSWORD))
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json(invalidBody("""
                        {"field": "username", "message": "%s"}
                        """.formatted(USERNAME_RULE)), JsonCompareMode.STRICT);

        assertThat(usernames()).containsExactly("admin", "demo");
    }

    @ParameterizedTest(name = "a password of {0} characters")
    @ValueSource(ints = {7, 73})
    @DisplayName("CA-26.7: a password of 7 characters and one of 73 are rejected with 400; errors names the password, and the answer does not repeat it")
    void passwordThatIsTooShortOrTooLongIsRejected(int length) {
        // Characters that no fixed text has, so that finding them means that they were repeated.
        String password = "k4".repeat(40).substring(0, length);
        assertThat(password).hasSize(length);

        byte[] bytes = create(admin(), Map.of("username", "leia", "password", password))
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json(invalidBody("""
                        {"field": "password", "message": "%s"}
                        """.formatted(PASSWORD_RULE)), JsonCompareMode.STRICT)
                .returnResult().getResponseBody();

        // Neither the whole value nor a piece of it.
        assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotContain("k4k4");
        assertThat(usernames()).containsExactly("admin", "demo");
    }

    @Test
    @DisplayName("CA-26.8: a password of 40 times \"é\", which is 40 characters and 80 bytes, is rejected with 400, and errors names the password")
    void passwordOfMoreThan72BytesIsRejected() {
        // Within the limit of characters and beyond the one of bytes, which is what BCrypt reads.
        String password = "é".repeat(40);
        assertThat(password).hasSize(40);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(80);

        byte[] bytes = create(admin(), Map.of("username", "leia", "password", password))
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json(invalidBody("""
                        {"field": "password", "message": "%s"}
                        """.formatted(PASSWORD_RULE)), JsonCompareMode.STRICT)
                .returnResult().getResponseBody();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotContain("é");
        assertThat(usernames()).containsExactly("admin", "demo");
    }

    @ParameterizedTest(name = "role \"{0}\"")
    // The empty one is the third example of the specification: a role that is sent has to be one of the two.
    @ValueSource(strings = {"admin", "ROOT", ""})
    @DisplayName("CA-26.9: a role that is not exactly USER or ADMIN is rejected with 400; errors has only the role, and it is not corrected")
    void invalidRoleIsRejected(String role) {
        create(admin(), Map.of("username", "leia", "password", PASSWORD, "role", role))
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json(invalidBody("""
                        {"field": "role", "message": "%s"}
                        """.formatted(ROLE_RULE)), JsonCompareMode.STRICT);

        assertThat(usernames()).containsExactly("admin", "demo");
    }

    @Test
    @DisplayName("CA-26.10: with the three fields invalid, the answer is 400 and errors has the three, in the order password, role, username")
    void threeInvalidFieldsAreAllInErrors() {
        create(admin(), Map.of("username", "Leia", "password", "1234567", "role", "ROOT"))
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                // Strict: the order of the list is checked too.
                .expectBody().json(invalidBody("""
                        {"field": "password", "message": "%s"},
                        {"field": "role", "message": "%s"},
                        {"field": "username", "message": "%s"}
                        """.formatted(PASSWORD_RULE, ROLE_RULE, USERNAME_RULE)), JsonCompareMode.STRICT);

        assertThat(usernames()).containsExactly("admin", "demo");
    }

    @ParameterizedTest(name = "a password of {0} characters")
    @ValueSource(ints = {8, 72})
    @DisplayName("CA-26.11: a password of exactly 8 characters and one of exactly 72 are accepted with 201, and each user logs in with its password")
    void shortestAndLongestPasswordsAreAccepted(int length) {
        String password = "k4".repeat(36).substring(0, length);
        assertThat(password).hasSize(length);
        String username = "with-" + length + "-characters";

        create(admin(), Map.of("username", username, "password", password)).expectStatus().isCreated();

        // The login checks that the answer is 200.
        TestSession.login(anonymous, username, password);
    }

    @Test
    @DisplayName("CA-26.12: the API documentation includes the request, with the name createUser, the padlock and its answers 201, 400, 401, 403 and 409; it has exactly ten requests")
    void documentationIncludesTheRequest() {
        String documentation = anonymous.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();

        Map<String, Map<String, Object>> paths = JsonPath.read(documentation, "$.paths");
        List<String> requests = new ArrayList<>();
        paths.forEach((path, methods) -> methods.keySet()
                .forEach(method -> requests.add(method.toUpperCase(Locale.ROOT) + " " + path)));
        assertThat(requests).contains("POST /api/v1/users").hasSize(10);

        Map<String, Object> request = JsonPath.read(documentation, "$.paths['/api/v1/users'].post");
        assertThat(request).containsEntry("operationId", "createUser");
        assertThat(request).containsEntry("tags", List.of("Users"));
        // The padlock: the request requires the Bearer token, by its own declaration or by the
        // one of the whole document, which is how OpenAPI reads it.
        Map<String, Object> schemes = JsonPath.read(documentation, "$.components.securitySchemes");
        assertThat(schemes).hasSize(1);
        String bearer = schemes.keySet().iterator().next();
        Map<String, Object> whole = JsonPath.read(documentation, "$");
        Object security = request.containsKey("security") ? request.get("security") : whole.get("security");
        assertThat(security).isEqualTo(List.of(Map.of(bearer, List.of())));
        // Its answers, and every error as Problem Details.
        Map<String, Object> responses = JsonPath.read(documentation, "$.paths['/api/v1/users'].post.responses");
        assertThat(responses).containsOnlyKeys("201", "400", "401", "403", "409");
        for (String error : List.of("400", "401", "403", "409")) {
            Map<String, Object> content = JsonPath.read(documentation,
                    "$.paths['/api/v1/users'].post.responses['" + error + "'].content");
            assertThat(content).as(error).containsOnlyKeys(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        }
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"this is not JSON", ""})
    @DisplayName("A body that is not JSON, or that is missing, is rejected with 400, as Problem Details and without errors")
    void bodyThatIsNotJsonOrIsMissingHasNoErrors(String body) {
        byte[] bytes = admin().post().uri(USERS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Bad Request")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.instance").isEqualTo(USERS)
                .returnResult().getResponseBody();

        // No field failed: the body could not even be read. So there is no list, not even an
        // empty one.
        Map<String, Object> problem = JsonPath.read(new String(bytes, StandardCharsets.UTF_8), "$");
        assertThat(problem).containsOnlyKeys("type", "title", "status", "detail", "instance");
        assertThat(usernames()).containsExactly("admin", "demo");
    }

    @Test
    @DisplayName("A password of eight spaces is rejected with 400, and errors names the password: the login does not take a password of spaces only, so that user could never log in")
    void passwordOfSpacesOnlyIsRejected() {
        create(admin(), Map.of("username", "leia", "password", " ".repeat(8)))
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json(invalidBody("""
                        {"field": "password", "message": "%s"}
                        """.formatted(PASSWORD_RULE)), JsonCompareMode.STRICT);

        assertThat(usernames()).containsExactly("admin", "demo");
    }

    @Test
    @DisplayName("A password with spaces inside and at both ends is accepted with 201 and stored as it arrived, and the user logs in with it: the spaces count and are not trimmed")
    void passwordWithSpacesIsStoredAsItArrived() {
        String password = "  two words  ";

        create(admin(), Map.of("username", "leia", "password", password)).expectStatus().isCreated();

        // It is the hash of the password with its spaces, not of the trimmed one.
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, "leia");
        assertThat(new BCryptPasswordEncoder().matches(password, hash)).isTrue();
        assertThat(new BCryptPasswordEncoder().matches(password.strip(), hash)).isFalse();
        // The login checks that the answer is 200.
        TestSession.login(anonymous, "leia", password);
    }

    @Test
    @DisplayName("Fields that the request does not have, like id or createdAt, are ignored")
    void fieldsThatTheRequestDoesNotHaveAreIgnored() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        // The identifier that is sent is the one of a user that exists: if it were read, the
        // request would be about that user.
        Map<String, Object> demoBefore = jdbc.queryForMap("SELECT * FROM users WHERE username = ?", "demo");

        byte[] bytes = admin().post().uri(USERS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "id": %s,
                          "username": "leia",
                          "password": "%s",
                          "role": "USER",
                          "createdAt": "1977-05-25T00:00:00Z",
                          "passwordHash": "not-a-hash"
                        }
                        """.formatted(demoBefore.get("id"), PASSWORD))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.username").isEqualTo("leia")
                .jsonPath("$.role").isEqualTo("USER")
                .returnResult().getResponseBody();

        // What was sent in those fields reached neither the answer nor the database.
        Map<String, Object> created = JsonPath.read(new String(bytes, StandardCharsets.UTF_8), "$");
        assertThat(created).containsOnlyKeys("username", "role", "createdAt");
        assertThat(Instant.parse((String) created.get("createdAt"))).isBetween(before, Instant.now());
        Map<String, Object> user = jdbc.queryForMap("SELECT * FROM users WHERE username = ?", "leia");
        assertThat(user.get("id")).isNotEqualTo(demoBefore.get("id"));
        assertThat((String) user.get("password_hash")).matches(BCRYPT_HASH);
        assertThat(((Timestamp) user.get("created_at")).toInstant()).isBetween(before, Instant.now());
        assertThat(jdbc.queryForMap("SELECT * FROM users WHERE username = ?", "demo")).isEqualTo(demoBefore);
    }

    @Test
    @DisplayName("Requests to create the same user that arrive at once: one is answered with 201 and the others with 409, and there is one user")
    void sameUserCreatedAtOnceIsCreatedOnce() throws Exception {
        int requests = 4;
        RestTestClient admin = admin();
        Callable<Integer> request = () -> create(admin, Map.of("username", "leia", "password", PASSWORD))
                .expectBody(String.class).returnResult().getStatus().value();
        List<Callable<Integer>> atOnce = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            atOnce.add(request);
        }

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService threads = Executors.newFixedThreadPool(requests)) {
            for (Future<Integer> answer : threads.invokeAll(atOnce)) {
                statuses.add(answer.get());
            }
        }

        // Whichever request wins, and whether the others find the user when they look for it or
        // the database refuses their row: never a 500, never two users.
        assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE username = ?", Integer.class, "leia"))
                .isEqualTo(1);
    }

    /** Logs in as the administrator of the seed and returns a client that sends its token. */
    private RestTestClient admin() {
        return TestSession.openAsAdmin(anonymous);
    }

    /** Asks to create a user. The client writes the JSON and escapes what needs it. */
    private static RestTestClient.ResponseSpec create(RestTestClient whoAsks, Map<String, String> body) {
        return whoAsks.post().uri(USERS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    /** The whole answer to a body with invalid fields, with these elements in {@code errors}. */
    private static String invalidBody(String errors) {
        return """
                {
                  "type": "about:blank",
                  "title": "Bad Request",
                  "status": 400,
                  "detail": "Invalid request content.",
                  "instance": "/api/v1/users",
                  "errors": [
                    %s
                  ]
                }
                """.formatted(errors);
    }

    /** The name of every user of the database, sorted. */
    private List<String> usernames() {
        return jdbc.queryForList("SELECT username FROM users ORDER BY username", String.class);
    }

    /** The claims of a token: the second of its three Base64URL parts, which are separated by dots. */
    private static DocumentContext claimsOf(String token) {
        String claims = token.split("\\.")[1];
        return JsonPath.parse(new String(Base64.getUrlDecoder().decode(claims), StandardCharsets.UTF_8));
    }

}
