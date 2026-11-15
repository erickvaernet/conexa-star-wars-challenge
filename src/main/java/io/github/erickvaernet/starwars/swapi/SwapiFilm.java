package io.github.erickvaernet.starwars.swapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.erickvaernet.starwars.catalog.films.FilmDetail;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import java.util.List;

/**
 * The data of a film as SWAPI sends it, and its translation into the detail of the catalog.
 *
 * <p>Only the data that the API answers is read. The planets and the species of the film
 * ({@code planets}, {@code species}) and the internal data of SWAPI ({@code created},
 * {@code edited}, {@code url}) are in its answer and are ignored here.
 *
 * @param title        the title; it is the name of the film in a list
 * @param episodeId    {@code episode_id}; SWAPI sends it as a number, the only value of a film
 *                     that is not text. It is declared as any value, so that the translation
 *                     decides what is accepted
 * @param openingCrawl {@code opening_crawl}; it has line breaks inside
 * @param director     the director
 * @param producer     the producers
 * @param releaseDate  {@code release_date}
 * @param characters   the addresses in SWAPI of the characters of the film
 * @param starships    the addresses in SWAPI of its starships
 * @param vehicles     the addresses in SWAPI of its vehicles
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SwapiFilm(
        String title,
        // Not an Integer on purpose. With Integer, the library that reads the JSON decides: by
        // default it turns a text such as "4" into a number and cuts 4.5 down to 4, without notice.
        @JsonProperty("episode_id") Object episodeId,
        @JsonProperty("opening_crawl") String openingCrawl,
        String director,
        String producer,
        @JsonProperty("release_date") String releaseDate,
        List<String> characters,
        List<String> starships,
        List<String> vehicles) implements SwapiItemData {

    /**
     * Translates the film.
     *
     * <p>The title is required. Any other value that SWAPI did not send stays {@code null}, and
     * a relation that it did not send is an empty list: a secondary value that is missing does
     * not hide the rest of the film. A relation that did arrive is translated whole or not at
     * all, and so is the episode.
     */
    @Override
    public FilmDetail toItem(String id) {
        return new FilmDetail(
                id,
                SwapiText.required(title, "title"),
                wholeNumber(episodeId),
                SwapiText.optional(openingCrawl),
                SwapiText.optional(director),
                SwapiText.optional(producer),
                SwapiText.optional(releaseDate),
                SwapiAddress.identifiers(characters),
                SwapiAddress.identifiers(starships),
                SwapiAddress.identifiers(vehicles));
    }

    /**
     * Reads the episode, which the API promises as a whole number.
     *
     * <p>The values of SWAPI are not interpreted. A text, even {@code "4"}, or a number with
     * decimals is not converted: it is a value that arrived and is not what was expected, like
     * an address without its identifier.
     *
     * @return the number, or {@code null} if SWAPI did not send it
     * @throws BadGatewayException if what arrived is not a whole number
     */
    private static Integer wholeNumber(Object episode) {
        if (episode == null) {
            return null;
        }
        if (episode instanceof Integer number) {
            return number;
        }
        throw new BadGatewayException("SWAPI answered a film with an episode_id that is not a whole number", null);
    }

}
