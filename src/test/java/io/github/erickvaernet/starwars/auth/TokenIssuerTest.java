package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Checks the tokens that the application issues, by reading them back with the same key.
 */
class TokenIssuerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final TokenIssuer issuer = new TokenIssuer(new JwtProperties(SECRET, 60));

    @Test
    @DisplayName("The token carries the user name and the role")
    void tokenCarriesTheUserNameAndTheRole() {
        Jwt token = read(issuer.issue("demo", "USER"), SECRET);

        assertThat(token.getSubject()).isEqualTo("demo");
        assertThat(token.getClaimAsString("role")).isEqualTo("USER");
    }

    @Test
    @DisplayName("The token is issued now and expires after the configured minutes")
    void tokenExpiresAfterTheConfiguredMinutes() {
        Jwt token = read(issuer.issue("demo", "USER"), SECRET);

        assertThat(token.getIssuedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
        assertThat(Duration.between(token.getIssuedAt(), token.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
        assertThat(issuer.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    @DisplayName("The token is signed with HS256")
    void tokenIsSignedWithHs256() {
        Jwt token = read(issuer.issue("demo", "USER"), SECRET);

        assertThat(token.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    @DisplayName("A token cannot be read with another key")
    void tokenCannotBeReadWithAnotherKey() {
        String token = issuer.issue("demo", "USER");

        assertThatThrownBy(() -> read(token, "another-key-another-key-another-key"))
                .isInstanceOf(JwtException.class);
    }

    /** Validates the signature and the expiry of a token, and returns its content. */
    private static Jwt read(String token, String secret) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().decode(token);
    }

}
