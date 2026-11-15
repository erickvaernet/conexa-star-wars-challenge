package io.github.erickvaernet.starwars.catalog;

/**
 * What an entity declares to be listed. It is all the core and the data source need to know
 * about it, so adding an entity changes neither of them.
 *
 * @param sourceName the name of the entity in the data source: {@code people}, {@code films}...
 */
public record CatalogEntity(String sourceName) {
}
