package io.github.erickvaernet.starwars.catalog;

import java.util.List;

/**
 * What the data source answered when it was asked for one page of a list.
 *
 * <p>The source is always asked for a page and a size, but it does not always follow them: SWAPI
 * cuts the pages of some lists and returns others whole. There are two possible answers, and the
 * core decides what to do by which one arrived, never by which entity it is.
 */
public sealed interface Listing {

    /**
     * The source cut the page and reported how many elements there are in all.
     *
     * @param items         the elements of the page that was asked for
     * @param totalElements the total of elements of the list
     */
    record OnePage(List<ItemSummary> items, int totalElements) implements Listing {
    }

    /**
     * The source ignored the page and the size, and returned every element.
     *
     * @param items every element of the list
     */
    record Complete(List<ItemSummary> items) implements Listing {
    }

}
