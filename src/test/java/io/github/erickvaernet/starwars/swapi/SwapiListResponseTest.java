package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.Listing;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import io.github.erickvaernet.starwars.swapi.SwapiListResponse.Item;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the translation of a list of SWAPI into the model of the catalog.
 *
 * <p>The answers are built here, so the test can include what the recordings do not have: texts
 * with spaces around them and answers with missing data.
 */
class SwapiListResponseTest {

    @Test
    @DisplayName("A page of SWAPI becomes a page with its elements, in the same order, and the total")
    void pageOfSwapiBecomesAPageWithItsTotal() {
        SwapiListResponse response = new SwapiListResponse(82, List.of(
                new Item("82", "Sly Moore"),
                new Item("83", "Tion Medon")));

        assertThat(response.toListing()).isEqualTo(new Listing.OnePage(
                List.of(new ItemSummary("82", "Sly Moore"), new ItemSummary("83", "Tion Medon")), 82));
    }

    @Test
    @DisplayName("The identifier stays as text, the way SWAPI gives it")
    void identifierStaysAsText() {
        SwapiListResponse response = new SwapiListResponse(1, List.of(new Item("1", "Luke Skywalker")));

        Listing.OnePage page = (Listing.OnePage) response.toListing();

        assertThat(page.items().getFirst().id()).isEqualTo("1");
    }

    @Test
    @DisplayName("The texts lose the spaces at their start and at their end, and nothing else")
    void textsLoseTheSpacesAroundThem() {
        SwapiListResponse response = new SwapiListResponse(1, List.of(new Item(" 7 ", "  Beru Whitesun lars \t")));

        Listing.OnePage page = (Listing.OnePage) response.toListing();

        assertThat(page.items()).containsExactly(new ItemSummary("7", "Beru Whitesun lars"));
    }

    @Test
    @DisplayName("A page without elements is a valid answer")
    void pageWithoutElementsIsValid() {
        assertThat(new SwapiListResponse(0, List.of()).toListing()).isEqualTo(new Listing.OnePage(List.of(), 0));
    }

    @Test
    @DisplayName("An answer without the total of records cannot be read")
    void answerWithoutTheTotalCannotBeRead() {
        SwapiListResponse response = new SwapiListResponse(null, List.of(new Item("1", "Luke Skywalker")));

        assertThatThrownBy(response::toListing).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An answer with a negative total of records cannot be read")
    void answerWithANegativeTotalCannotBeRead() {
        assertThatThrownBy(() -> new SwapiListResponse(-1, List.of()).toListing())
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An answer without the list of results cannot be read")
    void answerWithoutResultsCannotBeRead() {
        assertThatThrownBy(() -> new SwapiListResponse(82, null).toListing())
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An element without identifier or without name cannot be read: it is not left out in silence")
    void elementWithoutIdentifierOrNameCannotBeRead() {
        List<Item> withANullElement = new ArrayList<>();
        withANullElement.add(null);

        assertThatThrownBy(() -> new SwapiListResponse(1, List.of(new Item(null, "Luke Skywalker"))).toListing())
                .isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(() -> new SwapiListResponse(1, List.of(new Item("1", null))).toListing())
                .isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(() -> new SwapiListResponse(1, List.of(new Item("  ", "Luke Skywalker"))).toListing())
                .isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(() -> new SwapiListResponse(1, withANullElement).toListing())
                .isInstanceOf(BadGatewayException.class);
    }

}
