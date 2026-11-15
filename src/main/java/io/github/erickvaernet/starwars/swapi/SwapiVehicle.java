package io.github.erickvaernet.starwars.swapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.erickvaernet.starwars.catalog.vehicles.VehicleDetail;
import java.util.List;

/**
 * The data of a vehicle as SWAPI sends it, and its translation into the detail of the catalog.
 *
 * <p>Only the data that the API answers is read. The internal data of SWAPI ({@code created},
 * {@code edited}, {@code url}) is in its answer and is ignored here.
 *
 * <p>Every value is text, the quantities too: SWAPI sends them that way, and they are not
 * converted. They only lose the spaces at their start and at their end.
 *
 * @param name                 the name
 * @param model                the model
 * @param vehicleClass         {@code vehicle_class}
 * @param manufacturer         the manufacturers
 * @param costInCredits        {@code cost_in_credits}
 * @param length               the length; SWAPI sends the one of vehicle 4 as {@code "36.8 "},
 *                             with a space at the end
 * @param crew                 the crew
 * @param passengers           the passengers
 * @param maxAtmospheringSpeed {@code max_atmosphering_speed}
 * @param cargoCapacity        {@code cargo_capacity}
 * @param consumables          the consumables, a quantity with its unit, as in {@code "2 months"}
 * @param films                the addresses in SWAPI of the films of the vehicle
 * @param pilots               the addresses in SWAPI of its pilots
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SwapiVehicle(
        String name,
        String model,
        @JsonProperty("vehicle_class") String vehicleClass,
        String manufacturer,
        @JsonProperty("cost_in_credits") String costInCredits,
        String length,
        String crew,
        String passengers,
        @JsonProperty("max_atmosphering_speed") String maxAtmospheringSpeed,
        @JsonProperty("cargo_capacity") String cargoCapacity,
        String consumables,
        List<String> films,
        List<String> pilots) implements SwapiItemData {

    /**
     * Translates the vehicle.
     *
     * <p>The name is required. Any other text that SWAPI did not send stays {@code null}, and a
     * relation that it did not send is an empty list: a secondary value that is missing does not
     * hide the rest of the vehicle. A relation that did arrive is translated whole or not at
     * all.
     */
    @Override
    public VehicleDetail toItem(String id) {
        return new VehicleDetail(
                id,
                SwapiText.required(name, "name"),
                SwapiText.optional(model),
                SwapiText.optional(vehicleClass),
                SwapiText.optional(manufacturer),
                SwapiText.optional(costInCredits),
                SwapiText.optional(length),
                SwapiText.optional(crew),
                SwapiText.optional(passengers),
                SwapiText.optional(maxAtmospheringSpeed),
                SwapiText.optional(cargoCapacity),
                SwapiText.optional(consumables),
                SwapiAddress.identifiers(films),
                SwapiAddress.identifiers(pilots));
    }

}
