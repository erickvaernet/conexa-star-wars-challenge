package io.github.erickvaernet.starwars.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of the login request.
 *
 * <p>Both fields are mandatory. A field that is missing, empty or made only of spaces is
 * rejected before any credential is checked. So is a value that cannot belong to any user: the
 * login is public, and what it receives goes to the database and to BCrypt.
 *
 * <p>The examples of the API documentation are the test user, so that the login can be tried
 * from Swagger UI without typing anything. Its password is public on purpose: the README
 * publishes it, and the user exists only where the seed is switched on.
 *
 * @param username the user name
 * @param password the password, as the user typed it
 */
@Schema(description = "The credentials of a user.")
public record LoginRequest(

        @Schema(
                description = "The user name. The example is the test user, which exists only where the seed "
                        + "is on, as in `docker compose`.",
                example = "demo")
        @NotBlank
        @Size(max = LoginRequest.MAX_USERNAME_LENGTH)
        @Pattern(regexp = LoginRequest.NO_CONTROL_CHARACTERS)
        String username,

        @Schema(
                description = "The password. The example is the one of the test user, which exists only where "
                        + "the seed is on, as in `docker compose`.",
                example = "demo1234")
        @NotBlank
        @Size(max = LoginRequest.MAX_PASSWORD_LENGTH)
        String password) {

    /** The length of the column that stores the user name: a longer one cannot exist. */
    static final int MAX_USERNAME_LENGTH = 50;

    /** BCrypt reads at most the first 72 bytes of a password. */
    static final int MAX_PASSWORD_LENGTH = 72;

    /**
     * A text without control characters (the Unicode category Cc), such as the null character or
     * a line break. No user name has one, and PostgreSQL refuses to look up a text that has the
     * null character.
     */
    static final String NO_CONTROL_CHARACTERS = "\\P{Cc}*";

}
