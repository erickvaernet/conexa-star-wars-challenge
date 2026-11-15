package io.github.erickvaernet.starwars.catalog;

import io.github.erickvaernet.starwars.shared.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The common core of the catalog: the same code pages, filters and looks up the four entities.
 *
 * <p>For a list, it asks the source for the page and then decides by how the source answered. It
 * never asks which entity it is, so an entity that SWAPI starts or stops paging needs no change
 * here.
 */
@Service
public class CatalogService {

    private final CatalogSource source;

    public CatalogService(CatalogSource source) {
        this.source = source;
    }

    /**
     * Returns one page of the list of an entity, with its filter if it has one.
     *
     * <p>A filter by identifier does not go through the list of the source: the source is asked
     * for that element, and the list is the one or no element that it found. An identifier that
     * does not exist is an empty list and not an error, like any search without results.
     *
     * @param entity the entity to list
     * @param query  the page, the size and the filter that were asked for
     */
    public PageResult<ItemSummary> list(CatalogEntity entity, ListQuery query) {
        if (query.id() != null) {
            List<ItemSummary> found = source.find(entity, query.id()).map(CatalogItem::summary).stream().toList();
            // Cut like any whole list: a page after the first one is empty, and the totals still
            // count the element.
            return PageResult.sliceOf(found, query.page());
        }
        return switch (source.list(entity, query.page())) {
            case Listing.OnePage page -> PageResult.ofSourcePage(page.items(), page.totalElements(), query.page());
            case Listing.Complete all -> PageResult.sliceOf(all.items(), query.page());
        };
    }

    /**
     * Returns the complete data of one element.
     *
     * @param entity the entity of the element
     * @param id     the identifier of the element
     * @param type   the detail of that entity. The core does not know the details: each entity
     *               says which one it expects, and the source builds it
     * @throws NotFoundException if the source has no element with that identifier
     */
    public <T extends CatalogItem> T detail(CatalogEntity entity, ItemId id, Class<T> type) {
        CatalogItem item = source.find(entity, id).orElseThrow(NotFoundException::new);
        return type.cast(item);
    }

}
