package io.github.erickvaernet.starwars.catalog;

/**
 * What an entity declares to be listed and searched. It is all the core needs to know about it,
 * so adding an entity does not change the core. The data source needs one thing more, which is
 * not here: how to read the detail of the entity, which it declares on its own side.
 *
 * @param sourceName  the name of the entity in the data source: {@code people}, {@code films}...
 * @param searchField the field of the data source that holds the name of an element, and by
 *                    which the source searches: {@code name}; for a film, {@code title}
 */
public record CatalogEntity(String sourceName, String searchField) {
}
