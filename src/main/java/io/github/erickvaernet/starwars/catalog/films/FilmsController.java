package io.github.erickvaernet.starwars.catalog.films;

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
 * The requests about the films of Star Wars.
 *
 * <p>It only declares what is particular to films: the route, the name of the entity, the field
 * by which it is searched and the type of its detail. The paging, the filters and the search of
 * one element are the ones of the common core, the same for every entity.
 *
 * <p>SWAPI treats films in its own way: it does not cut their list in pages, and it searches
 * them by their title. Neither shows here nor in the API. The core cuts the page out of the
 * whole list, and the title is the name of a film in a list and what the {@code name} filter
 * looks at.
 */
@RestController
@RequestMapping("/api/v1/films")
@Tag(name = "Films", description = "The films of Star Wars")
class FilmsController {

    /** In SWAPI the entity is called {@code films}, and it is searched by the field {@code title}. */
    private static final CatalogEntity FILMS = new CatalogEntity("films", "title");

    private final CatalogService catalog;

    FilmsController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listFilms",
            summary = "List films, one page at a time",
            description = "Each element has the identifier of a film and its title, as `name`, in the order of "
                    + "SWAPI: the one of release, not the one of the episodes. "
                    + "With `id`, the list has only that film, or is empty if it does not exist. "
                    + "With `name`, it has the films whose title contains that text. With both, it has "
                    + "that film only if its title contains the text. "
                    + "With `expanded=true`, each element is the complete data of the film, the same object "
                    + "that its detail answers, where its title is `title`, instead of its summary.")
    @ApiResponse(responseCode = "200", description = "The page that was asked for, with the totals of the list.")
    @ListErrorResponses
    PageResult<FilmListElement> list(@ParameterObject PageParameters parameters) {
        return catalog.list(FILMS, parameters.toQuery());
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getFilm",
            summary = "Get the complete data of a film",
            description = "Every value is text, as SWAPI gives it, except the episode, which is a whole number. "
                    + "The relations are lists of identifiers of characters, starships and vehicles, which can "
                    + "be asked for in this API.")
    @ApiResponse(responseCode = "200", description = "The film with that identifier.")
    @DetailErrorResponses
    FilmDetail detail(
            @Parameter(
                    description = "The identifier of the film. Only digits: from 1 to 10, without leading zeros.",
                    schema = @Schema(type = "string", pattern = ItemId.PATTERN))
            @PathVariable String id) {
        return catalog.detail(FILMS, new ItemId(id), FilmDetail.class);
    }

}
