package io.github.erickvaernet.starwars.auth;

import io.github.erickvaernet.starwars.shared.ValidationProblemDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creation of users: {@code POST /api/v1/users}.
 *
 * <p>It is the only request that writes something, and the only one kept for a role: the access
 * rules let in the administrators alone, before the body is even read. That is why a common user
 * gets a 403 also for a body that is not valid.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "The creation of users, which only an administrator can do")
public class UserController {

    private final UserCreationService service;

    UserController(UserCreationService service) {
        this.service = service;
    }

    /**
     * Creates a user, which can log in at once.
     *
     * <p>The answer has no {@code Location} header: there is no request to look a user up, so
     * no address is announced.
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "createUser",
            summary = "Create a user",
            description = "Creates a user, which can log in at once. **It requires the role `ADMIN`:** log in "
                    + "as the test administrator, `admin`, and press **Authorize** with its token. With the "
                    + "token of a common user, like `demo`, the answer is 403, whatever the body. If the role "
                    + "is not sent, the new user is a common one.")
    @ApiResponse(responseCode = "201", description = "The user that was created, with the role it was given.")
    @ApiResponse(
            responseCode = "400",
            description = "The body is not valid JSON, or a field does not follow its rule. When a field is "
                    + "what failed, `errors` says which, with all the invalid ones at once.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ValidationProblemDetail.class)))
    @ApiResponse(
            responseCode = "401",
            description = "The token is missing, malformed, expired or has an invalid signature.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "403",
            description = "The token is valid, but it is not the one of an administrator.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "There is already a user with that name.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(request.username(), request.password(), request.role());
    }

}
