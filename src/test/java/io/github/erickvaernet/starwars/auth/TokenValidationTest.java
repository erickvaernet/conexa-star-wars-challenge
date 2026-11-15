package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Checks the validation that every request with a token goes through: the signature and the
 * expiry.
 *
 * <p>It uses the validator of the application on its own, without the web layer. The tokens that
 * must be rejected are signed here, because the application only issues valid ones.
 */
class TokenValidationTest {

    /** 64 characters: long enough to sign with HS512 too, which needs a longer key than HS256. */
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static final String ANOTHER_SECRET = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

    private final JwtProperties properties = new JwtProperties(SECRET, 60);

    private final JwtDecoder validator = new SecurityConfiguration().tokenValidator(properties);

    @Test
    @DisplayName("A token issued by the application is accepted, with its user name and its role")
    void tokenIssuedByTheApplicationIsAccepted() {
        Jwt token = validator.decode(new TokenIssuer(properties).issue("demo", "USER"));

        assertThat(token.getSubject()).isEqualTo("demo");
        assertThat(token.getClaimAsString("role")).isEqualTo("USER");
    }

    @Test
    @DisplayName("A token signed with another key is rejected")
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = sign(ANOTHER_SECRET, MacAlgorithm.HS256, inOneHour());

        assertThatThrownBy(() -> validator.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("A token signed with the right key and another algorithm is rejected")
    void tokenSignedWithAnotherAlgorithmIsRejected() {
        String token = sign(SECRET, MacAlgorithm.HS512, inOneHour());

        assertThatThrownBy(() -> validator.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("A token that declares no signature is rejected")
    void tokenWithoutSignatureIsRejected() {
        // A JWT is three Base64URL parts separated by dots: header, claims and signature.
        String[] parts = sign(SECRET, MacAlgorithm.HS256, inOneHour()).split("\\.");
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String token = header + "." + parts[1] + ".";

        assertThatThrownBy(() -> validator.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("A token that is about to expire is still accepted")
    void tokenAboutToExpireIsAccepted() {
        String token = sign(SECRET, MacAlgorithm.HS256, Instant.now().plus(1, ChronoUnit.MINUTES));

        assertThat(validator.decode(token).getSubject()).isEqualTo("demo");
    }

    @Test
    @DisplayName("An expired token is rejected")
    void expiredTokenIsRejected() {
        String token = sign(SECRET, MacAlgorithm.HS256, Instant.now().minus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> validator.decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("A token that expired a few seconds ago is rejected: the expiry has no tolerance")
    void expiryHasNoTolerance() {
        String token = sign(SECRET, MacAlgorithm.HS256, Instant.now().minus(5, ChronoUnit.SECONDS));

        assertThatThrownBy(() -> validator.decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    private static Instant inOneHour() {
        return Instant.now().plus(1, ChronoUnit.HOURS);
    }

    /** Signs a token with the content that the application gives to its own, issued one hour before it expires. */
    private static String sign(String secret, MacAlgorithm algorithm, Instant expiresAt) {
        JwtEncoder encoder = NimbusJwtEncoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .algorithm(algorithm)
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("demo")
                .claim("role", "USER")
                .issuedAt(expiresAt.minus(1, ChronoUnit.HOURS))
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(), claims)).getTokenValue();
    }

}
