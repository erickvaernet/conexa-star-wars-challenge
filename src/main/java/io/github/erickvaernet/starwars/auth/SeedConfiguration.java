package io.github.erickvaernet.starwars.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Switch for the test users.
 *
 * <p>The schema and the test users are in separate folders. Flyway always reads the schema
 * folder; it reads the seed folder too only when {@code SEED_ENABLED} is true, so a real
 * environment never gets users whose password is public.
 */
@Configuration(proxyBeanMethods = false)
class SeedConfiguration {

    private static final String SCHEMA_FOLDER = "classpath:db/migration";

    private static final String SEED_FOLDER = "classpath:db/seed";

    @Bean
    @ConditionalOnProperty(name = "auth.seed.enabled", havingValue = "true")
    FlywayConfigurationCustomizer seedFolder() {
        return flyway -> flyway.locations(SCHEMA_FOLDER, SEED_FOLDER);
    }

}
