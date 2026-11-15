package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erickvaernet.starwars.shared.BadGatewayException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks how the identifier is taken out of an address of SWAPI, and that an address without one
 * is a failure of the data source and never a relation left out in silence.
 */
class SwapiAddressTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "https://www.swapi.tech/api/films/1, 1",
        "https://www.swapi.tech/api/starships/12, 12",
        "https://www.swapi.tech/api/vehicles/30, 30",
        "https://www.swapi.tech/api/people/83, 83",
        // Only the end of the address matters, not where SWAPI is.
        "http://localhost:8089/api/films/6, 6",
        "/api/films/1234567890, 1234567890"
    })
    @DisplayName("The identifier is the last segment of the address, as text")
    void identifierIsTheLastSegment(String address, String identifier) {
        assertThat(SwapiAddress.identifier(address)).isEqualTo(identifier);
    }

    @Test
    @DisplayName("The spaces around an address are ignored")
    void spacesAroundAnAddressAreIgnored() {
        assertThat(SwapiAddress.identifier("  https://www.swapi.tech/api/films/2 \t")).isEqualTo("2");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
        // Nothing after the last slash.
        "https://www.swapi.tech/api/films/",
        "https://www.swapi.tech/api/films/1/",
        // The last segment is not an identifier.
        "https://www.swapi.tech/api/films",
        "https://www.swapi.tech/api/films/abc",
        "https://www.swapi.tech/api/films/01",
        "https://www.swapi.tech/api/films/0",
        "https://www.swapi.tech/api/films/1?format=json",
        "https://www.swapi.tech/api/films/12345678901",
        // Not an address at all.
        "1",
        "films",
        "",
        " "
    })
    @DisplayName("An address that does not end in an identifier is a failure of the data source")
    void addressWithoutAnIdentifierIsAFailure(String address) {
        assertThatThrownBy(() -> SwapiAddress.identifier(address)).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("A missing address is a failure of the data source as well")
    void missingAddressIsAFailure() {
        assertThatThrownBy(() -> SwapiAddress.identifier(null)).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("A relation becomes the list of its identifiers, in the order of SWAPI")
    void relationBecomesTheListOfItsIdentifiers() {
        List<String> films = List.of(
                "https://www.swapi.tech/api/films/1",
                "https://www.swapi.tech/api/films/2",
                "https://www.swapi.tech/api/films/3",
                "https://www.swapi.tech/api/films/6");

        assertThat(SwapiAddress.identifiers(films)).containsExactly("1", "2", "3", "6");
    }

    @Test
    @DisplayName("A relation without elements is an empty list, and so is one that SWAPI did not send")
    void emptyOrMissingRelationIsAnEmptyList() {
        assertThat(SwapiAddress.identifiers(List.of())).isEmpty();
        assertThat(SwapiAddress.identifiers(null)).isEmpty();
    }

    @Test
    @DisplayName("One address without an identifier makes the whole relation fail: it is not left out")
    void oneBadAddressMakesTheWholeRelationFail() {
        List<String> films = List.of(
                "https://www.swapi.tech/api/films/1",
                "https://www.swapi.tech/api/films/",
                "https://www.swapi.tech/api/films/3");

        assertThatThrownBy(() -> SwapiAddress.identifiers(films)).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("A missing address inside a relation makes it fail too")
    void missingAddressInsideARelationMakesItFail() {
        List<String> films = new ArrayList<>(Arrays.asList("https://www.swapi.tech/api/films/1", null));

        assertThatThrownBy(() -> SwapiAddress.identifiers(films)).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("The failure keeps the address for the log")
    void failureKeepsTheAddressForTheLog() {
        assertThatThrownBy(() -> SwapiAddress.identifier("https://www.swapi.tech/api/films/"))
                .isInstanceOf(BadGatewayException.class)
                .hasMessageContaining("https://www.swapi.tech/api/films/");
    }

}
