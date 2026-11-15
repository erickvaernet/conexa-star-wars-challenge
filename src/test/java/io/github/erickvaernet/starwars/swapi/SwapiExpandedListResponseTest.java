package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erickvaernet.starwars.catalog.CatalogItem;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.Listing;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import io.github.erickvaernet.starwars.swapi.SwapiItemResponse.Result;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the translation of a list of SWAPI that brings the complete data of each element, in the
 * two forms in which SWAPI answers one: a page with the total of records, or every element at
 * once.
 *
 * <p>The answers are built here, and the data of an element is a fake one, so no entity is
 * involved: what is checked is the part that is the same for all of them.
 */
class SwapiExpandedListResponseTest {

    @Test
    @DisplayName("A page of SWAPI becomes a page with its elements translated, in the same order, and the total")
    void pageOfSwapiBecomesAPageWithItsTotal() {
        SwapiExpandedListResponse<FakeData> response = new SwapiExpandedListResponse<>(82, List.of(
                new Result<>("82", new FakeData("Sly Moore")),
                new Result<>("83", new FakeData("Tion Medon"))), null);

        assertThat(response.toListing()).isEqualTo(new Listing.OnePage<CatalogItem>(
                List.of(new FakeItem("82", "Sly Moore"), new FakeItem("83", "Tion Medon")), 82));
    }

    @Test
    @DisplayName("A list that came whole becomes the complete list, with its elements translated in the same order")
    void wholeListOfSwapiBecomesTheCompleteList() {
        SwapiExpandedListResponse<FakeData> response = new SwapiExpandedListResponse<>(null, null, List.of(
                new Result<>("1", new FakeData("A New Hope")),
                new Result<>("2", new FakeData("The Empire Strikes Back"))));

        assertThat(response.toListing()).isEqualTo(new Listing.Complete<CatalogItem>(
                List.of(new FakeItem("1", "A New Hope"), new FakeItem("2", "The Empire Strikes Back"))));
    }

    @Test
    @DisplayName("The identifier of each element is given to the translation of its entity without the spaces around it")
    void identifierLosesTheSpacesAroundIt() {
        SwapiExpandedListResponse<FakeData> response = new SwapiExpandedListResponse<>(
                1, List.of(new Result<>(" 7 ", new FakeData("Beru"))), null);

        assertThat(response.toListing().items()).containsExactly(new FakeItem("7", "Beru"));
    }

    @Test
    @DisplayName("A page and a whole list without elements are valid answers")
    void answersWithoutElementsAreValid() {
        assertThat(new SwapiExpandedListResponse<FakeData>(0, List.of(), null).toListing())
                .isEqualTo(new Listing.OnePage<CatalogItem>(List.of(), 0));
        assertThat(new SwapiExpandedListResponse<FakeData>(null, null, List.of()).toListing())
                .isEqualTo(new Listing.Complete<CatalogItem>(List.of()));
    }

    @Test
    @DisplayName("When the page and its total came, it is a page, even if the whole list came as well")
    void pageWithItsTotalWinsOverTheWholeList() {
        SwapiExpandedListResponse<FakeData> response = new SwapiExpandedListResponse<>(
                5, List.of(new Result<>("1", new FakeData("in the page"))),
                List.of(new Result<>("2", new FakeData("in the whole list"))));

        assertThat(response.toListing()).isEqualTo(new Listing.OnePage<CatalogItem>(
                List.of(new FakeItem("1", "in the page")), 5));
    }

    @Test
    @DisplayName("An answer with neither of the two forms, or with a page without its total, cannot be read")
    void answerWithoutAFormCannotBeRead() {
        SwapiExpandedListResponse<FakeData> empty = new SwapiExpandedListResponse<>(null, null, null);
        SwapiExpandedListResponse<FakeData> withoutTotal = new SwapiExpandedListResponse<>(
                null, List.of(new Result<>("1", new FakeData("Luke"))), null);
        SwapiExpandedListResponse<FakeData> withoutPage = new SwapiExpandedListResponse<>(82, null, null);

        assertThatThrownBy(empty::toListing).isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(withoutTotal::toListing).isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(withoutPage::toListing).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("A negative total of records cannot be read")
    void negativeTotalCannotBeRead() {
        SwapiExpandedListResponse<FakeData> response = new SwapiExpandedListResponse<>(-1, List.of(), null);

        assertThatThrownBy(response::toListing).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An element without its data makes the whole list a failure, in the two forms: it is what a summary looks like")
    void elementWithoutDataIsAFailure() {
        // What SWAPI would send if it ignored expanded: the identifier, and no properties.
        List<Result<FakeData>> elements = List.of(
                new Result<>("1", new FakeData("Luke")), new Result<>("2", null));

        assertThatThrownBy(new SwapiExpandedListResponse<>(82, elements, null)::toListing)
                .isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(new SwapiExpandedListResponse<>(null, null, elements)::toListing)
                .isInstanceOf(BadGatewayException.class);
    }

    @ParameterizedTest(name = "uid: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "  "})
    @DisplayName("An element without an identifier makes the whole list a failure, in the two forms")
    void elementWithoutAnIdentifierIsAFailure(String uid) {
        List<Result<FakeData>> elements = List.of(
                new Result<>("1", new FakeData("Luke")), new Result<>(uid, new FakeData("nobody")));

        assertThatThrownBy(new SwapiExpandedListResponse<>(82, elements, null)::toListing)
                .isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(new SwapiExpandedListResponse<>(null, null, elements)::toListing)
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An element that is missing altogether makes the whole list a failure")
    void missingElementIsAFailure() {
        List<Result<FakeData>> elements = new ArrayList<>();
        elements.add(new Result<>("1", new FakeData("Luke")));
        elements.add(null);

        assertThatThrownBy(new SwapiExpandedListResponse<>(82, elements, null)::toListing)
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An element that its entity cannot translate makes the whole list a failure: it is not left out")
    void elementThatCannotBeTranslatedIsAFailure() {
        List<Result<FakeData>> elements = List.of(
                new Result<>("1", new FakeData("Luke")), new Result<>("2", new FakeData(null)));

        assertThatThrownBy(new SwapiExpandedListResponse<>(82, elements, null)::toListing)
                .isInstanceOf(BadGatewayException.class);
    }

    /**
     * The data of an element of an entity that exists only here. Like the real ones, it cannot
     * be translated without its name.
     */
    private record FakeData(String name) implements SwapiItemData {

        @Override
        public CatalogItem toItem(String id) {
            return new FakeItem(id, SwapiText.required(name, "name"));
        }

    }

    private record FakeItem(String id, String name) implements CatalogItem {

        @Override
        public ItemSummary summary() {
            return new ItemSummary(id, name);
        }

    }

}
