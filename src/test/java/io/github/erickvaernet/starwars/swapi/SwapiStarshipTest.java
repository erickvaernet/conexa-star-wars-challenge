package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.starships.StarshipDetail;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the translation of a starship of SWAPI into the detail of the catalog.
 *
 * <p>The data is built here, so the test can include what the recording of starship 9 does not
 * have: pilots, texts with spaces around them, and data that SWAPI did not send.
 */
class SwapiStarshipTest {

    private static final List<String> FILMS = List.of(
            "https://www.swapi.tech/api/films/1",
            "https://www.swapi.tech/api/films/2",
            "https://www.swapi.tech/api/films/3");

    private static final List<String> PILOTS = List.of(
            "https://www.swapi.tech/api/people/1",
            "https://www.swapi.tech/api/people/9",
            "https://www.swapi.tech/api/people/18");

    /**
     * A starship with a different value in every field, so that two values that go to each
     * other's field show. Starship 9 of the recording does not serve for that: its cost and its
     * cargo capacity are the same text.
     */
    private static final SwapiStarship X_WING = new SwapiStarship(
            "X-wing", "T-65 X-wing", "Starfighter", "Incom Corporation", "149999", "12.5", "1", "0",
            "1050", "1.0", "100", "110", "1 week", FILMS, PILOTS);

    @Test
    @DisplayName("Every value goes to its field as text, and every relation becomes a list of identifiers")
    void everyValueGoesToItsField() {
        assertThat(X_WING.toItem("12")).isEqualTo(new StarshipDetail(
                "12", "X-wing", "T-65 X-wing", "Starfighter", "Incom Corporation", "149999", "12.5", "1", "0",
                "1050", "1.0", "100", "110", "1 week", List.of("1", "2", "3"), List.of("1", "9", "18")));
    }

    @Test
    @DisplayName("In a list, the starship is its identifier and its name")
    void summaryIsTheIdentifierAndTheName() {
        assertThat(X_WING.toItem("12").summary()).isEqualTo(new ItemSummary("12", "X-wing"));
    }

    @Test
    @DisplayName("A value that is not a plain number is answered as SWAPI sends it: it is not converted")
    void valuesAreNotConverted() {
        // The values of starship 9 in the real answer of SWAPI: a comma for the thousands,
        // decimals that are zero, a quantity with its unit and "it does not apply".
        SwapiStarship deathStar = new SwapiStarship(
                "Death Star", "DS-1 Orbital Battle Station", "Deep Space Mobile Battlestation",
                "Imperial Department of Military Research, Sienar Fleet Systems", "1000000000000", "120000",
                "342,953", "843,342", "n/a", "4.0", "10", "1000000000000", "3 years",
                List.of("https://www.swapi.tech/api/films/1"), List.of());

        StarshipDetail detail = deathStar.toItem("9");

        assertThat(detail.crew()).isEqualTo("342,953");
        assertThat(detail.passengers()).isEqualTo("843,342");
        assertThat(detail.maxAtmospheringSpeed()).isEqualTo("n/a");
        assertThat(detail.hyperdriveRating()).isEqualTo("4.0");
        assertThat(detail.consumables()).isEqualTo("3 years");
        assertThat(detail.pilotIds()).isEmpty();
    }

    @Test
    @DisplayName("The texts lose the spaces at their start and at their end, and nothing else")
    void textsLoseTheSpacesAroundThem() {
        SwapiStarship starship = new SwapiStarship(
                "  Millennium Falcon ", " YT-1300 light freighter", "Light freighter\t",
                " Corellian Engineering Corporation ", "100000 ", " 34.37", " 4 ", "6\n", " 1050 ", " 0.5", "75 ",
                " 100000 ", " 2 months ",
                List.of(" https://www.swapi.tech/api/films/1 "), List.of("https://www.swapi.tech/api/people/13 "));

        assertThat(starship.toItem("10")).isEqualTo(new StarshipDetail(
                "10", "Millennium Falcon", "YT-1300 light freighter", "Light freighter",
                "Corellian Engineering Corporation", "100000", "34.37", "4", "6", "1050", "0.5", "75", "100000",
                "2 months", List.of("1"), List.of("13")));
    }

    @Test
    @DisplayName("A text that SWAPI did not send is null, and the rest of the starship is still answered")
    void textThatWasNotSentIsNull() {
        SwapiStarship starship = new SwapiStarship(
                "X-wing", null, null, null, null, null, null, null, null, null, null, null, null, FILMS, PILOTS);

        assertThat(starship.toItem("12")).isEqualTo(new StarshipDetail(
                "12", "X-wing", null, null, null, null, null, null, null, null, null, null, null, null,
                List.of("1", "2", "3"), List.of("1", "9", "18")));
    }

    @Test
    @DisplayName("A relation that SWAPI did not send is an empty list")
    void relationThatWasNotSentIsAnEmptyList() {
        StarshipDetail detail = starship("X-wing", null, null).toItem("12");

        assertThat(detail.filmIds()).isEmpty();
        assertThat(detail.pilotIds()).isEmpty();
    }

    @Test
    @DisplayName("An empty text is answered as it is: it is not the same as a text that was not sent")
    void emptyTextStaysEmpty() {
        SwapiStarship starship = new SwapiStarship(
                "X-wing", "", "  ", "Incom Corporation", "149999", "12.5", "1", "0", "1050", "1.0", "100", "110",
                "1 week", FILMS, PILOTS);

        StarshipDetail detail = starship.toItem("12");

        assertThat(detail.model()).isEmpty();
        assertThat(detail.starshipClass()).isEmpty();
    }

    @ParameterizedTest(name = "name: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "  "})
    @DisplayName("A starship without a name is a failure of the data source: the name is not optional")
    void starshipWithoutANameIsAFailure(String name) {
        assertThatThrownBy(() -> starship(name, FILMS, PILOTS).toItem("12")).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("A relation that did arrive, with an address without its identifier, is a failure: it is not left out")
    void relationWithABadAddressIsAFailure() {
        List<String> badFilms = List.of(
                "https://www.swapi.tech/api/films/1",
                "https://www.swapi.tech/api/films/",
                "https://www.swapi.tech/api/films/3");
        List<String> badPilots = List.of(
                "https://www.swapi.tech/api/people/1",
                "https://www.swapi.tech/api/people");

        assertThatThrownBy(() -> starship("X-wing", badFilms, PILOTS).toItem("12"))
                .isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(() -> starship("X-wing", FILMS, badPilots).toItem("12"))
                .isInstanceOf(BadGatewayException.class);
    }

    /** A starship with that name and those relations, and the rest of the data of {@link #X_WING}. */
    private static SwapiStarship starship(String name, List<String> films, List<String> pilots) {
        return new SwapiStarship(
                name, "T-65 X-wing", "Starfighter", "Incom Corporation", "149999", "12.5", "1", "0",
                "1050", "1.0", "100", "110", "1 week", films, pilots);
    }

}
