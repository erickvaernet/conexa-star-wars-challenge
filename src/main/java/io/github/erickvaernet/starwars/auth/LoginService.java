package io.github.erickvaernet.starwars.auth;

import java.util.Optional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Checks a user name and a password and, if they are right, issues a token.
 *
 * <p>An unknown user and a wrong password are rejected in the same way, so that the login cannot
 * be used to find out which users exist.
 */
@Service
class LoginService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository users;

    private final PasswordEncoder passwordEncoder;

    private final TokenIssuer tokenIssuer;

    /** A hash that no password matches. It stands in for the hash of a user that does not exist. */
    private final String hashOfNoUser;

    LoginService(UserRepository users, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.hashOfNoUser = passwordEncoder.encode("no user has this password");
    }

    /**
     * Logs a user in.
     *
     * @throws BadCredentialsException if the user does not exist or the password is wrong
     */
    LoginResponse login(String username, String password) {
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
