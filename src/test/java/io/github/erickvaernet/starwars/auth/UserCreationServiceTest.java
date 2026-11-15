package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.erickvaernet.starwars.shared.UsernameAlreadyExistsException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Checks the rules of the creation of a user with a real BCrypt encoder; only the database is
 * replaced.
 */
class UserCreationServiceTest {

    /** A BCrypt hash: version, cost, and 53 characters of salt and hash. */
    private static final String BCRYPT_HASH = "\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}";

    /** The lowest BCrypt cost, to keep the tests fast. */
    private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));

    private final UserRepository users = mock(UserRepository.class);

    private UserCreationService service;

    @BeforeEach
    void setUp() {
        when(users.existsByUsername("demo")).thenReturn(true);
        when(users.save(any(User.class))).thenAnswer(saving -> saving.getArgument(0));
        service = new UserCreationService(users, passwordEncoder);
    }

    @Test
    @DisplayName("A user created without a role is a common user")
    void userCreatedWithoutARoleIsACommonUser() {
        UserResponse created = service.create("leia", "alderaan-1977", null);

        assertThat(created.username()).isEqualTo("leia");
        assertThat(created.role()).isEqualTo("USER");
        assertThat(saved().getUsername()).isEqualTo("leia");
        assertThat(saved().getRole()).isEqualTo("USER");
    }

    @ParameterizedTest(name = "role {0}")
    @ValueSource(strings = {"USER", "ADMIN"})
    @DisplayName("A user created with a role has that role")
    void userCreatedWithARoleHasThatRole(String role) {
        UserResponse created = service.create("mon.mothma", "alderaan-1977", role);

        assertThat(created.role()).isEqualTo(role);
        assertThat(saved().getRole()).isEqualTo(role);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"alderaan-1977", "  two words  ", "12345678"})
    @DisplayName("The password is stored as a BCrypt hash of what arrived, without trimming it")
    void passwordIsStoredAsAHashOfWhatArrived(String password) {
        service.create("leia", password, null);

        String hash = saved().getPasswordHash();
        assertThat(hash).matches(BCRYPT_HASH).doesNotContain(password);
        assertThat(passwordEncoder.matches(password, hash)).isTrue();
    }

    @Test
    @DisplayName("The answer says when the user was created: the moment that is stored, to the millisecond")
    void answerSaysWhenTheUserWasCreated() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        UserResponse created = service.create("leia", "alderaan-1977", null);

        assertThat(created.createdAt()).isEqualTo(saved().getCreatedAt());
        assertThat(created.createdAt()).isBetween(before, Instant.now());
        // The database keeps less digits than the clock gives. Without cutting them here, the
        // answer would name a moment that is not exactly the stored one.
        assertThat(created.createdAt()).isEqualTo(created.createdAt().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    @DisplayName("A user name that is already taken is rejected before the password is hashed, and nothing is stored")
    void userNameThatIsTakenIsRejected() {
        Throwable error = catchThrowable(() -> service.create("demo", "alderaan-1977", "ADMIN"));

        assertThat(error).isInstanceOf(UsernameAlreadyExistsException.class);
        verify(passwordEncoder, never()).encode(anyString());
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("A user name that the database refuses because another request took it first is rejected in the same way")
    void userNameTakenWhileCreatingIsRejectedInTheSameWay() {
        // Two requests for the same name at once: both find it free, and the unique column of the
        // database refuses the row of the second one.
        when(users.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        Throwable error = catchThrowable(() -> service.create("leia", "alderaan-1977", null));

        assertThat(error).isInstanceOf(UsernameAlreadyExistsException.class);
    }

    private User saved() {
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).save(user.capture());
        return user.getValue();
    }

}
