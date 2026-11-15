package io.github.erickvaernet.starwars.catalog;

/**
 * Where the data of the lists comes from.
 *
 * <p>The core depends on this interface and not on SWAPI: {@code swapi} implements it, and that
 * is the only place that knows the address and the format of SWAPI.
 */
public interface CatalogSource {

    /**
     * Asks the source for one page of the list of an entity.
     *
     * @param entity the entity to list
     * @param query  the page and the size to ask for
     * @return the page with its total, or the whole list if the source does not cut pages
     * @throws io.github.erickvaernet.starwars.shared.BadGatewayException     if the source fails
     *     or answers something that cannot be read
     * @throws io.github.erickvaernet.starwars.shared.GatewayTimeoutException if the source does
     *     not answer in time
     */
    Listing list(CatalogEntity entity, PageQuery query);

}
