package io.github.erickvaernet.starwars.swapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.Listing;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The answer of SWAPI to a list of summaries, and its translation into the model of the catalog.
 *
 * <p>SWAPI answers a list in one of two forms. When it cuts the list in pages, it sends the page
 * in {@code results} and the total in {@code total_records}. When it ignores the page and the
 * size, as it does in a search, it sends every element in {@code result}, each one with all its
 * data. Which form arrives is not known beforehand and can change without notice, so this record
 * reads both and tells them apart by what came.
 *
 * <p>Only the data that the application uses is read; the rest of the answer is ignored: of the
 * data of an element, only its name. {@link SwapiExpandedListResponse} reads the same two forms
 * when every element is wanted with its complete data. This record and its names never leave
 * this package.
 *
 * @param totalRecords how many elements the list has in all, when it comes in pages:
 *                     {@code total_records}
 * @param results      the elements of the page, when the list comes in pages
 * @param result       every element of the list, when it comes whole
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SwapiListResponse(
        @JsonProperty("total_records") Integer totalRecords, List<Item> results, List<FullItem> result) {

    /**
     * One element of a page of SWAPI.
     *
     * @param uid  the identifier; SWAPI sends it as text
     * @param name the name of the element
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String uid, String name) {
    }

    /**
     * One element of a list that SWAPI answers whole. Its name is not next to the identifier,
     * as in a page, but inside its data, in a field that depends on the entity.
     *
     * @param uid        the identifier; SWAPI sends it as text
     * @param properties the data of the element, by the names of SWAPI
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FullItem(String uid, Map<String, Object> properties) {
    }

    /**
     * Translates the answer into a page of the catalog or into the complete list, by the form
     * in which it came.
     *
     * <p>An answer with missing data is not completed nor trimmed down: a list with an element
     * left out in silence is harder to notice than an error.
     *
     * @param nameField the field that holds the name of an element in a list that comes whole:
     *                  the one its entity declares
     * @throws BadGatewayException if the answer has neither of the two forms, if the total is
     *     negative, or if the identifier or the name of an element are missing
     */
    Listing<ItemSummary> toListing(String nameField) {
        return listing(totalRecords, results, SwapiListResponse::toSummary, result, item -> toSummary(item, nameField));
    }

    /**
     * Tells the two forms of a list of SWAPI apart by what came, and translates its elements. It
     * is the only place that decides it, for a list of summaries and for one that brings the
     * complete data.
     *
     * @param totalRecords   the total, when the list comes in pages
     * @param results        the elements of the page, when the list comes in pages
     * @param ofAPage        how one element of a page is translated
     * @param result         every element, when the list comes whole
     * @param ofTheWholeList how one element of a whole list is translated
     * @throws BadGatewayException if the answer has neither of the two forms, if the total is
     *     negative, or if an element cannot be translated
     */
    static <P, W, T> Listing<T> listing(
            Integer totalRecords, List<P> results, Function<P, T> ofAPage,
            List<W> result, Function<W, T> ofTheWholeList) {
        if (totalRecords != null && results != null) {
            if (totalRecords < 0) {
                throw new BadGatewayException("SWAPI answered a list with a negative total of records", null);
            }
            return new Listing.OnePage<>(results.stream().map(ofAPage).toList(), totalRecords);
        }
        if (result != null) {
            return new Listing.Complete<>(result.stream().map(ofTheWholeList).toList());
        }
        throw new BadGatewayException(
                "SWAPI answered a list without a page and its total of records, and without the whole list", null);
    }

    private static ItemSummary toSummary(Item item) {
        if (item == null) {
            throw elementThatCannotBeRead();
        }
        return summary(item.uid(), item.name());
    }

    private static ItemSummary toSummary(FullItem item, String nameField) {
        // The name has to be there and be a text: SWAPI also sends numbers and lists in the data.
        if (item == null || item.properties() == null
                || !(item.properties().get(nameField) instanceof String name)) {
            throw elementThatCannotBeRead();
        }
        return summary(item.uid(), name);
    }

    private static ItemSummary summary(String uid, String name) {
        if (isMissing(uid) || isMissing(name)) {
            throw elementThatCannotBeRead();
        }
        // The only clean-up: spaces at the start and at the end. The identifier stays as text.
        return new ItemSummary(uid.strip(), name.strip());
    }

    private static BadGatewayException elementThatCannotBeRead() {
        return new BadGatewayException("SWAPI answered a list with an element without uid or name", null);
    }

    private static boolean isMissing(String text) {
        return text == null || text.isBlank();
    }

}
