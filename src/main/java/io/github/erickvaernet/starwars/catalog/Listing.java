package io.github.erickvaernet.starwars.catalog;

import java.util.List;

/**
 * What the data source answered when it was asked for one page of a list or of a search.
 *
 * <p>The source is always asked for a page and a size, but it does not always follow them: SWAPI
 * cuts the pages of some lists, and returns others, and every search, whole. There are two
 * possible answers, and the core decides what to do by which one arrived, never by which entity
 * it is.
 *
 * <p>The two answers are the same whatever an element is: its summary, or the element with its
 * complete data. The core pages both with the same code.
 *
 * @param <T> what each element of the answer is
 */
public sealed interface Listing<T> {

    /** The elements that arrived: the ones of the page, or every element of the list. */
    List<T> items();

    /**
     * The source cut the page and reported how many elements there are in all.
     *
     * @param items         the elements of the page that was asked for
     * @param totalElements the total of elements of the list
     */
    record OnePage<T>(List<T> items, int totalElements) implements Listing<T> {
    }

    /**
     * The source ignored the page and the size, and returned every element.
     *
     * @param items every element of the list
     */
    record Complete<T>(List<T> items) implements Listing<T> {
    }

}
