package io.github.erickvaernet.starwars.catalog;

import java.util.Optional;

/**
 * Where the data of the catalog comes from: the lists and each element.
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

    /**
     * Asks the source for one element by its identifier.
     *
     * <p>That the element does not exist is a normal answer, not a failure: it is the empty
     * result. The same request serves the filter by identifier of a list and the detail.
     *
     * @param entity the entity of the element
     * @param id     the identifier of the element
     * @return the element with its complete data, or empty if the source has none with that
     *     identifier
     * @throws io.github.erickvaernet.starwars.shared.BadGatewayException     if the source fails
     *     or answers something that cannot be read
     * @throws io.github.erickvaernet.starwars.shared.GatewayTimeoutException if the source does
     *     not answer in time
     */
    Optional<CatalogItem> find(CatalogEntity entity, ItemId id);

}
