package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.Listing;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import io.github.erickvaernet.starwars.swapi.SwapiListResponse.FullItem;
import io.github.erickvaernet.starwars.swapi.SwapiListResponse.Item;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the translation of a list of SWAPI into the model of the catalog, in the two forms in
 * which SWAPI answers one: a page with the total of records, or every element at once.
 *
 * <p>The answers are built here, so the test can include what the recordings do not have: texts
 * with spaces around them and answers with missing data.
 */
class SwapiListResponseTest {

    /** The field that holds the name of an element in most entities of SWAPI. */
    private static final String NAME = "name";

    @Test
    @DisplayName("A page of SWAPI becomes a page with its elements, in the same order, and the total")
    void pageOfSwapiBecomesAPageWithItsTotal() {
        SwapiListResponse response = new SwapiListResponse(82, List.of(
                new Item("82", "Sly Moore"),
                new Item("83", "Tion Medon")), null);

        assertThat(response.toListing(NAME)).isEqualTo(new Listing.OnePage(
                List.of(new ItemSummary("82", "Sly Moore"), new ItemSummary("83", "Tion Medon")), 82));
    }

    @Test
    @DisplayName("The identifier stays as text, the way SWAPI gives it")
    void identifierStaysAsText() {
        SwapiListResponse response = new SwapiListResponse(1, List.of(new Item("1", "Luke Skywalker")), null);

        Listing.OnePage page = (Listing.OnePage) response.toListing(NAME);

        assertThat(page.items().getFirst().id()).isEqualTo("1");
    }

    @Test
    @DisplayName("The texts lose the spaces at their start and at their end, and nothing else")
    void textsLoseTheSpacesAroundThem() {
        SwapiListResponse response = new SwapiListResponse(1, List.of(new Item(" 7 ", "  Beru Whitesun lars \t")), null);

        Listing.OnePage page = (Listing.OnePage) response.toListing(NAME);

        assertThat(page.items()).containsExactly(new ItemSummary("7", "Beru Whitesun lars"));
    }

    @Test
    @DisplayName("A page without elements is a valid answer")
    void pageWithoutElementsIsValid() {
        assertThat(new SwapiListResponse(0, List.of(), null).toListing(NAME)).isEqualTo(new Listing.OnePage(List.of(), 0));
    }

    @Test
    @DisplayName("An answer without the total of records cannot be read")
    void answerWithoutTheTotalCannotBeRead() {
        SwapiListResponse response = new SwapiListResponse(null, List.of(new Item("1", "Luke Skywalker")), null);

        assertThatThrownBy(() -> response.toListing(NAME)).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An answer with a negative total of records cannot be read")
    void answerWithANegativeTotalCannotBeRead() {
        assertThatThrownBy(() -> new SwapiListResponse(-1, List.of(), null).toListing(NAME))
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An answer without the list of results cannot be read")
    void answerWithoutResultsCannotBeRead() {
        assertThatThrownBy(() -> new SwapiListResponse(82, null, null).toListing(NAME))
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An element without identifier or without name cannot be read: it is not left out in silence")
    void elementWithoutIdentifierOrNameCannotBeRead() {
        List<Item> withANullElement = new ArrayList<>();
        withANullElement.add(null);

        assertThatThrownBy(() -> new SwapiListResponse(1, List.of(new Item(null, "Luke Skywalker")), null).toListing(NAME))
                .isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(() -> new SwapiListResponse(1, List.of(new Item("1", null)), null).toListing(NAME))
                .isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(() -> new SwapiListResponse(1, List.of(new Item("  ", "Luke Skywalker")), null).toListing(NAME))
                .isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(() -> new SwapiListResponse(1, withANullElement, null).toListing(NAME))
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("A list that SWAPI answers whole becomes a complete list, in the same order, with the name taken from the data of each element")
    void wholeListOfSwapiBecomesACompleteList() {
        SwapiListResponse response = new SwapiListResponse(null, null, List.of(
                new FullItem("1", Map.of("name", "Luke Skywalker", "height", "172")),
                new FullItem("11", Map.of("name", "Anakin Skywalker")),
                new FullItem("43", Map.of("name", "Shmi Skywalker"))));

        assertThat(response.toListing(NAME)).isEqualTo(new Listing.Complete(List.of(
                new ItemSummary("1", "Luke Skywalker"),
                new ItemSummary("11", "Anakin Skywalker"),
                new ItemSummary("43", "Shmi Skywalker"))));
    }

    @Test
    @DisplayName("In a whole list, the name of an element is the field that its entity declares: for a film, its title")
    void nameOfAWholeListIsTheDeclaredField() {
        SwapiListResponse response = new SwapiListResponse(null, null, List.of(
                new FullItem("1", Map.of("title", "A New Hope", "name", "not this one", "episode_id", 4))));

        assertThat(response.toListing("title"))
                .isEqualTo(new Listing.Complete(List.of(new ItemSummary("1", "A New Hope"))));
    }

    @Test
    @DisplayName("In a whole list too, the texts lose the spaces around them and the identifier stays as text")
    void textsOfAWholeListLoseTheSpacesAroundThem() {
        SwapiListResponse response = new SwapiListResponse(null, null, List.of(
                new FullItem(" 7 ", Map.of("name", "  Beru Whitesun lars \t"))));

        assertThat(response.toListing(NAME))
                .isEqualTo(new Listing.Complete(List.of(new ItemSummary("7", "Beru Whitesun lars"))));
    }

    @Test
    @DisplayName("A whole list without elements is a valid answer: it is what a search without matches gets")
    void wholeListWithoutElementsIsValid() {
        assertThat(new SwapiListResponse(null, null, List.of()).toListing(NAME))
                .isEqualTo(new Listing.Complete(List.of()));
    }

    @Test
    @DisplayName("An answer with the total and the page is read as a page, even if it also brings the other list")
    void answerWithTheTotalIsReadAsAPage() {
        SwapiListResponse response = new SwapiListResponse(
                82, List.of(new Item("1", "Luke Skywalker")), List.of(new FullItem("2", Map.of("name", "C-3PO"))));

        assertThat(response.toListing(NAME))
                .isEqualTo(new Listing.OnePage(List.of(new ItemSummary("1", "Luke Skywalker")), 82));
    }

    @Test
    @DisplayName("An answer in neither of the two forms cannot be read")
    void answerInNeitherFormCannotBeRead() {
        assertThatThrownBy(() -> new SwapiListResponse(null, null, null).toListing(NAME))
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An element of a whole list without identifier or without name cannot be read: it is not left out in silence")
    void elementOfAWholeListWithoutIdentifierOrNameCannotBeRead() {
        List<FullItem> withANullElement = new ArrayList<>();
        withANullElement.add(null);
        Map<String, Object> withANullName = new HashMap<>();
        withANullName.put("name", null);

        assertWholeListCannotBeRead(List.of(new FullItem(null, Map.of("name", "Luke Skywalker"))));
        assertWholeListCannotBeRead(List.of(new FullItem("  ", Map.of("name", "Luke Skywalker"))));
        assertWholeListCannotBeRead(List.of(new FullItem("1", null)));
        assertWholeListCannotBeRead(List.of(new FullItem("1", Map.of())));
        assertWholeListCannotBeRead(List.of(new FullItem("1", withANullName)));
        assertWholeListCannotBeRead(List.of(new FullItem("1", Map.of("name", "   "))));
        // The name is there, but it is not a text.
        assertWholeListCannotBeRead(List.of(new FullItem("1", Map.of("name", 42))));
        assertWholeListCannotBeRead(List.of(new FullItem("1", Map.of("name", List.of("Luke Skywalker")))));
        // The name is in another field than the one its entity declares.
        assertWholeListCannotBeRead(List.of(new FullItem("1", Map.of("title", "A New Hope"))));
        assertWholeListCannotBeRead(withANullElement);
        // One element that cannot be read spoils the whole list.
        assertWholeListCannotBeRead(List.of(
                new FullItem("1", Map.of("name", "Luke Skywalker")), new FullItem("2", Map.of())));
    }

    private static void assertWholeListCannotBeRead(List<FullItem> result) {
        assertThatThrownBy(() -> new SwapiListResponse(null, null, result).toListing(NAME))
                .isInstanceOf(BadGatewayException.class);
    }

}
