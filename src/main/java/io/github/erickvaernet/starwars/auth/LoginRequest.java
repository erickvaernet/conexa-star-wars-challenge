package io.github.erickvaernet.starwars.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of the login request.
 *
 * <p>Both fields are mandatory. A field that is missing, empty or made only of spaces is
 * rejected before any credential is checked.
 *
 * @param username the user name
 * @param password the password, as the user typed it
 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
