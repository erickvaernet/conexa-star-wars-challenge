package io.github.erickvaernet.starwars.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of the request that creates a user.
 *
 * <p>Nothing is corrected in silence: a value that does not follow its rule is rejected, with
 * neither its spaces trimmed nor its letters changed to lowercase or to capitals.
 *
 * <p>Every rule of a field carries the same text, the one of that field: it is what the answer
 * to an invalid body says about the field, whichever of its rules failed. A text is written as
 * it is sent, so it cannot have braces nor a dollar sign, which the validation would read as
 * placeholders.
 *
 * <p>The role is received as a text, and not as one of two fixed values, so that an invalid one
 * is answered like any other invalid field, by its name, and not as a body that cannot be read.
 *
 * @param username the name of the new user
 * @param password its password, as it was typed: the spaces count and are not trimmed
 * @param role     its role, {@code USER} or {@code ADMIN}; a common user if it is missing
 */
@Schema(description = "The user to create.")
public record CreateUserRequest(

        @Schema(
                description = "The name of the new user: 3 to 50 characters, only lowercase letters from `a` "
                        + "to `z`, digits, `.`, `_` and `-`.",
                example = "leia")
        @NotNull(message = CreateUserRequest.USERNAME_RULE)
        @Pattern(regexp = CreateUserRequest.USERNAME_FORMAT, message = CreateUserRequest.USERNAME_RULE)
        String username,

        @Schema(
                description = "Its password: 8 to 72 characters, not only spaces, and at most 72 bytes in "
                        + "UTF-8. The spaces count and are not trimmed. It is stored as a BCrypt hash.",
                example = "alderaan-1977")
        // The same rule that the login applies to a password: without it, a user could be created
        // with a password that the login refuses to read.
        @NotBlank(message = CreateUserRequest.PASSWORD_RULE)
        @Size(
                min = CreateUserRequest.MIN_PASSWORD_LENGTH,
                max = CreateUserRequest.MAX_PASSWORD_LENGTH,
                message = CreateUserRequest.PASSWORD_RULE)
        @MaxUtf8Bytes(value = CreateUserRequest.MAX_PASSWORD_LENGTH, message = CreateUserRequest.PASSWORD_RULE)
        String password,

        @Schema(
                description = "Its role. If it is missing, the user is a common one, `USER`.",
                allowableValues = {"USER", "ADMIN"},
                defaultValue = "USER",
                example = "USER")
        // A missing role is valid: this rule only judges one that was sent.
        @Pattern(regexp = CreateUserRequest.ROLES, message = CreateUserRequest.ROLE_RULE)
        String role) {

    /**
     * A user name: 3 to 50 characters, the length of the column that stores it, and only
     * lowercase letters without accents, digits, the dot, the underscore and the hyphen. Two
     * users cannot differ only in their capitals, and no name has spaces or invisible characters.
     */
    static final String USERNAME_FORMAT = "[a-z0-9._-]{3,50}";

    static final int MIN_PASSWORD_LENGTH = 8;

    /** BCrypt reads at most 72 bytes of a password, and fails when it is asked to hash more. */
    static final int MAX_PASSWORD_LENGTH = 72;

    /** The two roles, written exactly like this. */
    static final String ROLES = "USER|ADMIN";

    /** What an invalid user name is told, whichever of its rules it breaks. */
    static final String USERNAME_RULE = "must have 3 to 50 characters: lowercase letters, digits, '.', '_' or '-'";

    /** What an invalid password is told, whichever of its rules it breaks. */
    static final String PASSWORD_RULE = "must have " + MIN_PASSWORD_LENGTH + " to " + MAX_PASSWORD_LENGTH
            + " characters, not only spaces, and at most " + MAX_PASSWORD_LENGTH + " bytes in UTF-8";

    /** What an invalid role is told. */
    static final String ROLE_RULE = "must be USER or ADMIN";

}
