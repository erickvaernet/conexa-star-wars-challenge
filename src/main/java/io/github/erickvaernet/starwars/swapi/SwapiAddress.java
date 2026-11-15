package io.github.erickvaernet.starwars.swapi;

import io.github.erickvaernet.starwars.catalog.ItemId;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import java.util.List;

/**
 * Takes the identifier of an element out of its address in SWAPI.
 *
 * <p>SWAPI gives the relations of an element as addresses of its own, such as
 * {@code https://www.swapi.tech/api/films/1}, and the identifier is their last segment. The
 * addresses stay in this package: the catalog only gets the identifiers.
 *
 * <p>This depends on the form of those addresses, which SWAPI does not promise. If one arrives
 * with another form the answer is an error, because a relation that is missing without notice
 * is harder to detect than a failure.
 */
final class SwapiAddress {

    private SwapiAddress() {
    }

    /**
     * Translates a relation into the identifiers of its elements, in the same order.
     *
     * @param addresses the addresses of the related elements, or {@code null} if SWAPI did not
     *                  send the relation, which is taken as a relation without elements
     * @throws BadGatewayException if one of the addresses does not end in an identifier. The
     *     relation is not answered with that element left out
     */
    static List<String> identifiers(List<String> addresses) {
        if (addresses == null) {
            return List.of();
        }
        return addresses.stream().map(SwapiAddress::identifier).toList();
    }

    /**
     * Takes the identifier out of one address: the text after its last slash.
     *
     * <p>That text must have the form of an identifier, the same one that is required of the
     * identifiers that the API receives. So every identifier that the API answers can be asked
     * back to it.
     *
     * @throws BadGatewayException if the address is missing, has no slash, or does not end in an
     *     identifier
     */
    static String identifier(String address) {
        String trimmed = address == null ? "" : address.strip();
        int lastSlash = trimmed.lastIndexOf('/');
        String lastSegment = trimmed.substring(lastSlash + 1);
        if (lastSlash < 0 || !ItemId.isValid(lastSegment)) {
            throw new BadGatewayException(
                    "SWAPI answered a relation with an address that does not end in an identifier: " + address, null);
        }
        return lastSegment;
    }

}
