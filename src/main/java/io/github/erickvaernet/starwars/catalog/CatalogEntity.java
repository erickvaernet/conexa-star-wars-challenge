package io.github.erickvaernet.starwars.catalog;

/**
 * What an entity declares to be listed and searched. It is all the core and the data source need
 * to know about it, so adding an entity changes neither of them.
 *
 * @param sourceName  the name of the entity in the data source: {@code people}, {@code films}...
 * @param searchField the field of the data source that holds the name of an element, and by
 *                    which the source searches: {@code name}; for a film, {@code title}
 */
public record CatalogEntity(String sourceName, String searchField) {
}
