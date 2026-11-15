package io.github.erickvaernet.starwars.catalog;

import java.util.List;

/**
 * One page of a list. It is the answer of every list of the API, written as JSON as it is.
 *
 * @param content       the elements of the page, in the order of the source
 * @param page          the page that was asked for
 * @param size          the page size that was asked for
 * @param totalElements how many elements there are in all
 * @param totalPages    {@code totalElements / size}, rounded up; 0 when there are no elements
 * @param <T>           the type of the elements
 */
public record PageResult<T>(List<T> content, int page, int size, int totalElements, int totalPages) {

    /**
     * Builds the page when the source already answered with one page and the total of elements.
     *
     * <p>The elements are used as they came, with one exception: a page after the last one is
     * always empty. The source cannot be trusted there, because SWAPI answers such a page by
     * repeating its last one.
     *
     * @param items         the elements of the page, as the source sent them
     * @param totalElements the total that the source reported
     * @param query         the page and the size that were asked for
     */
    public static <T> PageResult<T> ofSourcePage(List<T> items, int totalElements, PageQuery query) {
        int totalPages = totalPages(totalElements, query.size());
        List<T> content = query.page() > totalPages ? List.of() : List.copyOf(items);
        return new PageResult<>(content, query.page(), query.size(), totalElements, totalPages);
    }

    /**
     * Builds the page when the source answered with the whole list: cuts the page out of it and
     * counts the totals.
     *
     * @param allItems every element, in the order of the source
     * @param query    the page and the size that were asked for
     */
    public static <T> PageResult<T> sliceOf(List<T> allItems, PageQuery query) {
        // As a long: a very large page number times the size does not fit in an int.
        long elementsBefore = (long) (query.page() - 1) * query.size();
        List<T> content = allItems.stream().skip(elementsBefore).limit(query.size()).toList();
        int totalElements = allItems.size();
        return new PageResult<>(
                content, query.page(), query.size(), totalElements, totalPages(totalElements, query.size()));
    }

    private static int totalPages(int totalElements, int size) {
        return Math.ceilDiv(totalElements, size);
    }

}
