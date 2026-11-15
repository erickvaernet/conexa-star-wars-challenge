package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Checks the login rules with a real BCrypt encoder and a real token issuer; only the database
 * is replaced.
 */
class LoginServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    /** The lowest BCrypt cost, to keep the tests fast. */
    private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));

    private final UserRepository users = mock(UserRepository.class);

    private LoginService service;

    @BeforeEach
    void setUp() {
        User demo = new User("demo", passwordEncoder.encode("demo1234"), "USER");
        when(users.findByUsername("demo")).thenReturn(Optional.of(demo));
        when(users.findByUsername("nadie")).thenReturn(Optional.empty());
        service = new LoginService(users, passwordEncoder, new TokenIssuer(new JwtProperties(SECRET, 60)));
    }

    @Test
    @DisplayName("The right user name and password get a Bearer token for that user")
    void rightCredentialsGetAToken() {
        LoginResponse response = service.login("demo", "demo1234");

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        Jwt token = read(response.accessToken());
        assertThat(token.getSubject()).isEqualTo("demo");
        assertThat(token.getClaimAsString("role")).isEqualTo("USER");
    }

    @Test
    @DisplayName("A wrong password is rejected")
    void wrongPasswordIsRejected() {
        Throwable error = catchThrowable(() -> service.login("demo", "not-the-password"));

        assertThat(error).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("An unknown user is rejected exactly like a wrong password")
    void unknownUserIsRejectedLikeAWrongPassword() {
        Throwable wrongPassword = catchThrowable(() -> service.login("demo", "not-the-password"));
        Throwable unknownUser = catchThrowable(() -> service.login("nadie", "demo1234"));

        assertThat(unknownUser).isInstanceOf(BadCredentialsException.class);
        assertThat(unknownUser).hasMessage(wrongPassword.getMessage());
    }

    @Test
    @DisplayName("An unknown user still costs a password comparison, so the response time does not reveal it")
    void unknownUserStillCostsAPasswordComparison() {
        catchThrowable(() -> service.login("nadie", "demo1234"));

        verify(passwordEncoder).matches(eq("demo1234"), anyString());
    }

    private static Jwt read(String token) {
        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build().decode(token);
    }

}
