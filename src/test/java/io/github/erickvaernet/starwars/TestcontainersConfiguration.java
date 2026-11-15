package io.github.erickvaernet.starwars;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Containers shared by the acceptance tests.
 *
 * <p>Acceptance tests run against a real PostgreSQL instead of an in-memory database,
 * so the schema migrations are exercised exactly as they run in production.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Same major version as the database started by Docker Compose. */
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17-alpine");

    /**
     * Starts PostgreSQL once per test context.
     *
     * <p>{@code @ServiceConnection} makes Spring Boot take the connection details from the
     * container, so the tests need no datasource properties.
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

}
