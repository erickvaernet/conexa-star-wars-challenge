package io.github.erickvaernet.starwars.catalog.people;

import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.CatalogService;
import io.github.erickvaernet.starwars.catalog.DetailErrorResponses;
import io.github.erickvaernet.starwars.catalog.ItemId;
import io.github.erickvaernet.starwars.catalog.ListErrorResponses;
import io.github.erickvaernet.starwars.catalog.PageParameters;
import io.github.erickvaernet.starwars.catalog.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The requests about people, the characters of Star Wars.
 *
 * <p>It only declares what is particular to people: the route, the name of the entity, the field
 * by which it is searched and the type of its detail. The paging, the filters and the search of
 * one element are the ones of the common core, the same for every entity.
 */
@RestController
@RequestMapping("/api/v1/people")
@Tag(name = "People", description = "The characters of Star Wars")
class PeopleController {

    /** In SWAPI the entity is called {@code people}, and it is searched by the field {@code name}. */
    private static final CatalogEntity PEOPLE = new CatalogEntity("people", "name");

    private final CatalogService catalog;

    PeopleController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listPeople",
            summary = "List people, one page at a time",
            description = "Each element has the identifier and the name of a character, in the order of SWAPI. "
                    + "With `id`, the list has only that character, or is empty if it does not exist. "
                    + "With `name`, it has the characters whose name contains that text. With both, it has "
                    + "that character only if its name contains the text. "
                    + "With `expanded=true`, each element is the complete data of the character, the same object "
                    + "that its detail answers, instead of its summary.")
    @ApiResponse(
            responseCode = "200",
            description = "The page that was asked for, with the totals of the list.",
            // The schema is still the one of the type that the method returns: declaring the
            // examples would otherwise leave the answer without one.
            useReturnTypeSchema = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = {
                        @ExampleObject(
                                name = "summary",
                                summary = "Without expanded: the summary of each element",
                                value = PeopleListExamples.SUMMARIES),
                        @ExampleObject(
                                name = "expanded",
                                summary = "With expanded=true: the complete data of each element",
                                value = PeopleListExamples.COMPLETE_ELEMENTS)}))
    @ListErrorResponses
    PageResult<PersonListElement> list(@ParameterObject PageParameters parameters) {
        return catalog.list(PEOPLE, parameters.toQuery());
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getPerson",
            summary = "Get the complete data of a character",
            description = "Every value is text, as SWAPI gives it. The relations are lists of identifiers "
                    + "of films, starships and vehicles, which can be asked for in this API.")
    @ApiResponse(responseCode = "200", description = "The character with that identifier.")
    @DetailErrorResponses
    PersonDetail detail(
            @Parameter(
                    description = "The identifier of the character. Only digits: from 1 to 10, without leading zeros.",
                    schema = @Schema(type = "string", pattern = ItemId.PATTERN))
            @PathVariable String id) {
        return catalog.detail(PEOPLE, new ItemId(id), PersonDetail.class);
    }

}
