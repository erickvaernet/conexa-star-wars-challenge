package io.github.erickvaernet.starwars.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks that the core decides by how the source answered, and never by which entity it is.
 *
 * <p>The source is a fake one written here: the core is tested without SWAPI and without Spring.
 */
class CatalogServiceTest {

    private static final CatalogEntity ANY_ENTITY = new CatalogEntity("anything");

    private static final List<ItemSummary> SIX_ITEMS = List.of(
            new ItemSummary("1", "first"),
            new ItemSummary("2", "second"),
            new ItemSummary("3", "third"),
            new ItemSummary("4", "fourth"),
            new ItemSummary("5", "fifth"),
            new ItemSummary("6", "sixth"));

    @Test
    @DisplayName("When the source answers with a page and its total, that page is used")
    void pageOfTheSourceIsUsed() {
        List<ItemSummary> secondPage = SIX_ITEMS.subList(2, 4);
        CatalogService catalog = new CatalogService((entity, query) -> new Listing.OnePage(secondPage, 6));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, new PageQuery(2, 2));

        assertThat(page).isEqualTo(new PageResult<>(secondPage, 2, 2, 6, 3));
    }

    @Test
    @DisplayName("When the source answers with the whole list, the page is cut out here")
    void wholeListIsCutHere() {
        CatalogService catalog = new CatalogService((entity, query) -> new Listing.Complete(SIX_ITEMS));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, new PageQuery(2, 2));

        assertThat(page).isEqualTo(new PageResult<>(SIX_ITEMS.subList(2, 4), 2, 2, 6, 3));
    }

    @Test
    @DisplayName("When the source repeats its last page for a page after the last one, the page is empty")
    void pageAfterTheLastOneIsEmpty() {
        List<ItemSummary> lastPage = SIX_ITEMS.subList(4, 6);
        CatalogService catalog = new CatalogService((entity, query) -> new Listing.OnePage(lastPage, 6));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, new PageQuery(50, 2));

        assertThat(page).isEqualTo(new PageResult<>(List.of(), 50, 2, 6, 3));
    }

    @Test
    @DisplayName("The source is always asked for the entity, the page and the size of the request")
    void sourceIsAskedForWhatWasRequested() {
        List<Object> asked = new ArrayList<>();
        CatalogService catalog = new CatalogService((entity, query) -> {
            asked.add(entity);
            asked.add(query);
            return new Listing.Complete(List.of());
        });
        CatalogEntity people = new CatalogEntity("people");

        catalog.list(people, new PageQuery(9, 10));

        assertThat(asked).containsExactly(people, new PageQuery(9, 10));
    }

}
