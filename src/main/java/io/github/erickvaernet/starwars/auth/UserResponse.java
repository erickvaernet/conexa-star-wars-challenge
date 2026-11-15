package io.github.erickvaernet.starwars.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Answer to the creation of a user: what was created.
 *
 * <p>It never has the password nor its hash.
 *
 * @param username  the name of the user that was created
 * @param role      the role it was given: the one that was sent or, if none was, {@code USER}
 * @param createdAt when it was created
 */
@Schema(description = "The user that was created. It never has the password nor its hash.")
public record UserResponse(

        @Schema(description = "The name of the user that was created.", example = "leia")
        String username,

        @Schema(
                description = "The role it was given: the one that was sent or, if none was, `USER`.",
                example = "USER")
        String role,

        @Schema(
                description = "When it was created: date and time in UTC, in ISO 8601 format.",
                example = "2026-11-16T14:03:27.512Z")
        Instant createdAt) {
}
