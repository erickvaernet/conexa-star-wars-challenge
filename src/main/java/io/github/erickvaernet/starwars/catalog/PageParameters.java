package io.github.erickvaernet.starwars.catalog;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The parameters of a list, as they arrive in the request. Every list takes them the same way:
 * its controller declares this record and nothing else.
 *
 * <p>They are received as text on purpose. Spring would turn them into numbers by itself, but it
 * would then answer a value that is not a number with a text of its own. Turning them into
 * numbers in {@link PageQuery} keeps every rule and every error text in one place. The API
 * documentation still shows them as what they are: integers, with their limits.
 *
 * @param page the {@code page} parameter, or {@code null} if it was not sent
 * @param size the {@code size} parameter, or {@code null} if it was not sent
 */
public record PageParameters(

        @Parameter(
                description = "Page number. The first page is 1. A page after the last one is empty.",
                schema = @Schema(type = "integer", format = "int32", minimum = "1", defaultValue = "1"))
        String page,

        @Parameter(
                description = "How many elements a page has.",
                schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                        defaultValue = "10"))
        String size) {

    /**
     * Turns the parameters into a valid query, with the default value of the ones that were not
     * sent.
     *
     * @throws io.github.erickvaernet.starwars.shared.InvalidParameterException if one of them is
     *     invalid; it names which
     */
    public ListQuery toQuery() {
        return new ListQuery(PageQuery.of(page, size), null);
    }

}
