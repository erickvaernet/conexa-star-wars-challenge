package io.github.erickvaernet.starwars.auth;

import io.github.erickvaernet.starwars.shared.TooManyLoginAttemptsException;
import java.util.Optional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Checks a user name and a password and, if they are right, issues a token.
 *
 * <p>An unknown user and a wrong password are rejected in the same way, so that the login cannot
 * be used to find out which users exist.
 *
 * <p>The failed logins are counted by the source address of the request. An address that failed
 * too many times is rejected before anything is checked: neither the database nor BCrypt is used.
 */
@Service
class LoginService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository users;

    private final PasswordEncoder passwordEncoder;

    private final TokenIssuer tokenIssuer;

    private final LoginAttemptLimiter attempts;

    /** A hash that no password matches. It stands in for the hash of a user that does not exist. */
    private final String hashOfNoUser;

    LoginService(UserRepository users, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer,
            LoginAttemptLimiter attempts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.attempts = attempts;
        this.hashOfNoUser = passwordEncoder.encode("no user has this password");
    }

    /**
     * Logs a user in.
     *
     * <p>The login takes its place in the count of its address before it checks anything, and
     * that place becomes a failure only if the credentials turn out to be wrong. A correct login
     * gives it back, and so does one that ends in an unexpected error: neither of them tried a
     * wrong password. One of the two things always happens, so no place stays taken by a login
     * that is over.
     *
     * @param address the source address of the request, which the failed logins are counted by
     * @throws TooManyLoginAttemptsException if that address already failed too many times; the
     *                                       credentials are not checked
     * @throws BadCredentialsException       if the user does not exist or the password is wrong
     */
    LoginResponse login(String username, String password, String address) {
        attempts.acquire(address);
        boolean wrongCredentials = false;
        try {
            return checkCredentials(username, password);
        } catch (BadCredentialsException rejected) {
            wrongCredentials = true;
            throw rejected;
        } finally {
            if (wrongCredentials) {
                attempts.failed(address);
            } else {
                attempts.release(address);
            }
        }
    }

    private LoginResponse checkCredentials(String username, String password) {
        Optional<User> user = users.findByUsername(username);

        // The comparison runs also when the user does not exist. BCrypt is slow on purpose, so
        // skipping it would make that case answer faster and give away that the user is unknown.
        String hash = user.map(User::getPasswordHash).orElse(hashOfNoUser);
        boolean passwordMatches = passwordEncoder.matches(password, hash);

        if (user.isEmpty() || !passwordMatches) {
            throw new BadCredentialsException("Invalid username or password");
        }
        String token = tokenIssuer.issue(user.get().getUsername(), user.get().getRole());
        return new LoginResponse(token, TOKEN_TYPE, tokenIssuer.expiresInSeconds());
    }

}
