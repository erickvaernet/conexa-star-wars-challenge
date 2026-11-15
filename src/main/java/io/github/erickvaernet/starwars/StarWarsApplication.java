package io.github.erickvaernet.starwars;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the Star Wars API.
 *
 * <p>The application reads Star Wars data from SWAPI, paginates and filters it the same way for
 * every entity, and protects it with a login. It stores no Star Wars data: its database only
 * holds users.
 */
@SpringBootApplication
// Finds the settings classes of every package, such as the token settings.
@ConfigurationPropertiesScan
public class StarWarsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StarWarsApplication.class, args);
    }

}
