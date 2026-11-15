package io.github.erickvaernet.starwars.auth;

import io.github.erickvaernet.starwars.shared.UsernameAlreadyExistsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Creates users. The values it receives already follow the rules of {@link CreateUserRequest}.
 *
 * <p>The password is stored as a BCrypt hash of what arrived, and the user can log in at once.
 */
@Service
class UserCreationService {

    /** The role of a user that is created without saying which one. */
    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository users;

    private final PasswordEncoder passwordEncoder;

    UserCreationService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a user.
     *
     * @param role the role of the user, or {@code null} for a common one
     * @throws UsernameAlreadyExistsException if another user has that name
     */
    UserResponse create(String username, String password, String role) {
        // Asked first because it is the usual way to find out: it answers without the cost of
        // BCrypt, which is slow on purpose, and without an error of the database in the log.
        if (users.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException();
        }
        User user = new User(username, passwordEncoder.encode(password), role == null ? DEFAULT_ROLE : role);
        try {
            users.save(user);
        } catch (DataIntegrityViolationException refusedByTheDatabase) {
            // Two requests for the same name at once: both found it free, and the unique column
            // of the database refused the row of the second one. It is the only rule of the
            // table that a value accepted by the request can break.
            throw new UsernameAlreadyExistsException();
        }
        return new UserResponse(user.getUsername(), user.getRole(), user.getCreatedAt());
    }

}
