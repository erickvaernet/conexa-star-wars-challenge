package io.github.erickvaernet.starwars.shared;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One element of the {@code errors} list of a validation error: a field of the body, or a
 * parameter, that does not follow its rule.
 *
 * <p>Both texts are written in the code. The value that was received is never part of them, so
 * nothing sent by the client is repeated back.
 *
 * @param field   the name of the field or of the parameter, as the client sends it
 * @param message the rule it must follow, the same text whichever part of the rule failed
 */
@Schema(description = "A field of the body, or a parameter, that is invalid.")
public record InvalidField(

        @Schema(description = "The name of the field or of the parameter, as it is sent.", example = "size")
        String field,

        @Schema(
                description = "The rule that it must follow. It is a fixed text for each field, and it never "
                        + "repeats the value that was received.",
                example = "must be an integer between 1 and 100")
        String message) {
}
