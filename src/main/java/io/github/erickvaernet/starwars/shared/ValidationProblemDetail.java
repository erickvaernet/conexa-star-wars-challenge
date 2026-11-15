package io.github.erickvaernet.starwars.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.http.ProblemDetail;

/**
 * How the API documentation shows a 400: the fields of every error, plus the list that says
 * which fields of the body, or which parameter, are invalid.
 *
 * <p>It only describes. Nothing implements it: the answer is built by {@link ApiExceptionHandler},
 * which adds the list to the error. It is here, and not next to a controller, because the login
 * and the lists answer the same way and neither can depend on the other.
 */
@Schema(
        description = "An error of a request that is not accepted, as Problem Details. When what failed is a "
                + "field of the body or a parameter, `errors` says which.",
        allOf = ProblemDetail.class)
public interface ValidationProblemDetail {

    /** The invalid fields, each once and sorted by name. A parameter is the only element. */
    @Schema(description = "The fields of the body that are invalid, each one once and sorted by name; or the "
            + "parameter that is invalid, alone. It is missing when the error does not come from a field: "
            + "a body that is not JSON, a parameter that is not correctly encoded or an address that is rejected.")
    List<InvalidField> getErrors();

}
