package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.erickvaernet.starwars.shared.TooManyLoginAttemptsException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Checks the login rules with a real BCrypt encoder, a real token issuer and the real count of
 * failed attempts, with its default settings; only the database is replaced.
 */
class LoginServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    /** The source address of the logins of a test, and another one. */
    private static final String ADDRESS = "203.0.113.7";

    private static final String ANOTHER_ADDRESS = "203.0.113.8";

    /** The lowest BCrypt cost, to keep the tests fast. */
    private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));

    private final UserRepository users = mock(UserRepository.class);

    private LoginService service;

    @BeforeEach
    void setUp() {
        User demo = new User("demo", passwordEncoder.encode("demo1234"), "USER");
        when(users.findByUsername("demo")).thenReturn(Optional.of(demo));
        when(users.findByUsername("nadie")).thenReturn(Optional.empty());
        LoginAttemptLimiter attempts = new LoginAttemptLimiter(
                new LoginAttemptProperties(5, Duration.ofMinutes(15)), Optional.empty());
        service = new LoginService(users, passwordEncoder, new TokenIssuer(new JwtProperties(SECRET, 60)), attempts);
    }

    @Test
    @DisplayName("The right user name and password get a Bearer token for that user")
    void rightCredentialsGetAToken() {
        LoginResponse response = service.login("demo", "demo1234", ADDRESS);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        Jwt token = read(response.accessToken());
        assertThat(token.getSubject()).isEqualTo("demo");
        assertThat(token.getClaimAsString("role")).isEqualTo("USER");
    }

    @Test
    @DisplayName("A wrong password is rejected")
    void wrongPasswordIsRejected() {
        Throwable error = catchThrowable(() -> service.login("demo", "not-the-password", ADDRESS));

        assertThat(error).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("An unknown user is rejected exactly like a wrong password")
    void unknownUserIsRejectedLikeAWrongPassword() {
        Throwable wrongPassword = catchThrowable(() -> service.login("demo", "not-the-password", ADDRESS));
        Throwable unknownUser = catchThrowable(() -> service.login("nadie", "demo1234", ADDRESS));

        assertThat(unknownUser).isInstanceOf(BadCredentialsException.class);
        assertThat(unknownUser).hasMessage(wrongPassword.getMessage());
    }

    @Test
    @DisplayName("An unknown user still costs a password comparison, so the response time does not reveal it")
    void unknownUserStillCostsAPasswordComparison() {
        catchThrowable(() -> service.login("nadie", "demo1234", ADDRESS));

        verify(passwordEncoder).matches(eq("demo1234"), anyString());
    }

    @Test
    @DisplayName("For an unknown user, the password is compared with a real BCrypt hash, which is what takes the time")
    void unknownUserIsComparedWithARealHash() {
        catchThrowable(() -> service.login("nadie", "demo1234", ADDRESS));

        // Compared with a text that is not a hash, BCrypt answers at once: the unknown user would
        // be the fast case again, and the response time would tell which users exist.
        verify(passwordEncoder).matches(eq("demo1234"), argThat(hash -> hash.matches("\\$2[aby]\\$\\d{2}\\$.{53}")));
    }

    @Test
    @DisplayName("After five failed logins of an address, a login from it is rejected without looking for the user or comparing the password, even with the right credentials")
    void blockedLoginIsNotChecked() {
        failLogin(ADDRESS, 5);
        // The five failed logins looked for the user and compared: only what comes next counts.
        clearInvocations(users, passwordEncoder);

        Throwable error = catchThrowable(() -> service.login("demo", "demo1234", ADDRESS));

        assertThat(error).isInstanceOf(TooManyLoginAttemptsException.class);
        assertThat(((TooManyLoginAttemptsException) error).retryAfterSeconds()).isBetween(1L, 900L);
        verify(users, never()).findByUsername(anyString());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("The five failed logins that block an address are rejected as always: the fifth one is not told apart")
    void fiveFailedLoginsAreRejectedAsAlways() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(catchThrowable(() -> service.login("demo", "not-the-password", ADDRESS)))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    @Test
    @DisplayName("A correct login does not clear the failures of its address: after four failures and a correct login, one more failure blocks it")
    void correctLoginDoesNotClearTheFailures() {
        failLogin(ADDRESS, 4);
        service.login("demo", "demo1234", ADDRESS);
        failLogin(ADDRESS, 1);

        Throwable error = catchThrowable(() -> service.login("demo", "demo1234", ADDRESS));

        assertThat(error).isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("Correct logins do not add to the count: an address that logs in well many times is never blocked")
    void correctLoginsAreNeverBlocked() {
        for (int login = 1; login <= 50; login++) {
            assertThat(service.login("demo", "demo1234", ADDRESS).accessToken()).isNotBlank();
        }
    }

    @Test
    @DisplayName("The failures are counted by address and not by user: an unknown user counts like a wrong password, and another address is not affected")
    void failuresAreCountedByAddressNotByUser() {
        catchThrowable(() -> service.login("demo", "not-the-password", ADDRESS));
        catchThrowable(() -> service.login("demo", "not-the-password", ADDRESS));
        catchThrowable(() -> service.login("nadie", "demo1234", ADDRESS));
        catchThrowable(() -> service.login("nadie", "demo1234", ADDRESS));
        catchThrowable(() -> service.login("nadie", "not-the-password", ADDRESS));

        assertThat(catchThrowable(() -> service.login("demo", "demo1234", ADDRESS)))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThat(service.login("demo", "demo1234", ANOTHER_ADDRESS).accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("A login that ends in an unexpected error is not a failed login: it does not add to the count")
    void unexpectedErrorDoesNotCountAsAFailedLogin() {
        when(users.findByUsername("leia")).thenThrow(new IllegalStateException("the database is down"));

        for (int attempt = 1; attempt <= 10; attempt++) {
            assertThat(catchThrowable(() -> service.login("leia", "alderaan-1977", ADDRESS)))
                    .isInstanceOf(IllegalStateException.class);
        }

        // The five places of the address are still free.
        failLogin(ADDRESS, 5);
        assertThat(catchThrowable(() -> service.login("demo", "demo1234", ADDRESS)))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("Of forty failed logins that arrive at once from one address, only five get to compare a password")
    void simultaneousFailedLoginsDoNotGetPastTheLimit() throws Exception {
        // Each comparison takes long enough for the forty logins to be inside the login at once.
        // If the attempts were counted after the comparison, all forty would get to compare.
        doAnswer(comparison -> {
            Thread.sleep(200);
            return comparison.callRealMethod();
        }).when(passwordEncoder).matches(anyString(), anyString());
        int logins = 40;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> outcomes = new ArrayList<>();

        try (ExecutorService threads = Executors.newFixedThreadPool(logins)) {
            for (int login = 1; login <= logins; login++) {
                outcomes.add(threads.submit(() -> {
                    start.await();
                    return catchThrowable(() -> service.login("demo", "not-the-password", ADDRESS));
                }));
            }
            start.countDown();
        }

        List<Throwable> errors = new ArrayList<>();
        for (Future<Throwable> outcome : outcomes) {
            errors.add(outcome.get());
        }
        verify(passwordEncoder, times(5)).matches(anyString(), anyString());
        assertThat(errors).filteredOn(BadCredentialsException.class::isInstance).hasSize(5);
        assertThat(errors).filteredOn(TooManyLoginAttemptsException.class::isInstance).hasSize(35);
        verify(users, times(5)).findByUsername(any());
    }

    @Test
    @DisplayName("Six correct logins at once from an address without failures: five get their token, and the one that finds no place is told to retry in one second, not to wait the whole window")
    void simultaneousCorrectLoginsAreNotToldToWaitTheWholeWindow() throws Exception {
        // Five logins stay inside the comparison of the password until the test lets them go on:
        // the sixth one arrives while the five of them are in course. Nothing waits a fixed time.
        CountDownLatch fiveComparing = new CountDownLatch(5);
        CountDownLatch goOn = new CountDownLatch(1);
        doAnswer(comparison -> {
            fiveComparing.countDown();
            goOn.await();
            return comparison.callRealMethod();
        }).when(passwordEncoder).matches(anyString(), anyString());
        List<Future<LoginResponse>> inCourse = new ArrayList<>();
        Throwable sixth;

        try (ExecutorService threads = Executors.newFixedThreadPool(5)) {
            try {
                for (int login = 1; login <= 5; login++) {
                    inCourse.add(threads.submit(() -> service.login("demo", "demo1234", ADDRESS)));
                }
                assertThat(fiveComparing.await(30, TimeUnit.SECONDS)).as("five logins in course").isTrue();

                sixth = catchThrowable(() -> service.login("demo", "demo1234", ADDRESS));
            } finally {
                goOn.countDown();
            }
        }

        assertThat(sixth).isInstanceOf(TooManyLoginAttemptsException.class);
        assertThat(((TooManyLoginAttemptsException) sixth).retryAfterSeconds()).isEqualTo(1);
        for (Future<LoginResponse> login : inCourse) {
            assertThat(login.get().accessToken()).isNotBlank();
        }
        // Nothing was counted: the next login is checked, and the address still has its five places.
        assertThat(service.login("demo", "demo1234", ADDRESS).accessToken()).isNotBlank();
        failLogin(ADDRESS, 5);
    }

    @Test
    @DisplayName("A login rejected because of five failures is told to wait what is left of the window, as before")
    void loginRejectedByFailuresWaitsTheRestOfTheWindow() {
        failLogin(ADDRESS, 5);

        Throwable first = catchThrowable(() -> service.login("demo", "demo1234", ADDRESS));
        Throwable second = catchThrowable(() -> service.login("demo", "demo1234", ADDRESS));

        // The window lasts fifteen minutes and the test takes far less than one: the wait is
        // nearly all of it. The second rejected login shows that the first one freed no place.
        assertThat(first).isInstanceOf(TooManyLoginAttemptsException.class);
        assertThat(((TooManyLoginAttemptsException) first).retryAfterSeconds()).isBetween(840L, 900L);
        assertThat(second).isInstanceOf(TooManyLoginAttemptsException.class);
        assertThat(((TooManyLoginAttemptsException) second).retryAfterSeconds()).isBetween(840L, 900L);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("The fifth failed login of an address leaves the warning of the block in the log, with the address and without the user name or the password; the first four leave nothing")
    void fifthFailedLoginLeavesTheWarningOfTheBlock(CapturedOutput output) {
        failLogin(ADDRESS, 4);
        assertThat(output.getAll()).doesNotContain("blocked");

        failLogin(ADDRESS, 1);

        assertThat(output.getAll()).contains("WARN").contains("Login blocked for the address " + ADDRESS);
        assertThat(output.getAll()).doesNotContain("not-the-password").doesNotContain("demo");
    }

    /** Makes that many failed logins from an address, and checks that each one is rejected as such. */
    private void failLogin(String address, int times) {
        for (int attempt = 1; attempt <= times; attempt++) {
            assertThat(catchThrowable(() -> service.login("demo", "not-the-password", address)))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    private static Jwt read(String token) {
        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build().decode(token);
    }

}
