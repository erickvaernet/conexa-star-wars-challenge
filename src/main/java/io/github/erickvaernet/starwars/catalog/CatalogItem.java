package io.github.erickvaernet.starwars.catalog;

/**
 * One element with its complete data, whatever its entity.
 *
 * <p>The detail of each entity has its own fields and lives in the package of that entity. This
 * is the only thing the core needs from all of them: how the element looks in a list. With it,
 * the filter by identifier works the same for every entity.
 */
public interface CatalogItem {

    /** The element as a list shows it: its identifier and its name. For a film, its title. */
    ItemSummary summary();

}
