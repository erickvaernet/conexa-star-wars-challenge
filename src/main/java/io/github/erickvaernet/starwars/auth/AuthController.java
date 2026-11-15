package io.github.erickvaernet.starwars.auth;

import io.github.erickvaernet.starwars.shared.ValidationProblemDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login request: {@code POST /api/v1/auth/login}.
 *
 * <p>It is one of the few public requests. An invalid body is answered with 400 and wrong
 * credentials with 401, both as Problem Details.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "The login, which gives the token that every other request requires")
public class AuthController {

    private final LoginService loginService;

    AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    /** Exchanges a user name and a password for a token. */
    @PostMapping(path = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "login",
            summary = "Log in and get a token",
            description = "Exchanges a user name and a password for a token. To use it on this page, copy "
                    + "`accessToken` from the answer, press **Authorize** and paste it: it is then sent with "
                    + "every other request, until it expires. The example is the test user, which is loaded "
                    + "only where the seed is on, as in `docker compose`; where the seed is off, that user "
                    + "does not exist.")
    // The documentation requires the token in every request unless the request says otherwise.
    // This empty list is how the login says so: it gets no padlock, and Swagger UI sends it no
    // token. That matters, because a public request that brings a rejected token is answered
    // with 401: with an expired token loaded, nobody could log in again from the page.
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "The token, its type and the seconds until it expires.")
    @ApiResponse(
            responseCode = "400",
            description = "The body is not valid JSON, or the user name or the password is missing, blank or "
                    + "too long. When a field is what failed, `errors` says which.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ValidationProblemDetail.class)))
    @ApiResponse(
            responseCode = "401",
            description = "The user name or the password is wrong. The answer does not say which.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request.username(), request.password());
    }

}
