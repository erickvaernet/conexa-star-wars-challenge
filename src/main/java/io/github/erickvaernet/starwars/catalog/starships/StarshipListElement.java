package io.github.erickvaernet.starwars.catalog.starships;

import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The name that the API documentation gives to an element of the list of starships: one of two
 * things, the summary of a starship or its complete data.
 *
 * <p>It exists only for the documentation. Nothing implements it, and no code reads an element
 * as one of these: at run time the page holds {@link ItemSummary} or {@link StarshipDetail} objects.
 * The list of starships declares it as the type of its elements, and the library that generates
 * the documentation reads from there, and from the annotation below, that an element is "one of"
 * those two.
 *
 * <p>Why a type that nothing implements: Java has no way to say "one of two types", and the
 * documentation library only reads the two forms from the type of the answer. The summary
 * belongs to the common core, which does not know the entities, so it cannot implement an
 * interface of this package.
 */
@Schema(
        description = "An element of the list of starships. Without `expanded`, or with `expanded=false`, it is "
                + "the summary of a starship. With `expanded=true`, it is its complete data, the same object that "
                + "its detail answers.",
        oneOf = {ItemSummary.class, StarshipDetail.class})
interface StarshipListElement {
}
