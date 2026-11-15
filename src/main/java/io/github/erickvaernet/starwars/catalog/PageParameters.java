package io.github.erickvaernet.starwars.catalog;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The parameters of a list, as they arrive in the request. Every list takes them the same way:
 * its controller declares this record and nothing else.
 *
 * <p>They are received as text on purpose. Spring would turn the page and the size into numbers
 * by itself, but it would then answer a value that is not a number with a text of its own.
 * Turning them into numbers in {@link PageQuery}, and checking the identifier in {@link ItemId}
 * and the name in {@link NameFilter}, and reading {@code expanded} in {@link ListQuery}, keeps
 * every rule and every error text in one place. The API documentation still shows them as what
 * they are: two integers with their limits, a text with its pattern, a text with its length and
 * a boolean.
 *
 * @param page     the {@code page} parameter, or {@code null} if it was not sent
 * @param size     the {@code size} parameter, or {@code null} if it was not sent
 * @param id       the {@code id} parameter, or {@code null} if it was not sent
 * @param name     the {@code name} parameter, or {@code null} if it was not sent
 * @param expanded the {@code expanded} parameter, or {@code null} if it was not sent
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
        String size,

        @Parameter(
                description = "Lists only the element with this identifier. If it does not exist, the list is empty. "
                        + "Only digits: from 1 to 10, without leading zeros.",
                schema = @Schema(type = "string", pattern = ItemId.PATTERN))
        String id,

        @Parameter(
                description = "Lists only the elements whose name contains this text, anywhere in it and without "
                        + "telling upper from lower case. From 1 to 100 characters, not counting the spaces at its "
                        + "start and at its end. Together with `id`, the list has that element only if its name "
                        + "contains the text.",
                schema = @Schema(type = "string", minLength = 1, maxLength = NameFilter.MAX_LENGTH))
        String name,

        @Parameter(
                description = "How much data each element of the list brings. With `true`, each element is the "
                        + "same object that the detail of the entity answers, with its complete data. With "
                        + "`false`, or without the parameter, each element is its summary: `id` and `name`. "
                        + "It does not change which elements are listed, their order or the totals. Only "
                        + "`true` and `false` are accepted, in lower case.",
                schema = @Schema(type = "boolean", defaultValue = "false"))
        String expanded) {

    /**
     * Turns the parameters into a valid query, with the default value of the page, of the size
     * and of {@code expanded} if they were not sent, and without the filters that were not sent.
     *
     * @throws io.github.erickvaernet.starwars.shared.InvalidParameterException if one of them is
     *     invalid; it names which. If several are, it names the first one in this order: page,
     *     size, id, name and expanded
     */
    public ListQuery toQuery() {
        return new ListQuery(
                PageQuery.of(page, size),
                id == null ? null : new ItemId(id),
                name == null ? null : new NameFilter(name),
                ListQuery.expandedFrom(expanded));
    }

}
