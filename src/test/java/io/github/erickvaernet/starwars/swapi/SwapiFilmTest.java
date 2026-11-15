package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.films.FilmDetail;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the translation of a film of SWAPI into the detail of the catalog.
 *
 * <p>The data is built here, so the test can include what the recording of film 1 does not have:
 * texts with spaces around them, data that SWAPI did not send, and an episode that is not a whole
 * number.
 */
class SwapiFilmTest {

    private static final String OPENING_CRAWL = "It is a period of civil war.\r\nRebel spaceships, striking\r\n"
            + "from a hidden base, have won\r\ntheir first victory against\r\nthe evil Galactic Empire.";

    private static final List<String> CHARACTERS = List.of(
            "https://www.swapi.tech/api/people/1",
            "https://www.swapi.tech/api/people/2",
            "https://www.swapi.tech/api/people/81");

    private static final List<String> STARSHIPS = List.of(
            "https://www.swapi.tech/api/starships/2",
            "https://www.swapi.tech/api/starships/13");

    private static final List<String> VEHICLES = List.of(
            "https://www.swapi.tech/api/vehicles/4",
            "https://www.swapi.tech/api/vehicles/8");

    /** Film 1, with values of the real answer of SWAPI. Its lists are shorter here. */
    private static final SwapiFilm A_NEW_HOPE = film("A New Hope", 4);

    @Test
    @DisplayName("Every value goes to its field, the episode as a number, and every relation becomes a list of identifiers")
    void everyValueGoesToItsField() {
        assertThat(A_NEW_HOPE.toItem("1")).isEqualTo(new FilmDetail(
                "1", "A New Hope", 4, OPENING_CRAWL, "George Lucas", "Gary Kurtz, Rick McCallum", "1977-05-25",
                List.of("1", "2", "81"), List.of("2", "13"), List.of("4", "8")));
    }

    @Test
    @DisplayName("In a list, the film is its identifier and its title, as its name")
    void summaryIsTheIdentifierAndTheTitle() {
        assertThat(A_NEW_HOPE.toItem("1").summary()).isEqualTo(new ItemSummary("1", "A New Hope"));
    }

    @Test
    @DisplayName("The texts lose the spaces at their start and at their end, and nothing else: the line breaks inside stay")
    void textsLoseTheSpacesAroundThem() {
        SwapiFilm film = new SwapiFilm(
                "  The Empire Strikes Back ", 5, " \r\nIt is a dark time for the\r\nRebellion.\r\n\r\nEvading the dreaded...\r\n ",
                " Irvin Kershner", "Gary Kurtz, Rick McCallum\t", " 1980-05-17 ",
                List.of(" https://www.swapi.tech/api/people/1 "), List.of(), List.of());

        assertThat(film.toItem("2")).isEqualTo(new FilmDetail(
                "2", "The Empire Strikes Back", 5, "It is a dark time for the\r\nRebellion.\r\n\r\nEvading the dreaded...",
                "Irvin Kershner", "Gary Kurtz, Rick McCallum", "1980-05-17",
                List.of("1"), List.of(), List.of()));
    }

    @Test
    @DisplayName("A value that SWAPI did not send is null, the episode too, and the rest of the film is still answered")
    void valueThatWasNotSentIsNull() {
        SwapiFilm film = new SwapiFilm("A New Hope", null, null, null, null, null, CHARACTERS, STARSHIPS, VEHICLES);

        assertThat(film.toItem("1")).isEqualTo(new FilmDetail(
                "1", "A New Hope", null, null, null, null, null,
                List.of("1", "2", "81"), List.of("2", "13"), List.of("4", "8")));
    }

    @Test
    @DisplayName("A relation that SWAPI did not send is an empty list")
    void relationThatWasNotSentIsAnEmptyList() {
        SwapiFilm film = new SwapiFilm(
                "A New Hope", 4, OPENING_CRAWL, "George Lucas", "Gary Kurtz, Rick McCallum", "1977-05-25",
                null, null, null);

        FilmDetail detail = film.toItem("1");

        assertThat(detail.characterIds()).isEmpty();
        assertThat(detail.starshipIds()).isEmpty();
        assertThat(detail.vehicleIds()).isEmpty();
    }

    @Test
    @DisplayName("An empty text is answered as it is: it is not the same as a text that was not sent")
    void emptyTextStaysEmpty() {
        SwapiFilm film = new SwapiFilm(
                "A New Hope", 4, "", "  ", "Gary Kurtz, Rick McCallum", "1977-05-25", CHARACTERS, STARSHIPS, VEHICLES);

        FilmDetail detail = film.toItem("1");

        assertThat(detail.openingCrawl()).isEmpty();
        assertThat(detail.director()).isEmpty();
    }

    @ParameterizedTest(name = "title: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "  "})
    @DisplayName("A film without a title is a failure of the data source: the title is its name, and it is not optional")
    void filmWithoutATitleIsAFailure(String title) {
        assertThatThrownBy(() -> film(title, 4).toItem("1")).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("A relation that did arrive, with an address without its identifier, is a failure: it is not left out")
    void relationWithABadAddressIsAFailure() {
        List<String> characters = List.of(
                "https://www.swapi.tech/api/people/1",
                "https://www.swapi.tech/api/people/",
                "https://www.swapi.tech/api/people/3");
        SwapiFilm film = new SwapiFilm(
                "A New Hope", 4, OPENING_CRAWL, "George Lucas", "Gary Kurtz, Rick McCallum", "1977-05-25",
                characters, STARSHIPS, VEHICLES);

        assertThatThrownBy(() -> film.toItem("1")).isInstanceOf(BadGatewayException.class);
    }

    @ParameterizedTest(name = "episode_id: {0}")
    @ValueSource(ints = {4, 1, 0, -1})
    @DisplayName("An episode that is a whole number is answered as it is, without judging its value")
    void episodeThatIsAWholeNumberIsAnsweredAsItIs(int episode) {
        assertThat(film("A New Hope", episode).toItem("1").episodeId()).isEqualTo(episode);
    }

    static Stream<Arguments> episodesThatAreNotAWholeNumber() {
        return Stream.of(
                // A text, even one that looks like a number: reading it would be converting it.
                Arguments.of("a text with digits", "4"),
                Arguments.of("a text", "IV"),
                Arguments.of("an empty text", ""),
                // A number with decimals, even when they are zero. It is not cut down to 4.
                Arguments.of("a decimal", 4.5),
                Arguments.of("a decimal that ends in zero", 4.0),
                // A whole number too large to be the one the API promises.
                Arguments.of("a number that is too large", 3_000_000_000L),
                Arguments.of("a boolean", true),
                Arguments.of("a list", List.of(4)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("episodesThatAreNotAWholeNumber")
    @DisplayName("An episode that did arrive and is not a whole number is a failure: it is neither converted nor left out")
    void episodeThatIsNotAWholeNumberIsAFailure(String description, Object episode) {
        assertThatThrownBy(() -> film("A New Hope", episode).toItem("1"))
                .isInstanceOf(BadGatewayException.class)
                .hasMessageContaining("episode_id");
    }

    /** A film with that title and that episode, and the rest of the data of film 1. */
    private static SwapiFilm film(String title, Object episode) {
        return new SwapiFilm(
                title, episode, OPENING_CRAWL, "George Lucas", "Gary Kurtz, Rick McCallum", "1977-05-25",
                CHARACTERS, STARSHIPS, VEHICLES);
    }

}
