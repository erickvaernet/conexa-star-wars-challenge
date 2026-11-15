package io.github.erickvaernet.starwars.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erickvaernet.starwars.shared.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks that the core decides by how the source answered, and never by which entity it is; and
 * what it does when it is asked for one element by its identifier, as a filter and as a detail.
 *
 * <p>The source is a fake one written here: the core is tested without SWAPI and without Spring.
 */
class CatalogServiceTest {

    private static final CatalogEntity ANY_ENTITY = new CatalogEntity("anything", "name");

    private static final List<ItemSummary> SIX_ITEMS = List.of(
            new ItemSummary("1", "first"),
            new ItemSummary("2", "second"),
            new ItemSummary("3", "third"),
            new ItemSummary("4", "fourth"),
            new ItemSummary("5", "fifth"),
            new ItemSummary("6", "sixth"));

    /** The only element that the fake source has when it is asked for one. */
    private static final FakeItem THIRD = new FakeItem("3", "third", "a value that only the detail has");

    private static final ItemId THIRD_ID = new ItemId("3");

    private static final ItemId MISSING_ID = new ItemId("9999");

    @Test
    @DisplayName("When the source answers with a page and its total, that page is used")
    void pageOfTheSourceIsUsed() {
        List<ItemSummary> secondPage = SIX_ITEMS.subList(2, 4);
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.OnePage(secondPage, 6)));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, unfiltered(2, 2));

        assertThat(page).isEqualTo(new PageResult<>(secondPage, 2, 2, 6, 3));
    }

    @Test
    @DisplayName("When the source answers with the whole list, the page is cut out here")
    void wholeListIsCutHere() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete(SIX_ITEMS)));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, unfiltered(2, 2));

        assertThat(page).isEqualTo(new PageResult<>(SIX_ITEMS.subList(2, 4), 2, 2, 6, 3));
    }

    @Test
    @DisplayName("When the source repeats its last page for a page after the last one, the page is empty")
    void pageAfterTheLastOneIsEmpty() {
        List<ItemSummary> lastPage = SIX_ITEMS.subList(4, 6);
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.OnePage(lastPage, 6)));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, unfiltered(50, 2));

        assertThat(page).isEqualTo(new PageResult<>(List.of(), 50, 2, 6, 3));
    }

    @Test
    @DisplayName("The source is always asked for the entity, the page and the size of the request")
    void sourceIsAskedForWhatWasRequested() {
        FakeSource source = new FakeSource(new Listing.Complete(List.of()));
        CatalogService catalog = new CatalogService(source);
        CatalogEntity people = new CatalogEntity("people", "name");

        catalog.list(people, unfiltered(9, 10));

        assertThat(source.asked).containsExactly(people, new PageQuery(9, 10));
    }

    @Test
    @DisplayName("Filtered by an identifier, the list is a page with that element, as a summary, out of a total of 1")
    void listFilteredByIdHasThatElement() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete(SIX_ITEMS)));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, filteredBy(THIRD_ID, 1, 10));

        assertThat(page).isEqualTo(new PageResult<>(List.of(new ItemSummary("3", "third")), 1, 10, 1, 1));
    }

    @Test
    @DisplayName("Filtered by an identifier that the source does not have, the list is an empty page with the totals at 0")
    void listFilteredByAMissingIdIsEmpty() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete(SIX_ITEMS)));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, filteredBy(MISSING_ID, 1, 10));

        assertThat(page).isEqualTo(new PageResult<>(List.of(), 1, 10, 0, 0));
    }

    @Test
    @DisplayName("Filtered by an identifier, a page after the first one is empty, and the totals still count the element")
    void pageAfterTheFirstOneOfAFilterByIdIsEmpty() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete(SIX_ITEMS)));

        assertThat(catalog.list(ANY_ENTITY, filteredBy(THIRD_ID, 2, 10)))
                .isEqualTo(new PageResult<>(List.of(), 2, 10, 1, 1));
        assertThat(catalog.list(ANY_ENTITY, filteredBy(THIRD_ID, 2, 1)))
                .isEqualTo(new PageResult<>(List.of(), 2, 1, 1, 1));
        // If the element does not exist, there is nothing to count on any page.
        assertThat(catalog.list(ANY_ENTITY, filteredBy(MISSING_ID, 2, 10)))
                .isEqualTo(new PageResult<>(List.of(), 2, 10, 0, 0));
    }

    @Test
    @DisplayName("Filtered by an identifier, the source is asked for that element of that entity, and never for the list")
    void filterByIdAsksTheSourceForThatElement() {
        FakeSource source = new FakeSource(new Listing.Complete(SIX_ITEMS));
        CatalogService catalog = new CatalogService(source);
        CatalogEntity people = new CatalogEntity("people", "name");

        catalog.list(people, filteredBy(THIRD_ID, 1, 10));

        assertThat(source.asked).containsExactly(people, THIRD_ID);
    }

    @Test
    @DisplayName("The detail of an element is the element of the source, as the type of its entity")
    void detailIsTheElementOfTheSource() {
        FakeSource source = new FakeSource(new Listing.Complete(SIX_ITEMS));
        CatalogService catalog = new CatalogService(source);
        CatalogEntity people = new CatalogEntity("people", "name");

        FakeItem detail = catalog.detail(people, THIRD_ID, FakeItem.class);

        assertThat(detail).isSameAs(THIRD);
        assertThat(source.asked).containsExactly(people, THIRD_ID);
    }

    @Test
    @DisplayName("The detail of an element that the source does not have is not found")
    void detailOfAMissingElementIsNotFound() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete(SIX_ITEMS)));

        assertThatThrownBy(() -> catalog.detail(ANY_ENTITY, MISSING_ID, FakeItem.class))
                .isInstanceOf(NotFoundException.class);
    }

    private static ListQuery unfiltered(int page, int size) {
        return new ListQuery(new PageQuery(page, size), null);
    }

    private static ListQuery filteredBy(ItemId id, int page, int size) {
        return new ListQuery(new PageQuery(page, size), id);
    }

    /**
     * The fake source. It answers every request for a list with the same listing, has a single
     * element, and notes down what it is asked.
     */
    private static final class FakeSource implements CatalogSource {

        private final Listing listing;

        private final List<Object> asked = new ArrayList<>();

        private FakeSource(Listing listing) {
            this.listing = listing;
        }

        @Override
        public Listing list(CatalogEntity entity, PageQuery query) {
            asked.add(entity);
            asked.add(query);
            return listing;
        }

        @Override
        public Optional<CatalogItem> find(CatalogEntity entity, ItemId id) {
            asked.add(entity);
            asked.add(id);
            return id.equals(THIRD_ID) ? Optional.of(THIRD) : Optional.empty();
        }

    }

    /** The detail of an element of an entity that exists only here. */
    private record FakeItem(String id, String name, String somethingElse) implements CatalogItem {

        @Override
        public ItemSummary summary() {
            return new ItemSummary(id, name);
        }

    }

}
