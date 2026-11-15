package io.github.erickvaernet.starwars.catalog;

import io.github.erickvaernet.starwars.shared.ValidationProblemDetail;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Documents the errors that any list can answer, so that each controller does not repeat them.
 *
 * <p>It only adds to the API documentation; it changes nothing of how the request behaves.
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
                schema = @Schema(implementation = ValidationProblemDetail.class)))
@ApiResponse(
        responseCode = "401",
        description = "The token is missing, malformed, expired or has an invalid signature.",
        content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
        responseCode = "502",
        description = "SWAPI answered with an error or with something that cannot be read.",
        content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
        responseCode = "504",
        description = "SWAPI did not answer in time.",
        content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
public @interface ListErrorResponses {
}
