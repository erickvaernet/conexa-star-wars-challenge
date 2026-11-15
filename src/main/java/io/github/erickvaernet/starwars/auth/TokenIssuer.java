package io.github.erickvaernet.starwars.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Issues the tokens that a user gets when logging in.
 *
 * <p>A token is a JWT signed with HS256: the same key signs it here and validates it on each
 * request, so the server keeps no session. The signing itself is done by Spring Security.
 */
@Component
class TokenIssuer {

    private final JwtEncoder encoder;

    private final Duration ttl;

    TokenIssuer(JwtProperties properties) {
        SecretKeySpec key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        this.ttl = Duration.ofMinutes(properties.ttlMinutes());
    }

    /**
     * Issues a token for a user that has just proved who they are.
     *
     * @return the signed token, with the user name in {@code sub}, the role in {@code role}, and
     *         the times of issue and expiry in {@code iat} and {@code exp}
     */
    String issue(String username, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Seconds until a token issued now expires. */
    long expiresInSeconds() {
        return ttl.toSeconds();
    }

}
