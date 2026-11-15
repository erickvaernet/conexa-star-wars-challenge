package io.github.erickvaernet.starwars.auth;

/**
 * Body of a successful login.
 *
 * @param accessToken the JWT to send in the {@code Authorization} header of the next requests
 * @param tokenType   always {@code Bearer}
 * @param expiresIn   seconds until the token expires
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
