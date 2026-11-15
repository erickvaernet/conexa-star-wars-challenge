package io.github.erickvaernet.starwars.catalog;

import io.github.erickvaernet.starwars.shared.InvalidParameterException;
import java.util.regex.Pattern;

/**
 * Which page of a list is asked for, and how many elements it has. Both are already valid.
 *
 * <p>An invalid value is rejected, never corrected: asking for 500 elements and getting 100
 * without notice would look like the whole list.
 *
 * @param page the page number; the first page is 1
 * @param size how many elements a page has, from 1 to {@value #MAX_SIZE}
 */
public record PageQuery(int page, int size) {

    static final int DEFAULT_PAGE = 1;

    /** The same page size that SWAPI uses when it is not told one. */
    static final int DEFAULT_SIZE = 10;

    static final int MAX_SIZE = 100;

    private static final String PAGE_RULE = "must be an integer greater than or equal to 1";

    private static final String SIZE_RULE = "must be an integer between 1 and " + MAX_SIZE;

    /** Only the digits 0 to 9. Zeros on the left are allowed: "01" is 1. */
    private static final Pattern ONLY_DIGITS = Pattern.compile("[0-9]+");

    /** Rejects a page or a size out of range, so that an invalid query cannot exist. */
    public PageQuery {
        if (page < 1) {
            throw new InvalidParameterException("page", PAGE_RULE);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidParameterException("size", SIZE_RULE);
        }
    }

    /**
     * Builds the query from the parameters as they arrive in the request, as text.
     *
     * <p>A parameter that was not sent takes its default value. One that was sent and is not an
     * integer in range is rejected, and that includes an empty one.
     *
     * @param page the {@code page} parameter, or {@code null} if it was not sent
     * @param size the {@code size} parameter, or {@code null} if it was not sent
     * @throws InvalidParameterException if one of them is invalid; it names which
     */
    public static PageQuery of(String page, String size) {
        return new PageQuery(
                toInteger("page", page, DEFAULT_PAGE, PAGE_RULE),
                toInteger("size", size, DEFAULT_SIZE, SIZE_RULE));
    }

    private static int toInteger(String parameter, String value, int defaultValue, String rule) {
        if (value == null) {
            return defaultValue;
        }
        // Checked before reading the number: Java would also read a sign ("+1") and the digits
        // of any alphabet, and neither is what the API documentation calls an integer.
        if (!ONLY_DIGITS.matcher(value).matches()) {
            throw new InvalidParameterException(parameter, rule);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException tooLarge) {
            throw new InvalidParameterException(parameter, rule);
        }
    }

}
