package io.github.erickvaernet.starwars.catalog;

import io.github.erickvaernet.starwars.shared.InvalidParameterException;
import java.util.Locale;

/**
 * The text to search names by, already valid: from 1 to 100 characters, without counting the
 * spaces at its start and at its end.
 *
 * <p>It is the only place that says what a text to search looks like and when a name contains
 * it. The text is kept without the spaces around it and in lower case, because a search never
 * tells upper from lower case. So two searches that differ only in that are the same filter:
 * they are the same request to the data source, and the same stored answer.
 *
 * <p>The text is searched as it is. It is not a pattern: a point is a point.
 *
 * @param value the text, without the spaces around it and in lower case
 */
public record NameFilter(String value) {

    /** The longest text that can be searched, in characters. The API documentation shows it. */
    public static final int MAX_LENGTH = 100;

    private static final String RULE =
            "must have 1 to " + MAX_LENGTH + " characters, not counting leading and trailing spaces";

    /**
     * Removes the spaces around the text, rejects it if nothing is left or if it is too long,
     * and keeps it in lower case.
     *
     * @throws InvalidParameterException if the text is missing, empty, only spaces or longer
     *     than {@value #MAX_LENGTH} characters; it names the parameter {@code name}
     */
    public NameFilter {
        if (value == null) {
            throw new InvalidParameterException("name", RULE);
        }
        value = value.strip();
        // Counted in characters and not with length(): a character outside the basic set, like
        // an emoji, takes two units of a Java text and would count twice.
        int characters = value.codePointCount(0, value.length());
        if (characters < 1 || characters > MAX_LENGTH) {
            throw new InvalidParameterException("name", RULE);
        }
        value = lowerCase(value);
    }

    /**
     * Tells whether a name contains the text, anywhere in it and without telling upper from
     * lower case.
     *
     * @param name the name of an element, as the data source gives it
     */
    public boolean matches(String name) {
        return lowerCase(name).contains(value);
    }

    /**
     * With a fixed locale: without one, Java uses the language of the machine, and in Turkish
     * the lower case of "I" is not "i".
     */
    private static String lowerCase(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

}
