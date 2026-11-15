package io.github.erickvaernet.starwars.auth;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the tokens, read from the environment at start-up.
 *
 * <p>An invalid value stops the application from starting: a missing or weak signing key must
 * never go unnoticed until the first login.
 *
 * @param secret     the key that signs the tokens, from {@code JWT_SECRET}
 * @param ttlMinutes how long a token is valid, from {@code JWT_TTL_MINUTES}
 */
@ConfigurationProperties("auth.jwt")
record JwtProperties(String secret, long ttlMinutes) {

    /** HS256 needs a key of at least 256 bits, which is 32 characters. */
    private static final int MIN_SECRET_LENGTH = 32;

    JwtProperties {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("JWT_SECRET is mandatory and needs at least "
                    + MIN_SECRET_LENGTH + " characters: it is the key that signs the tokens.");
        }
        if (ttlMinutes < 1) {
            throw new IllegalArgumentException("JWT_TTL_MINUTES must be 1 or more.");
        }
    }

    /**
     * The secret as an HS256 key.
     *
     * <p>The login signs with it and every request is validated with it. Both take it from here,
     * so they cannot end up with different keys.
     */
    SecretKey signingKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

}
