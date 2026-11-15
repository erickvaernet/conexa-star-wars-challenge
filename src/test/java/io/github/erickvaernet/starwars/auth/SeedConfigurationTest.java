package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Checks the switch that decides whether the test users are loaded.
 *
 * <p>Only the switch is loaded, through the real {@code application.properties}. No database is
 * needed: the tests look at the folders that Flyway is told to read.
 */
class SeedConfigurationTest {

    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(SeedConfiguration.class);

    @Test
    @DisplayName("Without SEED_ENABLED, Flyway keeps reading only the schema folder")
    void seedIsOffByDefault() {
        application.run(context -> assertThat(context).doesNotHaveBean(FlywayConfigurationCustomizer.class));
    }

    @Test
    @DisplayName("With SEED_ENABLED=false, Flyway keeps reading only the schema folder")
    void seedCanBeSwitchedOffExplicitly() {
        application.withPropertyValues("SEED_ENABLED=false")
                .run(context -> assertThat(context).doesNotHaveBean(FlywayConfigurationCustomizer.class));
    }

    @Test
    @DisplayName("With SEED_ENABLED=true, Flyway reads the schema folder and the seed folder")
    void seedAddsItsFolderWhenSwitchedOn() {
        application.withPropertyValues("SEED_ENABLED=true").run(context -> {
            FluentConfiguration flyway = Flyway.configure();

            context.getBean(FlywayConfigurationCustomizer.class).customize(flyway);

            assertThat(flyway.getLocations()).extracting(Location::getDescriptor)
                    .containsExactly("classpath:db/migration", "classpath:db/seed");
        });
    }

}
