package io.github.erickvaernet.starwars.catalog;

/**
 * What is asked of a list: which page, and which filters. Everything in it is already valid.
 *
 * <p>The two filters can come together: then the list has the element with that identifier only
 * if its name also contains the text.
 *
 * @param page the page and the size that were asked for
 * @param id   the identifier to filter by, or {@code null} not to filter by identifier
 * @param name the text that the name must contain, or {@code null} not to filter by name
 */
public record ListQuery(PageQuery page, ItemId id, NameFilter name) {
}
