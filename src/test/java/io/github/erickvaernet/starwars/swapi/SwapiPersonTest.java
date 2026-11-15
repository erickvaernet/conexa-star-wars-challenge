package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.people.PersonDetail;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the translation of a character of SWAPI into the detail of the catalog.
 *
 * <p>The data is built here, so the test can include what the recording of character 1 does not
 * have: texts with spaces around them, and data that SWAPI did not send.
 */
class SwapiPersonTest {

    private static final List<String> FILMS = List.of(
            "https://www.swapi.tech/api/films/1",
            "https://www.swapi.tech/api/films/2",
            "https://www.swapi.tech/api/films/3",
            "https://www.swapi.tech/api/films/6");

    private static final List<String> STARSHIPS = List.of(
            "https://www.swapi.tech/api/starships/12",
            "https://www.swapi.tech/api/starships/22");

    private static final List<String> VEHICLES = List.of(
            "https://www.swapi.tech/api/vehicles/14",
            "https://www.swapi.tech/api/vehicles/30");

    /** Character 1, with the values of the real answer of SWAPI. */
    private static final SwapiPerson LUKE = new SwapiPerson(
            "Luke Skywalker", "19BBY", "male", "172", "77", "blond", "fair", "blue", FILMS, STARSHIPS, VEHICLES);

    @Test
    @DisplayName("Every value goes to its field as text, and every relation becomes a list of identifiers")
    void everyValueGoesToItsField() {
        assertThat(LUKE.toItem("1")).isEqualTo(new PersonDetail(
                "1", "Luke Skywalker", "19BBY", "male", "172", "77", "blond", "fair", "blue",
                List.of("1", "2", "3", "6"), List.of("12", "22"), List.of("14", "30")));
    }

    @Test
    @DisplayName("In a list, the character is its identifier and its name")
    void summaryIsTheIdentifierAndTheName() {
        assertThat(LUKE.toItem("1").summary()).isEqualTo(new ItemSummary("1", "Luke Skywalker"));
    }

    @Test
    @DisplayName("The texts lose the spaces at their start and at their end, and nothing else")
    void textsLoseTheSpacesAroundThem() {
        SwapiPerson person = new SwapiPerson(
                "  Beru Whitesun lars ", " 47BBY", "female\t", " 165 ", "75 ", " brown", "light ", " blue ",
                List.of(" https://www.swapi.tech/api/films/1 "), List.of(), List.of());

        assertThat(person.toItem("7")).isEqualTo(new PersonDetail(
                "7", "Beru Whitesun lars", "47BBY", "female", "165", "75", "brown", "light", "blue",
                List.of("1"), List.of(), List.of()));
    }

    @Test
    @DisplayName("The values are not interpreted: unknown, n/a and a number with a comma stay as SWAPI wrote them")
    void valuesAreNotInterpreted() {
        SwapiPerson person = new SwapiPerson(
                "Jabba Desilijic Tiure", "600BBY", "hermaphrodite", "unknown", "1,358", "n/a", "green-tan, brown",
                "orange", List.of(), List.of(), List.of());

        PersonDetail detail = person.toItem("16");

        assertThat(detail.height()).isEqualTo("unknown");
        assertThat(detail.mass()).isEqualTo("1,358");
        assertThat(detail.hairColor()).isEqualTo("n/a");
        assertThat(detail.skinColor()).isEqualTo("green-tan, brown");
    }

    @Test
    @DisplayName("A text that SWAPI did not send is null, and the rest of the character is still answered")
    void textThatWasNotSentIsNull() {
        SwapiPerson person = new SwapiPerson(
                "Luke Skywalker", null, null, null, null, null, null, null, FILMS, STARSHIPS, VEHICLES);

        assertThat(person.toItem("1")).isEqualTo(new PersonDetail(
                "1", "Luke Skywalker", null, null, null, null, null, null, null,
                List.of("1", "2", "3", "6"), List.of("12", "22"), List.of("14", "30")));
    }

    @Test
    @DisplayName("A relation that SWAPI did not send is an empty list")
    void relationThatWasNotSentIsAnEmptyList() {
        SwapiPerson person = new SwapiPerson(
                "Luke Skywalker", "19BBY", "male", "172", "77", "blond", "fair", "blue", null, null, null);

        PersonDetail detail = person.toItem("1");

        assertThat(detail.filmIds()).isEmpty();
        assertThat(detail.starshipIds()).isEmpty();
        assertThat(detail.vehicleIds()).isEmpty();
    }

    @Test
    @DisplayName("An empty text is answered as it is: it is not the same as a text that was not sent")
    void emptyTextStaysEmpty() {
        SwapiPerson person = new SwapiPerson(
                "Luke Skywalker", "", "  ", "172", "77", "blond", "fair", "blue", FILMS, STARSHIPS, VEHICLES);

        PersonDetail detail = person.toItem("1");

        assertThat(detail.birthYear()).isEmpty();
        assertThat(detail.gender()).isEmpty();
    }

    @ParameterizedTest(name = "name: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "  "})
    @DisplayName("A character without a name is a failure of the data source: the name is not optional")
    void characterWithoutANameIsAFailure(String name) {
        SwapiPerson person = new SwapiPerson(
                name, "19BBY", "male", "172", "77", "blond", "fair", "blue", FILMS, STARSHIPS, VEHICLES);

        assertThatThrownBy(() -> person.toItem("1")).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("A relation that did arrive, with an address without its identifier, is a failure: it is not left out")
    void relationWithABadAddressIsAFailure() {
        List<String> films = List.of(
                "https://www.swapi.tech/api/films/1",
                "https://www.swapi.tech/api/films/",
                "https://www.swapi.tech/api/films/3");
        SwapiPerson person = new SwapiPerson(
                "Luke Skywalker", "19BBY", "male", "172", "77", "blond", "fair", "blue", films, STARSHIPS, VEHICLES);

        assertThatThrownBy(() -> person.toItem("1")).isInstanceOf(BadGatewayException.class);
    }

}
