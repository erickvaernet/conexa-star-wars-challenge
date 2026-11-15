package io.github.erickvaernet.starwars.catalog;

/**
 * One element of a list: just enough to recognise it and to ask for its detail.
 *
 * @param id   the identifier, as text, the way the source gives it
 * @param name the name of the element; for a film, its title
 */
public record ItemSummary(String id, String name) {
}
