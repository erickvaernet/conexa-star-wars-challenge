package io.github.erickvaernet.starwars.catalog;

import org.springframework.stereotype.Service;

/**
 * The common core of the lists: the same code pages the four entities.
 *
 * <p>It asks the source for the page and then decides by how the source answered. It never asks
 * which entity it is, so an entity that SWAPI starts or stops paging needs no change here.
 */
@Service
public class CatalogService {

    private final CatalogSource source;

    public CatalogService(CatalogSource source) {
        this.source = source;
    }

    /**
     * Returns one page of the list of an entity.
     *
     * @param entity the entity to list
     * @param query  the page and the size that were asked for
     */
    public PageResult<ItemSummary> list(CatalogEntity entity, PageQuery query) {
        return switch (source.list(entity, query)) {
            case Listing.OnePage page -> PageResult.ofSourcePage(page.items(), page.totalElements(), query);
            case Listing.Complete all -> PageResult.sliceOf(all.items(), query);
        };
    }

}
