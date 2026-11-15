package io.github.erickvaernet.starwars.catalog.films;

import io.github.erickvaernet.starwars.catalog.CatalogItem;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The complete data of a film. It is the answer of the detail of films, written as JSON as it is.
 *
 * <p>Every value is text, the way SWAPI gives it, except the episode: it is a whole number, and
 * SWAPI sends it as one. The relations are lists of identifiers of other elements of this API.
 * The planets and the species of the film are left out on purpose: this API has neither.
 *
 * <p>The identifier and the title are always present. Any other value is {@code null} if SWAPI
 * does not send it, and a relation that SWAPI does not send is an empty list.
 */
@Schema(description = "The complete data of a film.")
public record FilmDetail(

        @Schema(description = "The identifier of the film.", example = "1")
        String id,

        @Schema(description = "The title of the film. In a list, it is the name of the element.", example = "A New Hope")
        String title,

        @Schema(description = "The number of the episode. It is not the order in which the films were released.", example = "4")
        Integer episodeId,

        @Schema(
                description = "The text that opens the film, with its line breaks.",
                example = "It is a period of civil war.\r\nRebel spaceships, striking\r\nfrom a hidden base...")
        String openingCrawl,

        @Schema(description = "The director.", example = "George Lucas")
        String director,

        @Schema(description = "The producers, separated by commas.", example = "Gary Kurtz, Rick McCallum")
        String producer,

        @Schema(description = "The date of release, as year, month and day.", example = "1977-05-25")
        String releaseDate,

        @Schema(description = "The identifiers of the characters that appear in the film.", example = "[\"1\", \"2\"]")
        List<String> characterIds,

        @Schema(description = "The identifiers of the starships that appear in the film.", example = "[\"2\", \"3\"]")
        List<String> starshipIds,

        @Schema(description = "The identifiers of the vehicles that appear in the film.", example = "[\"4\", \"6\"]")
        List<String> vehicleIds) implements CatalogItem {

    /** In a list, the name of a film is its title. */
    @Override
    public ItemSummary summary() {
        return new ItemSummary(id, title);
    }

}
