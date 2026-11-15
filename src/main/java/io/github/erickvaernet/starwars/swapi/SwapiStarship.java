package io.github.erickvaernet.starwars.swapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.erickvaernet.starwars.catalog.starships.StarshipDetail;
import java.util.List;

/**
 * The data of a starship as SWAPI sends it, and its translation into the detail of the catalog.
 *
 * <p>Only the data that the API answers is read. The internal data of SWAPI ({@code created},
 * {@code edited}, {@code url}) is in its answer and is ignored here.
 *
 * <p>Every value is text, the quantities too: SWAPI sends them that way, and they are not
 * converted.
 *
 * @param name                 the name
 * @param model                the model
 * @param starshipClass        {@code starship_class}
 * @param manufacturer         the manufacturers
 * @param costInCredits        {@code cost_in_credits}
 * @param length               the length
 * @param crew                 the crew; it can have a comma for the thousands, as in {@code "342,953"}
 * @param passengers           the passengers
 * @param maxAtmospheringSpeed {@code max_atmosphering_speed}; it can be {@code "n/a"}
 * @param hyperdriveRating     {@code hyperdrive_rating}
 * @param mglt                 {@code MGLT}, which SWAPI writes in capital letters
 * @param cargoCapacity        {@code cargo_capacity}
 * @param consumables          the consumables, a quantity with its unit, as in {@code "3 years"}
 * @param films                the addresses in SWAPI of the films of the starship
 * @param pilots               the addresses in SWAPI of its pilots
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SwapiStarship(
        String name,
        String model,
        @JsonProperty("starship_class") String starshipClass,
        String manufacturer,
        @JsonProperty("cost_in_credits") String costInCredits,
        String length,
        String crew,
        String passengers,
        @JsonProperty("max_atmosphering_speed") String maxAtmospheringSpeed,
        @JsonProperty("hyperdrive_rating") String hyperdriveRating,
        @JsonProperty("MGLT") String mglt,
        @JsonProperty("cargo_capacity") String cargoCapacity,
        String consumables,
        List<String> films,
        List<String> pilots) implements SwapiItemData {

    /**
     * Translates the starship.
     *
     * <p>The name is required. Any other text that SWAPI did not send stays {@code null}, and a
     * relation that it did not send is an empty list: a secondary value that is missing does not
     * hide the rest of the starship. A relation that did arrive is translated whole or not at
     * all.
     */
    @Override
    public StarshipDetail toItem(String id) {
        return new StarshipDetail(
                id,
                SwapiText.required(name, "name"),
                SwapiText.optional(model),
                SwapiText.optional(starshipClass),
                SwapiText.optional(manufacturer),
                SwapiText.optional(costInCredits),
                SwapiText.optional(length),
                SwapiText.optional(crew),
                SwapiText.optional(passengers),
                SwapiText.optional(maxAtmospheringSpeed),
                SwapiText.optional(hyperdriveRating),
                SwapiText.optional(mglt),
                SwapiText.optional(cargoCapacity),
                SwapiText.optional(consumables),
                SwapiAddress.identifiers(films),
                SwapiAddress.identifiers(pilots));
    }

}
