package io.github.erickvaernet.starwars.shared;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How the API documentation shows an error: the five fields that every error of the API has, in
 * the Problem Details format (RFC 9457).
 *
 * <p>It only describes. Nothing implements it: the answers are built by {@link ApiExceptionHandler}
 * with the class that comes with Spring. That class cannot be the one the documentation shows: it
 * has a field, {@code properties}, that is never written as such, so the documentation would
 * show a field that no answer has. In the documentation it keeps the name that the format has.
 */
@Schema(
        name = "ProblemDetail",
        description = "An error, as Problem Details (RFC 9457). Every error of the API has these five fields.")
// The order in which an answer has them.
@JsonPropertyOrder({"type", "title", "status", "detail", "instance"})
public interface ApiProblemDetail {

    /** Always the same: the errors of the API have no type of their own. */
    @Schema(description = "The type of the error. Always `about:blank`: the status says what happened.")
    String getType();

    /** The name of the status. */
    @Schema(description = "The name of the HTTP status.")
    String getTitle();

    /** The HTTP status, repeated in the body. */
    @Schema(description = "The HTTP status of the answer.")
    int getStatus();

    /** What happened. */
    @Schema(description = "What happened, in a fixed text that never repeats what the request sent.")
    String getDetail();

    /** Where it happened. */
    @Schema(description = "The address that was asked for.")
    String getInstance();

}
