package io.github.erickvaernet.starwars.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Checks how a page is put together in the two cases: when the source already answered with a
 * page and its totals, and when it answered with the whole list.
 */
class PageResultTest {

    // A page that the source already cut.

    @Test
    @DisplayName("A page of the source is used as it came, with its total")
    void pageOfTheSourceIsUsedAsItCame() {
        PageResult<String> page = PageResult.ofSourcePage(List.of("a", "b", "c"), 82, new PageQuery(2, 3));

        assertThat(page.content()).containsExactly("a", "b", "c");
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(3);
        assertThat(page.totalElements()).isEqualTo(82);
        assertThat(page.totalPages()).isEqualTo(28);
    }

    @ParameterizedTest(name = "{0} elements in pages of {1}: {2} pages")
    @CsvSource({
        "82, 10, 9",
        "80, 10, 8",
        "81, 10, 9",
        "1, 10, 1",
        "10, 10, 1",
        "11, 10, 2",
        "82, 1, 82",
        "82, 100, 1",
        "0, 10, 0"
    })
    @DisplayName("The total of pages is the total of elements divided by the size, rounded up")
    void totalOfPagesIsRoundedUp(int totalElements, int size, int totalPages) {
        PageResult<String> page = PageResult.ofSourcePage(List.of(), totalElements, new PageQuery(1, size));

        assertThat(page.totalPages()).isEqualTo(totalPages);
    }

    @Test
    @DisplayName("A page after the last one is empty, whatever the source sent, and keeps the totals")
    void pageAfterTheLastOneIsEmptyWhateverTheSourceSent() {
        // SWAPI answers a page after the last one by repeating its last page.
        PageResult<String> page = PageResult.ofSourcePage(List.of("y", "z"), 82, new PageQuery(50, 10));

        assertThat(page.content()).isEmpty();
        assertThat(page.page()).isEqualTo(50);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalElements()).isEqualTo(82);
        assertThat(page.totalPages()).isEqualTo(9);
    }

    @Test
    @DisplayName("The last page of the source is not taken for a page after the last one")
    void lastPageIsKept() {
        PageResult<String> page = PageResult.ofSourcePage(List.of("y", "z"), 82, new PageQuery(9, 10));

        assertThat(page.content()).containsExactly("y", "z");
    }

    @Test
    @DisplayName("With no elements at all, the first page is empty and there are 0 pages")
    void withNoElementsTheFirstPageIsEmpty() {
        PageResult<String> page = PageResult.ofSourcePage(List.of(), 0, new PageQuery(1, 10));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    // The whole list, cut here.

    @Test
    @DisplayName("From the whole list, the page asked for is cut out and the totals are counted")
    void pageIsCutOutOfTheWholeList() {
        List<String> films = List.of("1st", "2nd", "3rd", "4th", "5th", "6th");

        PageResult<String> page = PageResult.sliceOf(films, new PageQuery(2, 2));

        assertThat(page.content()).containsExactly("3rd", "4th");
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("The first page starts at the first element")
    void firstPageStartsAtTheFirstElement() {
        PageResult<String> page = PageResult.sliceOf(List.of("1st", "2nd", "3rd"), new PageQuery(1, 2));

        assertThat(page.content()).containsExactly("1st", "2nd");
    }

    @Test
    @DisplayName("The last page has the elements that are left")
    void lastPageHasTheElementsThatAreLeft() {
        PageResult<String> page = PageResult.sliceOf(List.of("1st", "2nd", "3rd", "4th", "5th"), new PageQuery(3, 2));

        assertThat(page.content()).containsExactly("5th");
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("A size larger than the list returns the whole list in one page")
    void sizeLargerThanTheListReturnsItAll() {
        List<String> people = IntStream.rangeClosed(1, 82).mapToObj(Integer::toString).toList();

        PageResult<String> page = PageResult.sliceOf(people, new PageQuery(1, 100));

        assertThat(page.content()).isEqualTo(people);
        assertThat(page.totalElements()).isEqualTo(82);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cutting a page after the last one gives an empty page with the right totals")
    void cuttingAPageAfterTheLastOneGivesAnEmptyPage() {
        PageResult<String> page = PageResult.sliceOf(List.of("1st", "2nd", "3rd"), new PageQuery(5, 2));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("Cutting the largest page number there is does not fail")
    void cuttingTheLargestPageNumberDoesNotFail() {
        PageResult<String> page = PageResult.sliceOf(List.of("1st", "2nd"), new PageQuery(Integer.MAX_VALUE, 100));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("An empty list gives an empty page, 0 elements and 0 pages")
    void emptyListGivesAnEmptyPage() {
        PageResult<String> page = PageResult.sliceOf(List.of(), new PageQuery(1, 10));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

}
