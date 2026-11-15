package io.github.erickvaernet.starwars.catalog.people;

import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.CatalogService;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.ListErrorResponses;
import io.github.erickvaernet.starwars.catalog.PageParameters;
import io.github.erickvaernet.starwars.catalog.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The requests about people, the characters of Star Wars.
 *
 * <p>It only declares what is particular to people: the route and the name of the entity. The
 * paging is the one of the common core, the same for every entity.
 */
@RestController
@RequestMapping("/api/v1/people")
@Tag(name = "People", description = "The characters of Star Wars")
class PeopleController {

    private static final CatalogEntity PEOPLE = new CatalogEntity("people");

    private final CatalogService catalog;

    PeopleController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "List people, one page at a time",
            description = "Each element has the identifier and the name of a character, in the order of SWAPI.")
    @ApiResponse(responseCode = "200", description = "The page that was asked for, with the totals of the list.")
    @ListErrorResponses
    PageResult<ItemSummary> list(@ParameterObject PageParameters parameters) {
        return catalog.list(PEOPLE, parameters.toQuery());
    }

}
