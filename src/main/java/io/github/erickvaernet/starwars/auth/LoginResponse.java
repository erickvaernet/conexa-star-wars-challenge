package io.github.erickvaernet.starwars.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body of a successful login.
 *
 * @param accessToken the JWT to send in the {@code Authorization} header of the next requests
 * @param tokenType   always {@code Bearer}
 * @param expiresIn   seconds until the token expires
 */
@Schema(description = "The token of a session.")
public record LoginResponse(

        @Schema(
                description = "The token, a JWT. It goes in the `Authorization` header of every other request, "
                        + "after the word `Bearer`.",
                example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "The type of the token. Always `Bearer`.", example = "Bearer")
        String tokenType,

        @Schema(description = "Seconds until the token expires.", example = "3600")
        long expiresIn) {
}
