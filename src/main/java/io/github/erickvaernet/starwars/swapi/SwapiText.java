package io.github.erickvaernet.starwars.swapi;

import io.github.erickvaernet.starwars.shared.BadGatewayException;

/**
 * What is done to a text of SWAPI before it is answered: the spaces at its start and at its end
 * are removed, and nothing else. The values are not interpreted nor converted.
 */
final class SwapiText {

    private SwapiText() {
    }

    /**
     * Cleans a text that an element may lack.
     *
     * @return the text without the spaces around it, or {@code null} if SWAPI did not send it.
     *     An empty text stays empty: it is not the same as a missing one
     */
    static String optional(String text) {
        return text == null ? null : text.strip();
    }

    /**
     * Cleans a text without which the element cannot be shown: its identifier or its name.
     *
     * @param text the text, as SWAPI sent it
     * @param what which data it is, for the log
     * @throws BadGatewayException if the text is missing or blank
     */
    static String required(String text, String what) {
        if (text == null || text.isBlank()) {
            throw new BadGatewayException("SWAPI answered an element without " + what, null);
        }
        return text.strip();
    }

}
