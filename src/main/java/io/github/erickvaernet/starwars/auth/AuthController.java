package io.github.erickvaernet.starwars.auth;

import io.github.erickvaernet.starwars.shared.ApiProblemDetail;
import io.github.erickvaernet.starwars.shared.ValidationProblemDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.headers.Header;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login request: {@code POST /api/v1/auth/login}.
 *
 * <p>It is one of the few public requests. An invalid body is answered with 400, wrong
 * credentials with 401 and a login from an address that failed too many times with 429, all as
 * Problem Details.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "The login, which gives the token that every other request requires")
public class AuthController {

    /**
     * The examples that the API documentation shows for the errors of the login: the answers that
     * the API gives. Each one is written without its {@code instance}, which the documentation
     * fills in with the address of the request.
     */
    static final String INVALID_BODY = """
            {"type": "about:blank", "title": "Bad Request", "status": 400,
             "detail": "Invalid request content.",
             "errors": [{"field": "username",
                         "message": "must not be blank, and must have at most 50 characters and no control characters"}]}""";

    static final String WRONG_CREDENTIALS = """
            {"type": "about:blank", "title": "Unauthorized", "status": 401,
             "detail": "Invalid username or password."}""";

    static final String TOO_MANY_ATTEMPTS = """
            {"type": "about:blank", "title": "Too Many Requests", "status": 429,
             "detail": "Too many failed login attempts. Try again later."}""";

    private final LoginService loginService;

    AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    /**
     * Exchanges a user name and a password for a token.
     *
     * <p>The failed logins are counted by the source address of the request, which is the one
     * that the request itself tells. No header is read here: where a proxy passes the requests
     * on, it is the configuration of the server that decides whether its headers are believed.
     */
    @PostMapping(path = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "login",
            summary = "Log in and get a token",
            description = "Exchanges a user name and a password for a token. To use it on this page, copy "
                    + "`accessToken` from the answer, press **Authorize** and paste it: it is then sent with "
                    + "every other request, until it expires. The example is the test user, which is loaded "
                    + "only where the seed is on, as in `docker compose`; where the seed is off, that user "
                    + "does not exist. The seed loads a test administrator too, `admin`, with the password "
                    + "`admin1234`: it is the user that can create users. The failed logins are counted by "
                    + "source address: after five of them within fifteen minutes, every login from that "
                    + "address is answered with `429`, also with the right credentials, until those "
                    + "fifteen minutes end.")
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
                    schema = @Schema(implementation = ValidationProblemDetail.class),
                    examples = @ExampleObject(AuthController.INVALID_BODY)))
    @ApiResponse(
            responseCode = "401",
            description = "The user name or the password is wrong. The answer does not say which.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ApiProblemDetail.class),
                    examples = @ExampleObject(AuthController.WRONG_CREDENTIALS)))
    @ApiResponse(
            responseCode = "429",
            description = "The source address of the request already made five failed logins within fifteen "
                    + "minutes. The login is not checked, so the answer is the same with the right "
                    + "credentials. The tokens that were already given keep working.",
            headers = @Header(
                    name = "Retry-After",
                    description = "The seconds until a login from that address is checked again: a whole "
                            + "number, of 1 or more.",
                    schema = @Schema(type = "integer", format = "int32", example = "847")),
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ApiProblemDetail.class),
                    examples = @ExampleObject(AuthController.TOO_MANY_ATTEMPTS)))
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return loginService.login(request.username(), request.password(), httpRequest.getRemoteAddr());
    }

}
