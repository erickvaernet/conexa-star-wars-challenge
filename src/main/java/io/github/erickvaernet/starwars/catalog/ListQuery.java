package io.github.erickvaernet.starwars.catalog;

/**
 * What is asked of a list: which page, and which filter. Everything in it is already valid.
 *
 * @param page the page and the size that were asked for
 * @param id   the identifier to filter by, or {@code null} to list every element
 */
public record ListQuery(PageQuery page, ItemId id) {
}
