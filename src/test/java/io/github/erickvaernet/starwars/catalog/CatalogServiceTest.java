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
 * what it does when it is asked for one element by its identifier, as a filter and as a detail,
 * and when it is asked to filter by name, alone and together with the identifier. And that it
 * does the same with the elements that come with their complete data, without looking inside
 * them.
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

    /** The six elements with their complete data. The third one is the same {@link #THIRD}. */
    private static final List<CatalogItem> SIX_COMPLETE_ITEMS = List.of(
            new FakeItem("1", "first", "a value of the first"),
            new FakeItem("2", "second", "a value of the second"),
            THIRD,
            new FakeItem("4", "fourth", "a value of the fourth"),
            new FakeItem("5", "fifth", "a value of the fifth"),
            new FakeItem("6", "sixth", "a value of the sixth"));

    private static final ItemId THIRD_ID = new ItemId("3");

    private static final ItemId MISSING_ID = new ItemId("9999");

    @Test
    @DisplayName("When the source answers with a page and its total, that page is used")
    void pageOfTheSourceIsUsed() {
        List<ItemSummary> secondPage = SIX_ITEMS.subList(2, 4);
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.OnePage<>(secondPage, 6)));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, unfiltered(2, 2));

        assertThat(page).isEqualTo(new PageResult<>(secondPage, 2, 2, 6, 3));
    }

    @Test
    @DisplayName("When the source answers with the whole list, the page is cut out here")
    void wholeListIsCutHere() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete<>(SIX_ITEMS)));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, unfiltered(2, 2));

        assertThat(page).isEqualTo(new PageResult<>(SIX_ITEMS.subList(2, 4), 2, 2, 6, 3));
    }

    @Test
    @DisplayName("When the source repeats its last page for a page after the last one, the page is empty")
    void pageAfterTheLastOneIsEmpty() {
        List<ItemSummary> lastPage = SIX_ITEMS.subList(4, 6);
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.OnePage<>(lastPage, 6)));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, unfiltered(50, 2));

        assertThat(page).isEqualTo(new PageResult<>(List.of(), 50, 2, 6, 3));
    }

    @Test
    @DisplayName("The source is always asked for the entity, the page and the size of the request")
    void sourceIsAskedForWhatWasRequested() {
        FakeSource source = new FakeSource(new Listing.Complete<>(List.of()));
        CatalogService catalog = new CatalogService(source);
        CatalogEntity people = new CatalogEntity("people", "name");

        catalog.list(people, unfiltered(9, 10));

        assertThat(source.asked).containsExactly(people, new PageQuery(9, 10));
    }

    @Test
    @DisplayName("Filtered by an identifier, the list is a page with that element, as a summary, out of a total of 1")
    void listFilteredByIdHasThatElement() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete<>(SIX_ITEMS)));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, filteredBy(THIRD_ID, 1, 10));

        assertThat(page).isEqualTo(new PageResult<>(List.of(new ItemSummary("3", "third")), 1, 10, 1, 1));
    }

    @Test
    @DisplayName("Filtered by an identifier that the source does not have, the list is an empty page with the totals at 0")
    void listFilteredByAMissingIdIsEmpty() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete<>(SIX_ITEMS)));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, filteredBy(MISSING_ID, 1, 10));

        assertThat(page).isEqualTo(new PageResult<>(List.of(), 1, 10, 0, 0));
    }

    @Test
    @DisplayName("Filtered by an identifier, a page after the first one is empty, and the totals still count the element")
    void pageAfterTheFirstOneOfAFilterByIdIsEmpty() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete<>(SIX_ITEMS)));

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
        FakeSource source = new FakeSource(new Listing.Complete<>(SIX_ITEMS));
        CatalogService catalog = new CatalogService(source);
        CatalogEntity people = new CatalogEntity("people", "name");

        catalog.list(people, filteredBy(THIRD_ID, 1, 10));

        assertThat(source.asked).containsExactly(people, THIRD_ID);
    }

    @Test
    @DisplayName("Filtered by name, the source is asked to search that text in that entity, with the page and the size, and never for the list")
    void filterByNameAsksTheSourceToSearch() {
        FakeSource source = new FakeSource(new Listing.Complete<>(List.of()));
        CatalogService catalog = new CatalogService(source);
        CatalogEntity people = new CatalogEntity("people", "name");

        catalog.list(people, filteredBy("sky", 2, 5));

        assertThat(source.asked).containsExactly(people, new NameFilter("sky"), new PageQuery(2, 5));
    }

    @Test
    @DisplayName("When the source answers a search with every match, the page is cut out here and the totals are counted here")
    void matchesOfASearchAreCutHere() {
        List<ItemSummary> threeMatches = SIX_ITEMS.subList(0, 3);
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete<>(threeMatches)));

        assertThat(catalog.list(ANY_ENTITY, filteredBy("anything", 1, 10)))
                .isEqualTo(new PageResult<>(threeMatches, 1, 10, 3, 1));
        assertThat(catalog.list(ANY_ENTITY, filteredBy("anything", 2, 2)))
                .isEqualTo(new PageResult<>(SIX_ITEMS.subList(2, 3), 2, 2, 3, 2));
        assertThat(catalog.list(ANY_ENTITY, filteredBy("anything", 3, 2)))
                .isEqualTo(new PageResult<>(List.of(), 3, 2, 3, 2));
    }

    @Test
    @DisplayName("The matches are the ones the source found: the core does not filter them again")
    void matchesOfTheSourceAreNotFilteredAgain() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete<>(SIX_ITEMS)));

        // None of the six names contains this text. They are what the source answered.
        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, filteredBy("zzzz", 1, 10));

        assertThat(page.content()).isEqualTo(SIX_ITEMS);
    }

    @Test
    @DisplayName("If the source answered a search with a page and its total, that page is used")
    void pageOfASearchIsUsed() {
        List<ItemSummary> secondPage = SIX_ITEMS.subList(2, 4);
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.OnePage<>(secondPage, 6)));

        PageResult<ItemSummary> page = catalog.list(ANY_ENTITY, filteredBy("anything", 2, 2));

        assertThat(page).isEqualTo(new PageResult<>(secondPage, 2, 2, 6, 3));
    }

    @Test
    @DisplayName("A search without matches is an empty page with the totals at 0")
    void searchWithoutMatchesIsEmpty() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete<>(List.of())));

        assertThat(catalog.list(ANY_ENTITY, filteredBy("zzzz", 1, 10)))
                .isEqualTo(new PageResult<>(List.of(), 1, 10, 0, 0));
    }

    @Test
    @DisplayName("Filtered by identifier and name, the list has the element when its name contains the text, without telling upper from lower case")
    void listFilteredByIdAndNameHasTheElementWhoseNameContainsTheText() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete<>(SIX_ITEMS)));
        PageResult<ItemSummary> theThird = new PageResult<>(List.of(new ItemSummary("3", "third")), 1, 10, 1, 1);

        assertThat(catalog.list(ANY_ENTITY, filteredBy(THIRD_ID, "third", 1, 10))).isEqualTo(theThird);
        assertThat(catalog.list(ANY_ENTITY, filteredBy(THIRD_ID, "hir", 1, 10))).isEqualTo(theThird);
        assertThat(catalog.list(ANY_ENTITY, filteredBy(THIRD_ID, "THIRD", 1, 10))).isEqualTo(theThird);
    }

    @Test
    @DisplayName("Filtered by identifier and name, the list is empty with the totals at 0 when the name does not contain the text")
    void listFilteredByIdAndNameIsEmptyWhenTheNameDoesNotMatch() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete<>(SIX_ITEMS)));

        // The text is the name of another element: the two filters have to be met by the same one.
        assertThat(catalog.list(ANY_ENTITY, filteredBy(THIRD_ID, "second", 1, 10)))
                .isEqualTo(new PageResult<>(List.of(), 1, 10, 0, 0));
        // A missing element has no name to compare.
        assertThat(catalog.list(ANY_ENTITY, filteredBy(MISSING_ID, "third", 1, 10)))
                .isEqualTo(new PageResult<>(List.of(), 1, 10, 0, 0));
    }

    @Test
    @DisplayName("Filtered by identifier and name, a page after the first one is empty, and the totals count the element only if it matches")
    void pageAfterTheFirstOneOfAFilterByIdAndNameIsEmpty() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete<>(SIX_ITEMS)));

        assertThat(catalog.list(ANY_ENTITY, filteredBy(THIRD_ID, "third", 2, 10)))
                .isEqualTo(new PageResult<>(List.of(), 2, 10, 1, 1));
        assertThat(catalog.list(ANY_ENTITY, filteredBy(THIRD_ID, "second", 2, 10)))
                .isEqualTo(new PageResult<>(List.of(), 2, 10, 0, 0));
    }

    @Test
    @DisplayName("Filtered by identifier and name, the source is asked for that element only: it is not asked to search, nor for the list")
    void filterByIdAndNameAsksTheSourceForThatElementOnly() {
        FakeSource source = new FakeSource(new Listing.Complete<>(SIX_ITEMS));
        CatalogService catalog = new CatalogService(source);
        CatalogEntity people = new CatalogEntity("people", "name");

        catalog.list(people, filteredBy(THIRD_ID, "third", 1, 10));
        catalog.list(people, filteredBy(THIRD_ID, "another", 1, 10));

        assertThat(source.asked).containsExactly(people, THIRD_ID, people, THIRD_ID);
    }

    @Test
    @DisplayName("The detail of an element is the element of the source, as the type of its entity")
    void detailIsTheElementOfTheSource() {
        FakeSource source = new FakeSource(new Listing.Complete<>(SIX_ITEMS));
        CatalogService catalog = new CatalogService(source);
        CatalogEntity people = new CatalogEntity("people", "name");

        FakeItem detail = catalog.detail(people, THIRD_ID, FakeItem.class);

        assertThat(detail).isSameAs(THIRD);
        assertThat(source.asked).containsExactly(people, THIRD_ID);
    }

    @Test
    @DisplayName("The detail of an element that the source does not have is not found")
    void detailOfAMissingElementIsNotFound() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete<>(SIX_ITEMS)));

        assertThatThrownBy(() -> catalog.detail(ANY_ENTITY, MISSING_ID, FakeItem.class))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("With the complete data, when the source answers with a page and its total, that page is used, with the elements as they came")
    void expandedPageOfTheSourceIsUsed() {
        List<CatalogItem> secondPage = SIX_COMPLETE_ITEMS.subList(2, 4);
        CatalogService catalog = new CatalogService(FakeSource.expandedInto(new Listing.OnePage<>(secondPage, 6)));

        PageResult<CatalogItem> page = catalog.list(ANY_ENTITY, expanded(unfiltered(2, 2)));

        // The same totals as with the summaries. The elements are the ones of the source, not a
        // copy and not their summaries.
        assertThat(page).isEqualTo(new PageResult<>(secondPage, 2, 2, 6, 3));
        assertThat(page.content().getFirst()).isSameAs(THIRD);
    }

    @Test
    @DisplayName("With the complete data, when the source answers with the whole list, the page is cut out here")
    void expandedWholeListIsCutHere() {
        CatalogService catalog = new CatalogService(FakeSource.expandedInto(new Listing.Complete<>(SIX_COMPLETE_ITEMS)));

        PageResult<CatalogItem> page = catalog.list(ANY_ENTITY, expanded(unfiltered(2, 2)));

        assertThat(page).isEqualTo(new PageResult<>(SIX_COMPLETE_ITEMS.subList(2, 4), 2, 2, 6, 3));
    }

    @Test
    @DisplayName("With the complete data, when the source repeats its last page for a page after the last one, the page is empty")
    void expandedPageAfterTheLastOneIsEmpty() {
        List<CatalogItem> lastPage = SIX_COMPLETE_ITEMS.subList(4, 6);
        CatalogService catalog = new CatalogService(FakeSource.expandedInto(new Listing.OnePage<>(lastPage, 6)));

        PageResult<CatalogItem> page = catalog.list(ANY_ENTITY, expanded(unfiltered(50, 2)));

        assertThat(page).isEqualTo(new PageResult<>(List.of(), 50, 2, 6, 3));
    }

    @Test
    @DisplayName("With the complete data, the source is asked for the page with the complete elements, and never for the page of summaries")
    void expandedListAsksTheSourceForTheCompleteElements() {
        FakeSource source = FakeSource.expandedInto(new Listing.Complete<>(List.of()));
        CatalogService catalog = new CatalogService(source);
        CatalogEntity people = new CatalogEntity("people", "name");

        catalog.list(people, expanded(unfiltered(9, 10)));

        assertThat(source.asked).containsExactly(FakeSource.EXPANDED, people, new PageQuery(9, 10));
    }

    @Test
    @DisplayName("With the complete data and filtered by name, the source is asked to search with the complete elements, and its matches are cut here")
    void expandedFilterByNameAsksTheSourceToSearchWithTheCompleteElements() {
        List<CatalogItem> threeMatches = SIX_COMPLETE_ITEMS.subList(0, 3);
        FakeSource source = FakeSource.expandedInto(new Listing.Complete<>(threeMatches));
        CatalogService catalog = new CatalogService(source);
        CatalogEntity people = new CatalogEntity("people", "name");

        PageResult<CatalogItem> page = catalog.list(people, expanded(filteredBy("sky", 2, 2)));

        // The matches are the ones the source found: none of these names contains "sky".
        assertThat(page).isEqualTo(new PageResult<>(SIX_COMPLETE_ITEMS.subList(2, 3), 2, 2, 3, 2));
        assertThat(source.asked)
                .containsExactly(FakeSource.EXPANDED, people, new NameFilter("sky"), new PageQuery(2, 2));
    }

    @Test
    @DisplayName("With the complete data and filtered by an identifier, the list is a page with that element itself, not its summary, out of a total of 1")
    void expandedListFilteredByIdHasThatElementItself() {
        FakeSource source = new FakeSource(new Listing.Complete<>(SIX_ITEMS));
        CatalogService catalog = new CatalogService(source);
        CatalogEntity people = new CatalogEntity("people", "name");

        PageResult<CatalogItem> page = catalog.list(people, expanded(filteredBy(THIRD_ID, 1, 10)));

        assertThat(page).isEqualTo(new PageResult<>(List.of(THIRD), 1, 10, 1, 1));
        assertThat(page.content().getFirst()).isSameAs(THIRD);
        // The same request as without the complete data: the element, and neither list.
        assertThat(source.asked).containsExactly(people, THIRD_ID);
    }

    @Test
    @DisplayName("With the complete data, a filter by an identifier that the source does not have, or on a page after the first one, is an empty page")
    void expandedListFilteredByIdIsEmptyLikeTheOneOfSummaries() {
        CatalogService catalog = new CatalogService(new FakeSource(new Listing.Complete<>(SIX_ITEMS)));

        assertThat(catalog.list(ANY_ENTITY, expanded(filteredBy(MISSING_ID, 1, 10))))
                .isEqualTo(new PageResult<>(List.of(), 1, 10, 0, 0));
        // The totals still count the element.
        assertThat(catalog.list(ANY_ENTITY, expanded(filteredBy(THIRD_ID, 2, 10))))
                .isEqualTo(new PageResult<>(List.of(), 2, 10, 1, 1));
    }

    @Test
    @DisplayName("With the complete data and filtered by identifier and name, the list has the element itself only when its name contains the text")
    void expandedListFilteredByIdAndNameComparesTheNameOfTheElement() {
        FakeSource source = new FakeSource(new Listing.Complete<>(SIX_ITEMS));
        CatalogService catalog = new CatalogService(source);

        assertThat(catalog.list(ANY_ENTITY, expanded(filteredBy(THIRD_ID, "HIR", 1, 10))))
                .isEqualTo(new PageResult<>(List.of(THIRD), 1, 10, 1, 1));
        assertThat(catalog.list(ANY_ENTITY, expanded(filteredBy(THIRD_ID, "second", 1, 10))))
                .isEqualTo(new PageResult<>(List.of(), 1, 10, 0, 0));
        // The text is only compared with the name: this one is in another value of the element.
        assertThat(catalog.list(ANY_ENTITY, expanded(filteredBy(THIRD_ID, "detail", 1, 10))))
                .isEqualTo(new PageResult<>(List.of(), 1, 10, 0, 0));
        // The element was asked for each time, and nothing else.
        assertThat(source.asked).containsExactly(ANY_ENTITY, THIRD_ID, ANY_ENTITY, THIRD_ID, ANY_ENTITY, THIRD_ID);
    }

    /** The same query, asking for the complete data of each element. */
    private static ListQuery expanded(ListQuery query) {
        return new ListQuery(query.page(), query.id(), query.name(), true);
    }

    private static ListQuery unfiltered(int page, int size) {
        return new ListQuery(new PageQuery(page, size), null, null, false);
    }

    private static ListQuery filteredBy(ItemId id, int page, int size) {
        return new ListQuery(new PageQuery(page, size), id, null, false);
    }

    private static ListQuery filteredBy(String name, int page, int size) {
        return new ListQuery(new PageQuery(page, size), null, new NameFilter(name), false);
    }

    private static ListQuery filteredBy(ItemId id, String name, int page, int size) {
        return new ListQuery(new PageQuery(page, size), id, new NameFilter(name), false);
    }

    /**
     * The fake source. It answers every request for a list, and every search, with the same
     * listing, has a single element, and notes down what it is asked. Asked for the complete
     * data, it answers with its other listing, and notes that down first.
     */
    private static final class FakeSource implements CatalogSource {

        /** What is noted before a request for the elements with their complete data. */
        private static final String EXPANDED = "expanded";

        private final Listing<ItemSummary> listing;

        private final Listing<CatalogItem> expandedListing;

        private final List<Object> asked = new ArrayList<>();

        private FakeSource(Listing<ItemSummary> listing) {
            this(listing, new Listing.Complete<>(List.of()));
        }

        private FakeSource(Listing<ItemSummary> listing, Listing<CatalogItem> expandedListing) {
            this.listing = listing;
            this.expandedListing = expandedListing;
        }

        /** A source that is only asked for the complete data: its summaries are another list. */
        private static FakeSource expandedInto(Listing<CatalogItem> expandedListing) {
            return new FakeSource(new Listing.Complete<>(SIX_ITEMS), expandedListing);
        }

        @Override
        public Listing<ItemSummary> list(CatalogEntity entity, PageQuery query) {
            asked.add(entity);
            asked.add(query);
            return listing;
        }

        @Override
        public Listing<ItemSummary> search(CatalogEntity entity, NameFilter name, PageQuery query) {
            asked.add(entity);
            asked.add(name);
            asked.add(query);
            return listing;
        }

        @Override
        public Listing<CatalogItem> listExpanded(CatalogEntity entity, PageQuery query) {
            asked.add(EXPANDED);
            asked.add(entity);
            asked.add(query);
            return expandedListing;
        }

        @Override
        public Listing<CatalogItem> searchExpanded(CatalogEntity entity, NameFilter name, PageQuery query) {
            asked.add(EXPANDED);
            asked.add(entity);
            asked.add(name);
            asked.add(query);
            return expandedListing;
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
