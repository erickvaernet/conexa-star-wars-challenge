package io.github.erickvaernet.starwars.catalog.starships;

import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.CatalogService;
import io.github.erickvaernet.starwars.catalog.DetailErrorResponses;
import io.github.erickvaernet.starwars.catalog.ItemId;
import io.github.erickvaernet.starwars.catalog.ListErrorResponses;
import io.github.erickvaernet.starwars.catalog.PageParameters;
import io.github.erickvaernet.starwars.catalog.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * The requests about the starships of Star Wars.
 *
 * <p>It only declares what is particular to starships: the route, the name of the entity, the
 * field by which it is searched and the type of its detail. The paging, the filters and the
 * search of one element are the ones of the common core, the same for every entity.
 */
@RestController
@RequestMapping("/api/v1/starships")
@Tag(name = "Starships", description = "The starships of Star Wars")
class StarshipsController {

    /** In SWAPI the entity is called {@code starships}, and it is searched by the field {@code name}. */
    private static final CatalogEntity STARSHIPS = new CatalogEntity("starships", "name");

    private final CatalogService catalog;

    StarshipsController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listStarships",
            summary = "List starships, one page at a time",
            description = "Each element has the identifier of a starship and its name, in the order of SWAPI, "
                    + "which is not the one of the identifiers. "
                    + "With `id`, the list has only that starship, or is empty if it does not exist. "
                    + "With `name`, it has the starships whose name contains that text. With both, it has "
                    + "that starship only if its name contains the text. "
                    + "With `expanded=true`, each element is the complete data of the starship, the same object "
                    + "that its detail answers, instead of its summary.")
    @ApiResponse(responseCode = "200", description = "The page that was asked for, with the totals of the list.")
    @ListErrorResponses
    PageResult<StarshipListElement> list(@ParameterObject PageParameters parameters) {
        return catalog.list(STARSHIPS, parameters.toQuery());
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getStarship",
            summary = "Get the complete data of a starship",
            description = "Every value is text, as SWAPI gives it, the quantities too: some are not plain "
                    + "numbers, like \"342,953\", \"n/a\" or \"3 years\". "
                    + "The relations are lists of identifiers of films and of characters, the pilots, which "
                    + "can be asked for in this API.")
    @ApiResponse(responseCode = "200", description = "The starship with that identifier.")
    @DetailErrorResponses
    StarshipDetail detail(
            @Parameter(
                    description = "The identifier of the starship. Only digits: from 1 to 10, without leading zeros.",
                    schema = @Schema(type = "string", pattern = ItemId.PATTERN))
            @PathVariable String id) {
        return catalog.detail(STARSHIPS, new ItemId(id), StarshipDetail.class);
    }

}
