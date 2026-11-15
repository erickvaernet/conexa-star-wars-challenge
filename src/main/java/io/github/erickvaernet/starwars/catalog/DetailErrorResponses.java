package io.github.erickvaernet.starwars.catalog;

import io.github.erickvaernet.starwars.shared.ApiProblemDetail;
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
 * Documents the errors that any detail can answer, so that each controller does not repeat them:
 * the ones of a list, and the element that does not exist.
 *
 * <p>It only adds to the API documentation; it changes nothing of how the request behaves.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ListErrorResponses
@ApiResponse(
        responseCode = "404",
        description = "There is no element with that identifier.",
        content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ApiProblemDetail.class),
                examples = @ExampleObject(DetailErrorResponses.NOT_FOUND)))
public @interface DetailErrorResponses {

    String NOT_FOUND = """
            {"type": "about:blank", "title": "Not Found", "status": 404,
             "detail": "The requested resource was not found."}""";

}
