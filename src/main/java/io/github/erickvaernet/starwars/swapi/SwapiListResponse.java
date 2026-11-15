package io.github.erickvaernet.starwars.swapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.Listing;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import java.util.List;

/**
 * The answer of SWAPI to a list that it cuts in pages, and its translation into the model of
 * the catalog.
 *
 * <p>Only the data that the application uses is read; the rest of the answer is ignored. This
 * record and its names never leave this package.
 *
 * @param totalRecords how many elements the list has in all: {@code total_records}
 * @param results      the elements of the page
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SwapiListResponse(@JsonProperty("total_records") Integer totalRecords, List<Item> results) {

    /**
     * One element of a list of SWAPI.
     *
     * @param uid  the identifier; SWAPI sends it as text
     * @param name the name of the element
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String uid, String name) {
    }

    /**
     * Translates the answer into a page of the catalog.
     *
     * <p>An answer with missing data is not completed nor trimmed down: a list with an element
     * left out in silence is harder to notice than an error.
     *
     * @throws BadGatewayException if the total, the results, or the identifier or the name of an
     *     element are missing
     */
    Listing toListing() {
        if (totalRecords == null || totalRecords < 0 || results == null) {
            throw new BadGatewayException("SWAPI answered a list without its total of records or its results", null);
        }
        return new Listing.OnePage(results.stream().map(SwapiListResponse::toSummary).toList(), totalRecords);
    }

    private static ItemSummary toSummary(Item item) {
        if (item == null || isMissing(item.uid()) || isMissing(item.name())) {
            throw new BadGatewayException("SWAPI answered a list with an element without uid or name", null);
        }
        // The only clean-up: spaces at the start and at the end. The identifier stays as text.
        return new ItemSummary(item.uid().strip(), item.name().strip());
    }

    private static boolean isMissing(String text) {
        return text == null || text.isBlank();
    }

}
