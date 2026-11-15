package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erickvaernet.starwars.StarWarsApplication;
import io.github.erickvaernet.starwars.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Acceptance tests for REQ-11: the test users are ready as soon as the project starts.
 *
 * <p>The application starts with the seed switched on against a real PostgreSQL that is empty,
 * and the tests look at what it left in the database.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SEED_ENABLED=true")
@Import(TestcontainersConfiguration.class)
class SeedAcceptanceTest {

    private static final String USERNAME = "demo";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PostgreSQLContainer postgres;

    @Test
    @DisplayName("CA-11.1: starting on an empty database creates the tables and the test user")
    void startingOnAnEmptyDatabaseCreatesTheTablesAndTheTestUser() {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'users'", String.class);
        assertThat(columns).containsExactlyInAnyOrder("id", "username", "password_hash", "role", "created_at");

        Map<String, Object> user = jdbc.queryForMap("SELECT * FROM users WHERE username = ?", USERNAME);
        assertThat(user.get("role")).isEqualTo("USER");
        assertThat(user.get("created_at")).isNotNull();
    }

    @Test
    @DisplayName("CA-11.2: a restart neither fails nor duplicates the test user")
    void restartNeitherFailsNorDuplicatesTheTestUser() {
        // The application under test already started once against this database. A second
        // instance on the same database goes through what a restart does: the migrations and the
        // seed run again. It gets the database through the same variables used outside the tests.
        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(StarWarsApplication.class)
                .run("--server.port=0",
                        "--SEED_ENABLED=true",
                        "--DB_URL=" + postgres.getJdbcUrl(),
                        "--DB_USER=" + postgres.getUsername(),
                        "--DB_PASSWORD=" + postgres.getPassword())) {
            assertThat(restarted.isRunning()).isTrue();
        }

        Integer testUsers = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE username = ?", Integer.class, USERNAME);
        assertThat(testUsers).isEqualTo(1);
    }

}
