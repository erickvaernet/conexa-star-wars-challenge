package io.github.erickvaernet.starwars.catalog;

import io.github.erickvaernet.starwars.shared.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The common core of the catalog: the same code pages, filters and looks up the four entities.
 *
 * <p>For a list or a search, it asks the source for the page and then decides by how the source
 * answered. It never asks which entity it is, so an entity that SWAPI starts or stops paging
 * needs no change here.
 *
 * <p>The elements of a page are summaries or, when the request asks for them, the elements with
 * their complete data. The core pages both with the same code and never looks inside a complete
 * element: all it knows of one is its summary.
 */
@Service
public class CatalogService {

    private final CatalogSource source;

    public CatalogService(CatalogSource source) {
        this.source = source;
    }

    /**
     * Returns one page of the list of an entity, with its filters if it has any.
     *
     * <p>A filter by identifier does not go through the list of the source: the source is asked
     * for that element, and the list is the one or no element that it found. An identifier that
     * does not exist is an empty list and not an error, like any search without results.
     *
     * <p>With the identifier and the name together, the element is asked for in the same way,
     * and it stays in the list only if its name contains the text. The source is not asked to
     * search: one element is enough to know.
     *
     * <p>With the name alone, the source searches. Its matches are used as they come.
     *
     * <p>When the query asks for the complete data, the elements and the totals are the same and
     * only what each element brings changes: the source is asked for the same page with its
     * complete elements, in a single request, and a filter by identifier returns the element it
     * found instead of its summary.
     *
     * @param entity the entity to list
     * @param query  the page, the size and the filters that were asked for, and whether each
     *               element comes with its complete data
     * @param <E>    the name that the entity gives to an element of its list in the API
     *               documentation, such as {@code PersonListElement}: "its summary or its
     *               detail". It is an interface that nothing implements, and it says nothing
     *               about what the page really holds
     */
    public <E> PageResult<E> list(CatalogEntity entity, ListQuery query) {
        PageResult<?> page = query.expanded() ? expandedPage(entity, query) : summaryPage(entity, query);
        // The one conversion that the compiler cannot check. The page holds summaries or complete
        // elements, and neither of them is an E.
        // - Why it is harmless: the type of the elements is erased when the application runs,
        //   and the page is only written as JSON, which looks at each object and not at this
        //   type. No code reads an element of the page as an E.
        // - Why it is here: Java has no type for "one of two", and the library that generates the
        //   API documentation only learns the two forms from the type of the answer of each list.
        @SuppressWarnings("unchecked")
        PageResult<E> named = (PageResult<E>) page;
        return named;
    }

    private PageResult<ItemSummary> summaryPage(CatalogEntity entity, ListQuery query) {
        if (query.id() != null) {
            return PageResult.sliceOf(found(entity, query).stream().map(CatalogItem::summary).toList(), query.page());
        }
        return page(query.name() == null
                ? source.list(entity, query.page())
                : source.search(entity, query.name(), query.page()), query.page());
    }

    private PageResult<CatalogItem> expandedPage(CatalogEntity entity, ListQuery query) {
        if (query.id() != null) {
            return PageResult.sliceOf(found(entity, query), query.page());
        }
        return page(query.name() == null
                ? source.listExpanded(entity, query.page())
                : source.searchExpanded(entity, query.name(), query.page()), query.page());
    }

    /**
     * The one or no element that a filter by identifier leaves: the element with that identifier,
     * if the source has it and, when there is also a filter by name, if its name contains the
     * text. It is cut afterwards like any whole list: a page after the first one is empty, and
     * the totals still count the element.
     */
    private List<CatalogItem> found(CatalogEntity entity, ListQuery query) {
        return source.find(entity, query.id())
                .filter(item -> query.name() == null || query.name().matches(item.summary().name()))
                .stream().toList();
    }

    /** Builds the page by how the source answered, whatever its elements are. */
    private static <T> PageResult<T> page(Listing<T> listing, PageQuery query) {
        return switch (listing) {
            case Listing.OnePage<T> page -> PageResult.ofSourcePage(page.items(), page.totalElements(), query);
            case Listing.Complete<T> all -> PageResult.sliceOf(all.items(), query);
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
