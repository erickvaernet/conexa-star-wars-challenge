package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erickvaernet.starwars.catalog.CatalogItem;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import io.github.erickvaernet.starwars.swapi.SwapiItemResponse.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the part of the answer for one element that is the same for every entity: the
 * identifier and the data go to the translation of the entity, and an answer without them is a
 * failure of the data source.
 *
 * <p>The data of the element is a fake one written here, so no entity is involved.
 */
class SwapiItemResponseTest {

    /** Data of an element that translates into an element named after the identifier it gets. */
    private static final SwapiItemData DATA = id -> () -> new ItemSummary(id, "element " + id);

    @Test
    @DisplayName("The identifier of the answer is given to the translation of the entity")
    void identifierIsGivenToTheTranslation() {
        CatalogItem item = new SwapiItemResponse<>(new Result<>("14", DATA)).toItem();

        assertThat(item.summary()).isEqualTo(new ItemSummary("14", "element 14"));
    }

    @Test
    @DisplayName("The identifier loses the spaces around it and stays as text")
    void identifierLosesTheSpacesAroundIt() {
        CatalogItem item = new SwapiItemResponse<>(new Result<>(" 7 ", DATA)).toItem();

        assertThat(item.summary().id()).isEqualTo("7");
    }

    @ParameterizedTest(name = "uid: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "  "})
    @DisplayName("An answer without an identifier is a failure of the data source")
    void answerWithoutAnIdentifierIsAFailure(String uid) {
        SwapiItemResponse<SwapiItemData> response = new SwapiItemResponse<>(new Result<>(uid, DATA));

        assertThatThrownBy(response::toItem).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An answer without the data of the element, or without a result at all, is a failure of the data source")
    void answerWithoutDataIsAFailure() {
        SwapiItemResponse<SwapiItemData> withoutData = new SwapiItemResponse<>(new Result<>("1", null));
        SwapiItemResponse<SwapiItemData> withoutResult = new SwapiItemResponse<>(null);

        assertThatThrownBy(withoutData::toItem).isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(withoutResult::toItem).isInstanceOf(BadGatewayException.class);
    }

}
