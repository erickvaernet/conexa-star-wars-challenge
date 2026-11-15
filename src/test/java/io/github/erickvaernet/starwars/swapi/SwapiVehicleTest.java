package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.vehicles.VehicleDetail;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the translation of a vehicle of SWAPI into the detail of the catalog.
 *
 * <p>The data is built here, so the test can include what the recording of vehicle 4 does not
 * have: pilots, a different value in every field, and data that SWAPI did not send.
 */
class SwapiVehicleTest {

    private static final List<String> FILMS = List.of(
            "https://www.swapi.tech/api/films/1",
            "https://www.swapi.tech/api/films/2",
            "https://www.swapi.tech/api/films/3");

    private static final List<String> PILOTS = List.of(
            "https://www.swapi.tech/api/people/1",
            "https://www.swapi.tech/api/people/9",
            "https://www.swapi.tech/api/people/18");

    /**
     * A vehicle with a different value in every field, so that two values that go to each
     * other's field show. Vehicle 4 of the recording does not serve for that: its passengers and
     * its maximum speed are the same text, and it has no pilots. It is built for the test: it is
     * not a real answer of SWAPI.
     */
    private static final SwapiVehicle SNOWSPEEDER = new SwapiVehicle(
            "Snowspeeder", "t-47 airspeeder", "airspeeder", "Incom corporation", "75000", "4.5", "2", "0",
            "650", "10", "none", FILMS, PILOTS);

    @Test
    @DisplayName("Every value goes to its field as text, and every relation becomes a list of identifiers")
    void everyValueGoesToItsField() {
        assertThat(SNOWSPEEDER.toItem("14")).isEqualTo(new VehicleDetail(
                "14", "Snowspeeder", "t-47 airspeeder", "airspeeder", "Incom corporation", "75000", "4.5", "2", "0",
                "650", "10", "none", List.of("1", "2", "3"), List.of("1", "9", "18")));
    }

    @Test
    @DisplayName("In a list, the vehicle is its identifier and its name")
    void summaryIsTheIdentifierAndTheName() {
        assertThat(SNOWSPEEDER.toItem("14").summary()).isEqualTo(new ItemSummary("14", "Snowspeeder"));
    }

    @Test
    @DisplayName("The length of vehicle 4, which SWAPI sends with a space at the end, loses the space, and no value is converted")
    void lengthOfTheSandCrawlerLosesItsSpaceAndNoValueIsConverted() {
        // The values of vehicle 4 in the real answer of SWAPI: the length ends in a space, and
        // the consumables are a quantity with its unit.
        SwapiVehicle sandCrawler = new SwapiVehicle(
                "Sand Crawler", "Digger Crawler", "wheeled", "Corellia Mining Corporation", "150000", "36.8 ",
                "46", "30", "30", "50000", "2 months",
                List.of("https://www.swapi.tech/api/films/1", "https://www.swapi.tech/api/films/5"), List.of());

        VehicleDetail detail = sandCrawler.toItem("4");

        assertThat(detail.length()).isEqualTo("36.8");
        assertThat(detail.costInCredits()).isEqualTo("150000");
        assertThat(detail.consumables()).isEqualTo("2 months");
        assertThat(detail.filmIds()).containsExactly("1", "5");
        assertThat(detail.pilotIds()).isEmpty();
    }

    @Test
    @DisplayName("The texts lose the spaces at their start and at their end, and nothing else")
    void textsLoseTheSpacesAroundThem() {
        SwapiVehicle vehicle = new SwapiVehicle(
                "  X-34 landspeeder ", " X-34 landspeeder model", "repulsorcraft\t", " SoroSuub Corporation ",
                "10550 ", " 3.4", " 1 ", "2\n", " 250 ", " 5", " 1 week ",
                List.of(" https://www.swapi.tech/api/films/1 "), List.of("https://www.swapi.tech/api/people/13 "));

        assertThat(vehicle.toItem("7")).isEqualTo(new VehicleDetail(
                "7", "X-34 landspeeder", "X-34 landspeeder model", "repulsorcraft", "SoroSuub Corporation",
                "10550", "3.4", "1", "2", "250", "5", "1 week", List.of("1"), List.of("13")));
    }

    @Test
    @DisplayName("A text that SWAPI did not send is null, and the rest of the vehicle is still answered")
    void textThatWasNotSentIsNull() {
        SwapiVehicle vehicle = new SwapiVehicle(
                "Snowspeeder", null, null, null, null, null, null, null, null, null, null, FILMS, PILOTS);

        assertThat(vehicle.toItem("14")).isEqualTo(new VehicleDetail(
                "14", "Snowspeeder", null, null, null, null, null, null, null, null, null, null,
                List.of("1", "2", "3"), List.of("1", "9", "18")));
    }

    @Test
    @DisplayName("A relation that SWAPI did not send is an empty list")
    void relationThatWasNotSentIsAnEmptyList() {
        VehicleDetail detail = vehicle("Snowspeeder", null, null).toItem("14");

        assertThat(detail.filmIds()).isEmpty();
        assertThat(detail.pilotIds()).isEmpty();
    }

    @Test
    @DisplayName("An empty text is answered as it is: it is not the same as a text that was not sent")
    void emptyTextStaysEmpty() {
        SwapiVehicle vehicle = new SwapiVehicle(
                "Snowspeeder", "", "  ", "Incom corporation", "75000", "4.5", "2", "0", "650", "10", "none",
                FILMS, PILOTS);

        VehicleDetail detail = vehicle.toItem("14");

        assertThat(detail.model()).isEmpty();
        assertThat(detail.vehicleClass()).isEmpty();
    }

    @ParameterizedTest(name = "name: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "  "})
    @DisplayName("A vehicle without a name is a failure of the data source: the name is not optional")
    void vehicleWithoutANameIsAFailure(String name) {
        assertThatThrownBy(() -> vehicle(name, FILMS, PILOTS).toItem("14")).isInstanceOf(BadGatewayException.class);
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

        assertThatThrownBy(() -> vehicle("Snowspeeder", badFilms, PILOTS).toItem("14"))
                .isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(() -> vehicle("Snowspeeder", FILMS, badPilots).toItem("14"))
                .isInstanceOf(BadGatewayException.class);
    }

    /** A vehicle with that name and those relations, and the rest of the data of {@link #SNOWSPEEDER}. */
    private static SwapiVehicle vehicle(String name, List<String> films, List<String> pilots) {
        return new SwapiVehicle(
                name, "t-47 airspeeder", "airspeeder", "Incom corporation", "75000", "4.5", "2", "0",
                "650", "10", "none", films, pilots);
    }

}
