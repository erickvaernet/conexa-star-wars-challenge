package io.github.erickvaernet.starwars.catalog;

import io.github.erickvaernet.starwars.shared.ApiProblemDetail;
import io.github.erickvaernet.starwars.shared.ValidationProblemDetail;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.MediaType;

/**
 * Documents the errors that any list can answer, so that each controller does not repeat them.
 *
 * <p>It only adds to the API documentation; it changes nothing of how the request behaves.
 *
 * <p>Each error has its example, which is the answer that the API gives. An example is written
 * without its {@code instance}: the documentation fills it in with the address of each request.
 */
// Also on another annotation: the errors of a detail are these and one more.
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "400",
        description = "A parameter is invalid. The detail names it, and `errors` has it with the rule that it "
                + "must follow. If several are invalid, it is the first one.",
        content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ValidationProblemDetail.class),
                examples = @ExampleObject(ListErrorResponses.INVALID_PARAMETER)))
@ApiResponse(
        responseCode = "401",
        description = "The token is missing, malformed, expired or has an invalid signature.",
        content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ApiProblemDetail.class),
                examples = @ExampleObject(ListErrorResponses.NOT_AUTHENTICATED)))
@ApiResponse(
        responseCode = "502",
        description = "SWAPI answered with an error or with something that cannot be read.",
        content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ApiProblemDetail.class),
                examples = @ExampleObject(ListErrorResponses.SOURCE_FAILED)))
@ApiResponse(
        responseCode = "504",
        description = "SWAPI did not answer in time.",
        content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ApiProblemDetail.class),
                examples = @ExampleObject(ListErrorResponses.SOURCE_TOO_SLOW)))
public @interface ListErrorResponses {

    /** An identifier that is not one: the parameter that a list and a detail both have. */
    String INVALID_PARAMETER = """
            {"type": "about:blank", "title": "Bad Request", "status": 400,
             "detail": "Parameter 'id' must be a number of 1 to 10 digits, without leading zeros.",
             "errors": [{"field": "id",
                         "message": "must be a number of 1 to 10 digits, without leading zeros"}]}""";

    String NOT_AUTHENTICATED = """
            {"type": "about:blank", "title": "Unauthorized", "status": 401,
             "detail": "Authentication is required to access this resource."}""";

    String SOURCE_FAILED = """
            {"type": "about:blank", "title": "Bad Gateway", "status": 502,
             "detail": "The data source failed or returned an unexpected response."}""";

    String SOURCE_TOO_SLOW = """
            {"type": "about:blank", "title": "Gateway Timeout", "status": 504,
             "detail": "The data source did not respond in time."}""";

}
