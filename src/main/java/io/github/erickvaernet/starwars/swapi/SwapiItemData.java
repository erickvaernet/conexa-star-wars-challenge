package io.github.erickvaernet.starwars.swapi;

import io.github.erickvaernet.starwars.catalog.CatalogItem;

/**
 * The data of one element as SWAPI sends it, with the names of SWAPI, able to translate itself
 * into the model of the catalog.
 *
 * <p>Each entity has one implementation: it is the piece that knows the names that SWAPI gives
 * to the fields of that entity. Those names never leave this package.
 */
interface SwapiItemData {

    /**
     * Translates the data into the detail of the element.
     *
     * @param id the identifier of the element; SWAPI sends it next to the data, not inside it
     * @throws io.github.erickvaernet.starwars.shared.BadGatewayException if the name is missing,
     *     or if a relation has an address that does not end in an identifier
     */
    CatalogItem toItem(String id);

}
