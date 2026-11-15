package io.github.erickvaernet.starwars.catalog.vehicles;

import io.github.erickvaernet.starwars.catalog.CatalogItem;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The complete data of a vehicle. It is the answer of the detail of vehicles, written as JSON as
 * it is.
 *
 * <p>Every value is text, the way SWAPI gives it, the quantities too, because some cannot be read
 * as a number without interpreting them: the consumables can be {@code "2 months"} and a value
 * that is not known is {@code "unknown"}. The only change is that a text loses the spaces at its
 * start and at its end: SWAPI gives the length of vehicle 4 as {@code "36.8 "}. The relations are
 * lists of identifiers of other elements of this API.
 *
 * <p>The identifier and the name are always present. Any other text is {@code null} if SWAPI
 * does not send it, and a relation that SWAPI does not send is an empty list.
 */
@Schema(description = "The complete data of a vehicle.")
public record VehicleDetail(

        @Schema(description = "The identifier of the vehicle.", example = "4")
        String id,

        @Schema(description = "The name of the vehicle.", example = "Sand Crawler")
        String name,

        @Schema(description = "The model.", example = "Digger Crawler")
        String model,

        @Schema(description = "The class of vehicle.", example = "wheeled")
        String vehicleClass,

        @Schema(description = "The manufacturers, separated by commas.", example = "Corellia Mining Corporation")
        String manufacturer,

        @Schema(description = "The cost, in galactic credits.", example = "150000")
        String costInCredits,

        @Schema(
                description = "The length, in meters. Text, as SWAPI gives it, without the spaces at its start "
                        + "and at its end.",
                example = "36.8")
        String length,

        @Schema(description = "The crew that the vehicle needs.", example = "46")
        String crew,

        @Schema(description = "The passengers that the vehicle can carry.", example = "30")
        String passengers,

        @Schema(description = "The maximum speed in the atmosphere.", example = "30")
        String maxAtmospheringSpeed,

        @Schema(description = "The cargo that the vehicle can carry, in kilograms.", example = "50000")
        String cargoCapacity,

        @Schema(
                description = "For how long the vehicle can provide for its crew without resupplying.",
                example = "2 months")
        String consumables,

        @Schema(
                description = "The identifiers of the films in which the vehicle appears.",
                example = "[\"1\", \"5\"]")
        List<String> filmIds,

        @Schema(
                description = "The identifiers of the characters that have piloted the vehicle. Empty if "
                        + "none is known.",
                example = "[\"1\", \"18\"]")
        List<String> pilotIds) implements CatalogItem {

    @Override
    public ItemSummary summary() {
        return new ItemSummary(id, name);
    }

}
