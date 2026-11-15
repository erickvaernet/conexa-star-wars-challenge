package io.github.erickvaernet.starwars.swapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.erickvaernet.starwars.catalog.CatalogItem;
import io.github.erickvaernet.starwars.catalog.Listing;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import io.github.erickvaernet.starwars.swapi.SwapiItemResponse.Result;
import java.util.List;

/**
 * The answer of SWAPI to a list that was asked for with {@code expanded=true}, and its
 * translation into the model of the catalog with the complete data of each element.
 *
 * <p>SWAPI answers it in the same two forms as any list: a page in {@code results} with the
 * total in {@code total_records}, or every element in {@code result}. What changes is that, in
 * both, each element has the form of the answer to one element: its identifier and, next to it,
 * all its data. So each one is read with the translation of the detail of its entity.
 *
 * <p>Some lists, and every search, come whole and with all the data whether {@code expanded} is
 * sent or not. Those are read here too when the complete data was asked for.
 *
 * <p>This record and its names never leave this package.
 *
 * @param totalRecords how many elements the list has in all, when it comes in pages:
 *                     {@code total_records}
 * @param results      the elements of the page, when the list comes in pages
 * @param result       every element of the list, when it comes whole
 * @param <D>          the data of an element, in the format that SWAPI uses for its entity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SwapiExpandedListResponse<D extends SwapiItemData>(
        @JsonProperty("total_records") Integer totalRecords, List<Result<D>> results, List<Result<D>> result) {

    /**
     * Translates the answer into a page of the catalog or into the complete list, by the form
     * in which it came, with each element translated like the one that is asked for alone.
     *
     * <p>One element that cannot be translated makes the whole answer fail: a list with an
     * element left out in silence is harder to notice than an error. That includes an element
     * without its data, which is what SWAPI would send if it stopped reading {@code expanded}.
     *
     * @throws BadGatewayException if the answer has neither of the two forms, if the total is
     *     negative, or if an element lacks its identifier or its data, or cannot be translated
     */
    Listing<CatalogItem> toListing() {
        return SwapiListResponse.listing(
                totalRecords, results, SwapiItemResponse::toItem, result, SwapiItemResponse::toItem);
    }

}
