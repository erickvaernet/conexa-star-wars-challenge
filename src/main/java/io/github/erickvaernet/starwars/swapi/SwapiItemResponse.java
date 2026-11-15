package io.github.erickvaernet.starwars.swapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.erickvaernet.starwars.catalog.CatalogItem;
import io.github.erickvaernet.starwars.shared.BadGatewayException;

/**
 * The answer of SWAPI to the request for one element, whatever its entity.
 *
 * <p>The outside of the answer is the same for all of them: the identifier, and next to it the
 * data of the element. What changes with the entity is that data, so it is a parameter.
 *
 * <p>Only what the application uses is read; the rest of the answer is ignored. This record and
 * its names never leave this package.
 *
 * @param result the element
 * @param <D>    the data of the element, in the format that SWAPI uses for its entity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SwapiItemResponse<D extends SwapiItemData>(Result<D> result) {

    /**
     * One element of SWAPI.
     *
     * @param uid        the identifier; SWAPI sends it as text
     * @param properties the data of the element
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result<D>(String uid, D properties) {
    }

    /**
     * Translates the answer into an element of the catalog.
     *
     * @throws BadGatewayException if the identifier or the data are missing, or if the data
     *     cannot be translated
     */
    CatalogItem toItem() {
        if (result == null || result.properties() == null) {
            throw new BadGatewayException("SWAPI answered an element without its result or its properties", null);
        }
        return result.properties().toItem(SwapiText.required(result.uid(), "uid"));
    }

}
