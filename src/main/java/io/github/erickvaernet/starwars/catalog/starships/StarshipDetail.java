package io.github.erickvaernet.starwars.catalog.starships;

import io.github.erickvaernet.starwars.catalog.CatalogItem;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The complete data of a starship. It is the answer of the detail of starships, written as JSON
 * as it is.
 *
 * <p>Every value is text, the way SWAPI gives it, the quantities too, because many cannot be read
 * as a number without interpreting them: a crew can be {@code "342,953"}, a speed {@code "n/a"}
 * and the consumables {@code "3 years"}. The relations are lists of identifiers of other elements
 * of this API.
 *
 * <p>The identifier and the name are always present. Any other text is {@code null} if SWAPI
 * does not send it, and a relation that SWAPI does not send is an empty list.
 */
@Schema(description = "The complete data of a starship.")
public record StarshipDetail(

        @Schema(description = "The identifier of the starship.", example = "9")
        String id,

        @Schema(description = "The name of the starship.", example = "Death Star")
        String name,

        @Schema(description = "The model.", example = "DS-1 Orbital Battle Station")
        String model,

        @Schema(description = "The class of starship.", example = "Deep Space Mobile Battlestation")
        String starshipClass,

        @Schema(
                description = "The manufacturers, separated by commas.",
                example = "Imperial Department of Military Research, Sienar Fleet Systems")
        String manufacturer,

        @Schema(description = "The cost, in galactic credits.", example = "1000000000000")
        String costInCredits,

        @Schema(description = "The length, in meters.", example = "120000")
        String length,

        @Schema(
                description = "The crew that the starship needs. Text, as SWAPI gives it: it can have a comma "
                        + "for the thousands.",
                example = "342,953")
        String crew,

        @Schema(description = "The passengers that the starship can carry.", example = "843,342")
        String passengers,

        @Schema(
                description = "The maximum speed in the atmosphere. It is \"n/a\" for a starship that does not "
                        + "fly in one.",
                example = "n/a")
        String maxAtmospheringSpeed,

        @Schema(description = "The class of its hyperdrive.", example = "4.0")
        String hyperdriveRating,

        @Schema(description = "The megalights per hour that the starship can travel.", example = "10")
        String mglt,

        @Schema(description = "The cargo that the starship can carry, in kilograms.", example = "1000000000000")
        String cargoCapacity,

        @Schema(
                description = "For how long the starship can provide for its crew without resupplying.",
                example = "3 years")
        String consumables,

        @Schema(description = "The identifiers of the films in which the starship appears.", example = "[\"1\"]")
        List<String> filmIds,

        @Schema(
                description = "The identifiers of the characters that have piloted the starship. Empty if "
                        + "none is known.",
                example = "[\"13\", \"14\"]")
        List<String> pilotIds) implements CatalogItem {

    @Override
    public ItemSummary summary() {
        return new ItemSummary(id, name);
    }

}
