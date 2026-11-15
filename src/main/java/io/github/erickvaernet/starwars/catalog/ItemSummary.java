package io.github.erickvaernet.starwars.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One element of a list: just enough to recognise it and to ask for its detail.
 *
 * @param id   the identifier, as text, the way the source gives it
 * @param name the name of the element; for a film, its title
 */
@Schema(description = "One element of a list: its identifier and its name.")
public record ItemSummary(

        @Schema(description = "The identifier of the element, with which its detail is asked for.", example = "1")
        String id,

        @Schema(description = "The name of the element. For a film, its title.", example = "Luke Skywalker")
        String name) {
}
