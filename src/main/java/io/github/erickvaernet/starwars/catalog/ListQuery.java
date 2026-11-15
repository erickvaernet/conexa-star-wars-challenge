package io.github.erickvaernet.starwars.catalog;

import io.github.erickvaernet.starwars.shared.InvalidParameterException;

/**
 * What is asked of a list: which page, which filters, and how much data of each element.
 * Everything in it is already valid.
 *
 * <p>The two filters can come together: then the list has the element with that identifier only
 * if its name also contains the text. The filters say which elements are in the list;
 * {@code expanded} only says how much each one brings.
 *
 * @param page     the page and the size that were asked for
 * @param id       the identifier to filter by, or {@code null} not to filter by identifier
 * @param name     the text that the name must contain, or {@code null} not to filter by name
 * @param expanded whether each element comes with its complete data, instead of its summary
 */
public record ListQuery(PageQuery page, ItemId id, NameFilter name, boolean expanded) {

    private static final String EXPANDED_RULE = "must be true or false";

    /**
     * Reads the {@code expanded} parameter as it arrives in the request, as text. It is the only
     * place that says which values it accepts.
     *
     * <p>Only the two exact texts are accepted. Java and Spring would also read "TRUE", "1" or
     * "yes" as a boolean, each with a list of its own; accepting what the API documentation
     * says, and nothing else, leaves no value whose meaning has to be guessed.
     *
     * @param value the parameter, or {@code null} if it was not sent
     * @return {@code true} for "true"; {@code false} for "false" and for a parameter that was
     *     not sent, which is its default value
     * @throws InvalidParameterException if it is any other text, and that includes an empty one;
     *     it names the parameter {@code expanded}
     */
    public static boolean expandedFrom(String value) {
        if (value == null || value.equals("false")) {
            return false;
        }
        if (value.equals("true")) {
            return true;
        }
        throw new InvalidParameterException("expanded", EXPANDED_RULE);
    }

}
