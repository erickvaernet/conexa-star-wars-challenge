package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.github.erickvaernet.starwars.StarWarsApplication;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.TestSession;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Acceptance tests for REQ-25: there are two roles, and one request is kept for the administrators.
 *
 * <p>The application runs on a real HTTP port against a real PostgreSQL that was empty when it
 * started, with the seed switched on, and looks for SWAPI at the simulated one. The two test
 * users are the ones that the seed loads: {@code demo}, a common user, and {@code admin}, an
 * administrator.
 *
 * <p>The request that is kept for the administrators is the one that creates a user,
 * {@code POST /api/v1/users}. What it does for an administrator is REQ-26, and its tests are in
 * {@link UserCreationAcceptanceTest}; the tests here are about who is let in.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import({TestcontainersConfiguration.class, SwapiStubConfiguration.class})
class RolesAcceptanceTest {

    private static final String USERS = "/api/v1/users";

    private static final String PEOPLE = "/api/v1/people";

    private static final String DEMO = "demo";

    /** The passwords published in the README. The database only holds their hashes. */
    private static final String DEMO_PASSWORD = "demo1234";

    private static final String ADMIN = "admin";

    private static final String ADMIN_PASSWORD = "admin1234";

    /** A BCrypt hash: version, cost, and 53 characters of salt and hash. */
    private static final String BCRYPT_HASH = "\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}";

    /** A body that an administrator would get a 201 for. */
    private static final String VALID_BODY = """
            {"username": "wedge", "password": "red-two-1977"}
            """;

    /**
     * The seed as version 0.1.0 had it, when the only test user was {@code demo}: a copy of that
     * file, kept with the tests under the name it had.
     */
    private static final String SEED_OF_VERSION_0_1_0 = "classpath:seed-v0.1.0";

    @LocalServerPort
    private int port;

    /** The signing key of the tests, from {@code src/test/resources/config/application.properties}. */
    @Value("${JWT_SECRET}")
    private String signingKey;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PostgreSQLContainer postgres;

    /** A client without a session. */
    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("CA-25.1: starting on an empty database with the seed on creates demo, with the role USER, and admin, with the role ADMIN; both passwords are BCrypt hashes")
    void startingOnAnEmptyDatabaseCreatesTheTwoTestUsers() {
        List<Map<String, Object>> testUsers = jdbc.queryForList(
                "SELECT * FROM users WHERE username IN (?, ?) ORDER BY username", ADMIN, DEMO);

        assertThat(testUsers)
                .extracting(user -> user.get("username"), user -> user.get("role"))
                .containsExactly(tuple(ADMIN, "ADMIN"), tuple(DEMO, "USER"));
        for (Map<String, Object> user : testUsers) {
            assertThat((String) user.get("password_hash")).as("%s", user.get("username")).matches(BCRYPT_HASH);
            assertThat(user.toString()).doesNotContain(DEMO_PASSWORD).doesNotContain(ADMIN_PASSWORD);
        }
    }

    @Test
    @DisplayName("CA-25.2: each test user logs in with its password; the token of demo carries the role USER and the one of admin, the role ADMIN")
    void eachTestUserLogsInAndItsTokenCarriesItsRole() {
        // Both logins check that the answer is 200.
        DocumentContext claimsOfDemo = claimsOf(TestSession.login(client, DEMO, DEMO_PASSWORD));
        DocumentContext claimsOfAdmin = claimsOf(TestSession.login(client, ADMIN, ADMIN_PASSWORD));

        assertThat(claimsOfDemo.read("$.sub", String.class)).isEqualTo(DEMO);
        assertThat(claimsOfDemo.read("$.role", String.class)).isEqualTo("USER");
        assertThat(claimsOfAdmin.read("$.sub", String.class)).isEqualTo(ADMIN);
        assertThat(claimsOfAdmin.read("$.role", String.class)).isEqualTo("ADMIN");
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/api/v1/people",
        "/api/v1/films",
        "/api/v1/starships",
        "/api/v1/vehicles",
        "/api/v1/people/1"
    })
    @DisplayName("CA-25.3: with a session of admin, the four lists and a detail are answered with 200: both roles can use them")
    void administratorCanUseTheListsAndTheDetails(String path) {
        TestSession.openAsAdmin(client).get().uri(path).exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("CA-25.4: a restart neither fails nor duplicates any of the two test users")
    void restartNeitherFailsNorDuplicatesTheTestUsers() {
        // The application under test already started once against this database. A second
        // instance on the same database goes through what a restart does: the migrations and the
        // seed run again. It gets the database through the same variables used outside the tests.
        try (ConfigurableApplicationContext restarted = start(postgres.getJdbcUrl())) {
            assertThat(restarted.isRunning()).isTrue();
        }

        assertThat(jdbc.queryForList("SELECT username FROM users WHERE username IN (?, ?) ORDER BY username",
                String.class, ADMIN, DEMO)).containsExactly(ADMIN, DEMO);
    }

    @Test
    @DisplayName("CA-25.5: on a database that already has demo and does not have admin, starting with the seed on creates admin and leaves the row of demo as it was")
    void startingOnADatabaseThatAlreadyHasDemoCreatesAdminAndKeepsDemo() {
        // A database as version 0.1.0 left it: the schema, and the seed of that version, which
        // loaded demo alone. It is built with Flyway, the way that version did it, so the
        // database remembers which seed file it ran.
        String url = newDatabase("left_by_version_0_1_0");
        Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration", SEED_OF_VERSION_0_1_0)
                .load()
                .migrate();
        JdbcTemplate database = new JdbcTemplate(
                new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword()));
        assertThat(database.queryForList("SELECT username FROM users", String.class)).containsExactly(DEMO);
        Map<String, Object> demoBefore = database.queryForMap("SELECT * FROM users WHERE username = ?", DEMO);

        try (ConfigurableApplicationContext started = start(url)) {
            assertThat(started.isRunning()).isTrue();
        }

        List<Map<String, Object>> admins = database.queryForList("SELECT * FROM users WHERE username = ?", ADMIN);
        assertThat(admins).hasSize(1);
        assertThat(admins.getFirst().get("role")).isEqualTo("ADMIN");
        assertThat((String) admins.getFirst().get("password_hash")).matches(BCRYPT_HASH);
        // Every column: neither its password, nor its role, nor the moment it was created.
        assertThat(database.queryForMap("SELECT * FROM users WHERE username = ?", DEMO)).isEqualTo(demoBefore);
        assertThat(database.queryForList("SELECT username FROM users ORDER BY username", String.class))
                .containsExactly(ADMIN, DEMO);
    }

    @Test
    @DisplayName("CA-25.6: with a session of demo, creating a user with a valid body is answered with 403, as Problem Details, with the fixed text and without WWW-Authenticate; no user is created")
    void commonUserIsNotAllowedToCreateUsers() {
        List<String> usersBefore = usernames();

        expectForbidden(create(TestSession.open(client), VALID_BODY));

        assertThat(usernames()).isEqualTo(usersBefore);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "{\"username\": \"ab\", \"password\": \"1234567\", \"role\": \"ROOT\"}",
        "{}",
        "this is not JSON"
    })
    @DisplayName("CA-25.7: with a session of demo, creating a user with an invalid body is answered with 403, not with 400: the role is checked before the content")
    void commonUserGetsTheSameAnswerWhateverTheBody(String invalidBody) {
        List<String> usersBefore = usernames();

        // The very same answer as with a valid body: nothing of the body was looked at.
        expectForbidden(create(TestSession.open(client), invalidBody));

        assertThat(usernames()).isEqualTo(usersBefore);
    }

    @Test
    @DisplayName("CA-25.8: without a session, or with an expired token, creating a user is answered with 401, the same one as CA-10.1")
    void creatingAUserWithoutASessionOrWithAnExpiredTokenIsNotAuthenticated() {
        List<String> usersBefore = usernames();
        // Nobody can wait for a real token to expire: this one is signed as already expired. It
        // is what the login issued to admin two hours ago, with its lifetime of one hour.
        Instant now = Instant.now();
        String expired = sign(ADMIN, "ADMIN", now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));
        // A token signed here the same way, and not expired, is as good as one from the login:
        // the expiry is the only reason why the other one is rejected.
        String notExpired = sign(ADMIN, "ADMIN", now, now.plus(1, ChronoUnit.HOURS));
        TestSession.withToken(client, notExpired).get().uri(PEOPLE).exchange().expectStatus().isOk();

        String withoutSession = expectNotAuthenticated(create(client, VALID_BODY));
        String withAnExpiredToken = expectNotAuthenticated(create(TestSession.withToken(client, expired), VALID_BODY));

        // Nothing in the answer may tell why the request was rejected.
        assertThat(withAnExpiredToken).isEqualTo(withoutSession);
        assertThat(usernames()).isEqualTo(usersBefore);
    }

    /** Asks to create a user, with the body written as it is sent. */
    private static RestTestClient.ResponseSpec create(RestTestClient whoAsks, String body) {
        return whoAsks.post().uri(USERS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    /**
     * Checks the 403 as a whole and strictly: the fields of every error of the API, with the text
     * of the specification, and nothing else. Unlike a 401, it does not say how to authenticate:
     * whoever asked already did.
     */
    private static void expectForbidden(RestTestClient.ResponseSpec response) {
        response
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Forbidden",
                          "status": 403,
                          "detail": "You do not have permission to perform this request.",
                          "instance": "/api/v1/users"
                        }
                        """, JsonCompareMode.STRICT);
    }

    /**
     * Checks the 401 of every request without a session, as a whole and strictly, and returns its
     * body. It is the one of CA-10.1, with the address of this request.
     */
    private static String expectNotAuthenticated(RestTestClient.ResponseSpec response) {
        byte[] body = response
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Unauthorized",
                          "status": 401,
                          "detail": "Authentication is required to access this resource.",
                          "instance": "/api/v1/users"
                        }
                        """, JsonCompareMode.STRICT)
                .returnResult().getResponseBody();
        return new String(body, StandardCharsets.UTF_8);
    }

    /** The name of every user of the database of the application under test, sorted. */
    private List<String> usernames() {
        return jdbc.queryForList("SELECT username FROM users ORDER BY username", String.class);
    }

    /**
     * Starts another instance of the application, with the seed on, against a database of the
     * PostgreSQL of the tests.
     */
    private ConfigurableApplicationContext start(String databaseUrl) {
        return new SpringApplicationBuilder(StarWarsApplication.class)
                .run("--server.port=0",
                        "--SEED_ENABLED=true",
                        "--DB_URL=" + databaseUrl,
                        "--DB_USER=" + postgres.getUsername(),
                        "--DB_PASSWORD=" + postgres.getPassword());
    }

    /** Creates an empty database in the PostgreSQL of the tests and returns its address. */
    private String newDatabase(String name) {
        jdbc.execute("DROP DATABASE IF EXISTS " + name);
        jdbc.execute("CREATE DATABASE " + name);
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + name;
    }

    /**
     * Signs a token the way the application does: HS256, with the user name, the role and the
     * times of issue and expiry.
     */
    private String sign(String username, String role, Instant issuedAt, Instant expiresAt) {
        JwtEncoder encoder = NimbusJwtEncoder
                .withSecretKey(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256)
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** The claims of a token: the second of its three Base64URL parts, which are separated by dots. */
    private static DocumentContext claimsOf(String token) {
        String claims = token.split("\\.")[1];
        return JsonPath.parse(new String(Base64.getUrlDecoder().decode(claims), StandardCharsets.UTF_8));
    }

}
