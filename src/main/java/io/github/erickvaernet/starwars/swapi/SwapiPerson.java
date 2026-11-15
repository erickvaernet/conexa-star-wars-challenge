package io.github.erickvaernet.starwars.swapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.erickvaernet.starwars.catalog.people.PersonDetail;
import java.util.List;

/**
 * The data of a character as SWAPI sends it, and its translation into the detail of the catalog.
 *
 * <p>Only the data that the API answers is read. The home planet ({@code homeworld}) and the
 * internal data of SWAPI ({@code created}, {@code edited}, {@code url}) are in its answer and
 * are ignored here.
 *
 * @param name      the name
 * @param birthYear {@code birth_year}
 * @param gender    the gender
 * @param height    the height; text, because it can be {@code "unknown"}
 * @param mass      the mass; text as well
 * @param hairColor {@code hair_color}
 * @param skinColor {@code skin_color}
 * @param eyeColor  {@code eye_color}
 * @param films     the addresses in SWAPI of the films of the character
 * @param starships the addresses in SWAPI of its starships
 * @param vehicles  the addresses in SWAPI of its vehicles
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SwapiPerson(
        String name,
        @JsonProperty("birth_year") String birthYear,
        String gender,
        String height,
        String mass,
        @JsonProperty("hair_color") String hairColor,
        @JsonProperty("skin_color") String skinColor,
        @JsonProperty("eye_color") String eyeColor,
        List<String> films,
        List<String> starships,
        List<String> vehicles) implements SwapiItemData {

    /**
     * Translates the character.
     *
     * <p>The name is required. Any other text that SWAPI did not send stays {@code null}, and a
     * relation that it did not send is an empty list: a secondary value that is missing does not
     * hide the rest of the character. A relation that did arrive is translated whole or not at
     * all.
     */
    @Override
    public PersonDetail toItem(String id) {
        return new PersonDetail(
                id,
                SwapiText.required(name, "name"),
                SwapiText.optional(birthYear),
                SwapiText.optional(gender),
                SwapiText.optional(height),
                SwapiText.optional(mass),
                SwapiText.optional(hairColor),
                SwapiText.optional(skinColor),
                SwapiText.optional(eyeColor),
                SwapiAddress.identifiers(films),
                SwapiAddress.identifiers(starships),
                SwapiAddress.identifiers(vehicles));
    }

}
