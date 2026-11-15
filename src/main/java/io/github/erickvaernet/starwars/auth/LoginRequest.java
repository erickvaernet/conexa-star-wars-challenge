package io.github.erickvaernet.starwars.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of the login request.
 *
 * <p>Both fields are mandatory. A field that is missing, empty or made only of spaces is
 * rejected before any credential is checked.
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
        @NotBlank String username,

        @Schema(
                description = "The password. The example is the one of the test user, which exists only where "
                        + "the seed is on, as in `docker compose`.",
                example = "demo1234")
        @NotBlank String password) {
}
