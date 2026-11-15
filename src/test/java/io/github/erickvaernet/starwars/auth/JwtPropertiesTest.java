package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Checks how the token settings are read at start-up.
 *
 * <p>Only the settings are loaded, through the real {@code application.properties}, so the tests
 * cover the whole path from the environment variable to the value the application uses. They need
 * neither the database nor Docker.
 */
class JwtPropertiesTest {

    private static final String SECRET_OF_32_CHARACTERS = "0123456789abcdef0123456789abcdef";

    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Settings.class);

    @Test
    @DisplayName("The application does not start without JWT_SECRET, and says why")
    void applicationDoesNotStartWithoutSecret() {
        application.withPropertyValues("JWT_SECRET=")
                .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("JWT_SECRET"));
    }

    @Test
    @DisplayName("The application does not start with a JWT_SECRET shorter than 32 characters")
    void applicationDoesNotStartWithAShortSecret() {
        application.withPropertyValues("JWT_SECRET=" + SECRET_OF_32_CHARACTERS.substring(1))
                .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("JWT_SECRET"));
    }

    @Test
    @DisplayName("With a JWT_SECRET of 32 characters the application starts, and tokens last 60 minutes")
    void tokensLast60MinutesByDefault() {
        application.withPropertyValues("JWT_SECRET=" + SECRET_OF_32_CHARACTERS)
                .run(context -> {
                    JwtProperties properties = context.getBean(JwtProperties.class);
                    assertThat(properties.secret()).isEqualTo(SECRET_OF_32_CHARACTERS);
                    assertThat(properties.ttlMinutes()).isEqualTo(60);
                });
    }

    @Test
    @DisplayName("JWT_TTL_MINUTES changes how long tokens last")
    void ttlCanBeChanged() {
        application.withPropertyValues("JWT_TTL_MINUTES=5")
                .run(context -> assertThat(context.getBean(JwtProperties.class).ttlMinutes()).isEqualTo(5));
    }

    @Test
    @DisplayName("The application does not start with a JWT_TTL_MINUTES below 1")
    void applicationDoesNotStartWithATtlBelowOne() {
        application.withPropertyValues("JWT_TTL_MINUTES=0")
                .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("JWT_TTL_MINUTES"));
    }

    @EnableConfigurationProperties(JwtProperties.class)
    static class Settings {
    }

}
