package io.github.erickvaernet.starwars.catalog;

import java.util.Optional;

/**
 * Where the data of the catalog comes from: the lists, the searches and each element.
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
     * Asks the source for the elements of an entity whose name contains a text, without telling
     * upper from lower case.
     *
     * <p>The page and the size are sent as in a list, and the answer has the same two forms: the
     * source may cut the page, or return every match at once. That there are no matches is a
     * normal answer: a list without elements.
     *
     * @param entity the entity to search in
     * @param name   the text that the name must contain
     * @param query  the page and the size to ask for
     * @return the page of matches with its total, or every match if the source does not cut pages
     * @throws io.github.erickvaernet.starwars.shared.BadGatewayException     if the source fails
     *     or answers something that cannot be read
     * @throws io.github.erickvaernet.starwars.shared.GatewayTimeoutException if the source does
     *     not answer in time
     */
    Listing search(CatalogEntity entity, NameFilter name, PageQuery query);

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
