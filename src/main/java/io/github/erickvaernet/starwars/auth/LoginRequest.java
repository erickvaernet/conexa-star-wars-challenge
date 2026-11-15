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
 * <p>Every rule of a field carries the same text, the one of that field: it is what the answer
 * to an invalid body says about the field, whichever of its rules failed. A text is written as
 * it is sent, so it cannot have braces nor a dollar sign, which the validation would read as
 * placeholders.
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
        @NotBlank(message = LoginRequest.USERNAME_RULE)
        @Size(max = LoginRequest.MAX_USERNAME_LENGTH, message = LoginRequest.USERNAME_RULE)
        @Pattern(regexp = LoginRequest.NO_CONTROL_CHARACTERS, message = LoginRequest.USERNAME_RULE)
        String username,

        @Schema(
                description = "The password. The example is the one of the test user, which exists only where "
                        + "the seed is on, as in `docker compose`.",
                example = "demo1234")
        @NotBlank(message = LoginRequest.PASSWORD_RULE)
        @Size(max = LoginRequest.MAX_PASSWORD_LENGTH, message = LoginRequest.PASSWORD_RULE)
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

    /** What an invalid user name is told, whichever of its three rules it breaks. */
    static final String USERNAME_RULE = "must not be blank, and must have at most " + MAX_USERNAME_LENGTH
            + " characters and no control characters";

    /** What an invalid password is told, whichever of its two rules it breaks. */
    static final String PASSWORD_RULE = "must not be blank, and must have at most " + MAX_PASSWORD_LENGTH
            + " characters";

}
