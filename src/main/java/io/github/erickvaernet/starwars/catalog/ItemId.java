package io.github.erickvaernet.starwars.catalog;

import io.github.erickvaernet.starwars.shared.InvalidParameterException;
import java.util.regex.Pattern;

/**
 * The identifier of an element, already valid: only digits, from 1 to 10, without leading zeros.
 *
 * <p>It is the only place that says what an identifier looks like. The parameter of a list and
 * the route of a detail both become one of these before anything is asked to the data source, so
 * an invalid value never reaches the address that is asked for.
 *
 * <p>The identifier is kept as text, the way SWAPI gives it. Only its form is checked: today
 * every identifier of SWAPI is a number, and if that changed this is the rule to loosen.
 *
 * @param value the identifier, as text
 */
public record ItemId(String value) {

    /** The form of an identifier, as a regular expression. The API documentation shows it. */
    public static final String PATTERN = "^[1-9][0-9]{0,9}$";

    private static final Pattern FORM = Pattern.compile(PATTERN);

    private static final String RULE = "must be a number of 1 to 10 digits, without leading zeros";

    /**
     * Rejects a text that is not an identifier, so that an invalid one cannot exist.
     *
     * @throws InvalidParameterException if the text does not have the form of an identifier; it
     *     names the parameter {@code id}
     */
    public ItemId {
        if (!isValid(value)) {
            throw new InvalidParameterException("id", RULE);
        }
    }

    /**
     * Tells whether a text has the form of an identifier. Nothing is trimmed nor corrected.
     *
     * @param text the text to check, or {@code null}
     */
    public static boolean isValid(String text) {
        return text != null && FORM.matcher(text).matches();
    }

}
