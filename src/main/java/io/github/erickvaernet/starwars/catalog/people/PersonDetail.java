package io.github.erickvaernet.starwars.catalog.people;

import io.github.erickvaernet.starwars.catalog.CatalogItem;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The complete data of a character. It is the answer of the detail of people, written as JSON
 * as it is.
 *
 * <p>Every value is text, the way SWAPI gives it, because some are not numbers: a height can be
 * {@code "unknown"} and a hair colour {@code "n/a"}. The relations are lists of identifiers of
 * other elements of this API. The home planet is left out on purpose: this API has no planets.
 *
 * <p>The identifier and the name are always present. Any other text is {@code null} if SWAPI
 * does not send it, and a relation that SWAPI does not send is an empty list.
 */
@Schema(description = "The complete data of a character.")
public record PersonDetail(

        @Schema(description = "The identifier of the character.", example = "1")
        String id,

        @Schema(description = "The name of the character.", example = "Luke Skywalker")
        String name,

        @Schema(description = "The year of birth, counted before or after the Battle of Yavin.", example = "19BBY")
        String birthYear,

        @Schema(description = "The gender, or n/a if the character has none.", example = "male")
        String gender,

        @Schema(description = "The height, in centimetres.", example = "172")
        String height,

        @Schema(description = "The mass, in kilograms.", example = "77")
        String mass,

        @Schema(description = "The colour of the hair, or n/a if the character has none.", example = "blond")
        String hairColor,

        @Schema(description = "The colour of the skin.", example = "fair")
        String skinColor,

        @Schema(description = "The colour of the eyes.", example = "blue")
        String eyeColor,

        @Schema(description = "The identifiers of the films the character appears in.", example = "[\"1\", \"2\"]")
        List<String> filmIds,

        @Schema(description = "The identifiers of the starships the character has piloted.", example = "[\"12\"]")
        List<String> starshipIds,

        @Schema(description = "The identifiers of the vehicles the character has piloted.", example = "[\"14\"]")
        List<String> vehicleIds) implements CatalogItem {

    @Override
    public ItemSummary summary() {
        return new ItemSummary(id, name);
    }

}
