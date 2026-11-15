package io.github.erickvaernet.starwars.swapi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.ItemId;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.Listing;
import io.github.erickvaernet.starwars.catalog.NameFilter;
import io.github.erickvaernet.starwars.catalog.PageQuery;
import io.github.erickvaernet.starwars.catalog.films.FilmDetail;
import io.github.erickvaernet.starwars.catalog.people.PersonDetail;
import io.github.erickvaernet.starwars.catalog.starships.StarshipDetail;
import io.github.erickvaernet.starwars.catalog.vehicles.VehicleDetail;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import io.github.erickvaernet.starwars.shared.GatewayTimeoutException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Checks the client of SWAPI against the simulated SWAPI: what it asks for, in a list, in a search
 * and for one element, how it reads a real answer and what it does with each kind of failure.
 *
 * <p>It needs neither the application nor Docker: only the stub is started. The failures are not
 * recordings; each test tells the stub what to answer.
 */
@SpringJUnitConfig(SwapiStubConfiguration.class)
class SwapiCatalogSourceTest {

    private static final CatalogEntity PEOPLE = new CatalogEntity("people", "name");

    private static final String SWAPI_PEOPLE = "/api/people";

    private static final CatalogEntity FILMS = new CatalogEntity("films", "title");

    private static final String SWAPI_FILMS = "/api/films";

    private static final CatalogEntity STARSHIPS = new CatalogEntity("starships", "name");

    private static final String SWAPI_STARSHIPS = "/api/starships";

    private static final CatalogEntity VEHICLES = new CatalogEntity("vehicles", "name");

    private static final String SWAPI_VEHICLES = "/api/vehicles";

    /** The identifier of the character that has a recording. */
    private static final ItemId LUKE = new ItemId("1");

    private static final String JSON = "application/json; charset=utf-8";

    private static final NameFilter SKY = new NameFilter("sky");

    /** What SWAPI really answers to a search without matches. */
    private static final String NO_MATCHES = "{\"message\":\"ok\",\"result\":[]}";

    /**
     * Long, so that no test depends on how fast the machine is. The first answer that a newly
     * started JVM reads can take longer than half a second.
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /** Short, so that the tests of the slow answer do not take long. Only they use it. */
    private static final Duration SHORT_READ_TIMEOUT = Duration.ofMillis(500);

    @Autowired
    private WireMockServer swapi;

    @Autowired
    private Environment environment;

    private SwapiCatalogSource source;

    /** The same client, with a read timeout that a slow answer of the stub exceeds soon. */
    private SwapiCatalogSource impatientSource;

    @BeforeEach
    void setUp() {
        String baseUrl = environment.getRequiredProperty("SWAPI_BASE_URL");
        source = sourceAt(baseUrl);
        impatientSource = new SwapiCatalogSource(
                new SwapiProperties(baseUrl, Duration.ofSeconds(3), SHORT_READ_TIMEOUT));
    }

    @AfterEach
    void bringTheRecordingsBack() {
        swapi.resetToDefaultMappings();
        swapi.resetRequests();
    }

    @Test
    @DisplayName("A real page of SWAPI is read: its elements, in order, and the total of records")
    void realPageIsRead() {
        Listing listing = source.list(PEOPLE, new PageQuery(9, 10));

        assertThat(listing).isEqualTo(new Listing.OnePage(
                List.of(new ItemSummary("82", "Sly Moore"), new ItemSummary("83", "Tion Medon")), 82));
    }

    @Test
    @DisplayName("SWAPI is always asked for the page and the size of the request, by the name of the entity")
    void swapiIsAskedForThePageAndTheSize() {
        source.list(PEOPLE, new PageQuery(1, 100));

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("limit", equalTo("100")));
    }

    @Test
    @DisplayName("The first page of the largest size brings the 82 people")
    void largestPageBringsEveryone() {
        Listing.OnePage page = (Listing.OnePage) source.list(PEOPLE, new PageQuery(1, 100));

        assertThat(page.items()).hasSize(82);
        assertThat(page.totalElements()).isEqualTo(82);
    }

    @Test
    @DisplayName("A list that SWAPI answers whole, without cutting pages, is read as the complete list")
    void listThatSwapiAnswersWholeIsReadAsComplete() {
        // Not a recording: the list of people never has this shape. It is the one SWAPI uses
        // when it ignores the page and the size.
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON).withBody("""
                        {"message":"ok","result":[
                          {"uid":"1","properties":{"name":"Luke Skywalker","height":"172"},"description":"A person"},
                          {"uid":"2","properties":{"name":"C-3PO"}}]}
                        """)));

        Listing listing = source.list(PEOPLE, new PageQuery(2, 1));

        assertThat(listing).isEqualTo(new Listing.Complete(
                List.of(new ItemSummary("1", "Luke Skywalker"), new ItemSummary("2", "C-3PO"))));
    }

    @ParameterizedTest(name = "SWAPI answers {0}")
    // 429 is what SWAPI answers after 100 requests in 15 minutes.
    @ValueSource(ints = {400, 404, 429, 500, 503})
    @DisplayName("An error answer of SWAPI is a failure of the data source")
    void errorAnswerIsAFailure(int status) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(status)
                        .withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBody("{\"message\":\"it failed\"}")));

        assertThatThrownBy(() -> source.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);
    }

    @ParameterizedTest(name = "SWAPI answers 200 with: {0}")
    @ValueSource(strings = {
        "this is not JSON",
        "{\"message\":\"ok\",\"total_records\":82,\"results\":[{\"uid\":\"1\"",
        "[]",
        "{}",
        "{\"message\":\"ok\"}",
        "{\"total_records\":\"many\",\"results\":[]}",
        "{\"total_records\":82,\"results\":\"none\"}",
        // The shape of a list that SWAPI does not cut in pages, with something missing or wrong:
        // the result is one element and not a list, or an element has no name or no identifier.
        "{\"message\":\"ok\",\"result\":{\"uid\":\"1\",\"properties\":{\"name\":\"Luke Skywalker\"}}}",
        "{\"message\":\"ok\",\"result\":[{\"uid\":\"1\",\"properties\":{\"title\":\"A New Hope\"}}]}",
        "{\"message\":\"ok\",\"result\":[{\"properties\":{\"name\":\"Luke Skywalker\"}}]}",
        "{\"message\":\"ok\",\"result\":\"none\"}"
    })
    @DisplayName("An answer that cannot be read is a failure of the data source")
    void answerThatCannotBeReadIsAFailure(String body) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON).withBody(body)));

        assertThatThrownBy(() -> source.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An answer that is not JSON at all, like an error page, is a failure of the data source")
    void answerThatIsNotJsonIsAFailure() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "text/html")
                        .withBody("<html><body>Maintenance</body></html>")));

        assertThatThrownBy(() -> source.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An answer without a body is a failure of the data source")
    void answerWithoutABodyIsAFailure() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON)));

        assertThatThrownBy(() -> source.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("A connection that SWAPI cuts is a failure of the data source")
    void connectionCutIsAFailure() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> source.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("When nothing listens at the address of SWAPI, it is a failure of the data source")
    void nothingListeningIsAFailure() {
        SwapiCatalogSource unreachable = sourceAt("http://localhost:1/api");

        assertThatThrownBy(() -> unreachable.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An answer that takes longer than the read timeout is a timeout, and the wait ends there")
    void slowAnswerIsATimeout() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBodyFile("people-page-1-limit-10.json")
                        .withFixedDelay(3000)));

        long start = System.nanoTime();
        assertThatThrownBy(() -> impatientSource.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(GatewayTimeoutException.class);
        Duration waited = Duration.ofNanos(System.nanoTime() - start);

        // It waited the read timeout, not the 3 seconds that the answer takes.
        assertThat(waited).isLessThan(Duration.ofMillis(2500));
    }

    @Test
    @DisplayName("The failure keeps what failed for the log")
    void failureKeepsWhatFailedForTheLog() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> source.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class)
                .hasMessageContaining("people")
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("A real character of SWAPI is read: every field of its detail, and its relations as identifiers")
    void realCharacterIsRead() {
        assertThat(source.find(PEOPLE, LUKE)).contains(new PersonDetail(
                "1", "Luke Skywalker", "19BBY", "male", "172", "77", "blond", "fair", "blue",
                List.of("1", "2", "3", "6"), List.of("12", "22"), List.of("14", "30")));
    }

    @Test
    @DisplayName("SWAPI is asked for one element by the name of the entity and the identifier, and for nothing else")
    void swapiIsAskedForTheElement() {
        source.find(PEOPLE, LUKE);

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/1")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("When SWAPI really answers that a character does not exist, there is no element, and it is not a failure")
    void characterThatDoesNotExistIsNotAFailure() {
        // The recording of the real answer for character 9999: a 404 with a body of its own.
        assertThat(source.find(PEOPLE, new ItemId("9999"))).isEmpty();

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE + "/9999")));
    }

    @ParameterizedTest(name = "SWAPI answers {0}")
    // Every error but 404, which for one element means that it does not exist.
    @ValueSource(ints = {400, 429, 500, 503})
    @DisplayName("Any other error answer of SWAPI for one element is a failure of the data source")
    void errorAnswerForAnElementIsAFailure(int status) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE + "/1")).atPriority(1)
                .willReturn(aResponse().withStatus(status)
                        .withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBody("{\"message\":\"it failed\"}")));

        assertThatThrownBy(() -> source.find(PEOPLE, LUKE)).isInstanceOf(BadGatewayException.class);
    }

    @ParameterizedTest(name = "SWAPI answers 200 with: {0}")
    @ValueSource(strings = {
        "this is not JSON",
        "{\"message\":\"ok\",\"result\":{\"uid\":\"1\",\"properties\":{\"name\":\"Luke",
        "[]",
        "{}",
        "{\"message\":\"ok\"}",
        // Without the identifier, without the data, and without the name.
        "{\"result\":{\"properties\":{\"name\":\"Luke Skywalker\"}}}",
        "{\"result\":{\"uid\":\"1\"}}",
        "{\"result\":{\"uid\":\"1\",\"properties\":{\"height\":\"172\"}}}",
        // A relation that is not a list, and one with an address without its identifier.
        "{\"result\":{\"uid\":\"1\",\"properties\":{\"name\":\"Luke Skywalker\",\"films\":\"none\"}}}",
        "{\"result\":{\"uid\":\"1\",\"properties\":{\"name\":\"Luke Skywalker\","
                + "\"films\":[\"https://www.swapi.tech/api/films/\"]}}}",
        // The shape of a list instead of the one of an element.
        "{\"message\":\"ok\",\"total_records\":82,\"results\":[{\"uid\":\"1\",\"name\":\"Luke Skywalker\"}]}"
    })
    @DisplayName("An answer for one element that cannot be read is a failure of the data source")
    void answerForAnElementThatCannotBeReadIsAFailure(String body) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE + "/1")).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON).withBody(body)));

        assertThatThrownBy(() -> source.find(PEOPLE, LUKE)).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("A character with only its identifier and its name is read: what SWAPI does not send is null or empty")
    void characterWithOnlyItsNameIsRead() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE + "/1")).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBody("{\"result\":{\"uid\":\"1\",\"properties\":{\"name\":\"Luke Skywalker\"}}}")));

        assertThat(source.find(PEOPLE, LUKE)).contains(new PersonDetail(
                "1", "Luke Skywalker", null, null, null, null, null, null, null, List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("An answer for one element without a body is a failure of the data source")
    void answerForAnElementWithoutABodyIsAFailure() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE + "/1")).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON)));

        assertThatThrownBy(() -> source.find(PEOPLE, LUKE)).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("When nothing listens at the address of SWAPI, asking for one element is a failure of the data source")
    void nothingListeningIsAFailureForAnElement() {
        SwapiCatalogSource unreachable = sourceAt("http://localhost:1/api");

        assertThatThrownBy(() -> unreachable.find(PEOPLE, LUKE)).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An answer for one element that takes longer than the read timeout is a timeout")
    void slowAnswerForAnElementIsATimeout() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE + "/1")).atPriority(1)
                .willReturn(aResponse().withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBodyFile("people-1.json")
                        .withFixedDelay(3000)));

        assertThatThrownBy(() -> impatientSource.find(PEOPLE, LUKE)).isInstanceOf(GatewayTimeoutException.class);
    }

    @Test
    @DisplayName("The failure of a request for one element keeps what failed for the log")
    void failureForAnElementKeepsWhatFailedForTheLog() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE + "/1")).atPriority(1)
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> source.find(PEOPLE, LUKE))
                .isInstanceOf(BadGatewayException.class)
                .hasMessageContaining("people/1")
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("A real search of SWAPI is read: every match at once, in order, with the name taken from the data of each one")
    void realSearchIsRead() {
        Listing listing = source.search(PEOPLE, SKY, new PageQuery(1, 10));

        assertThat(listing).isEqualTo(new Listing.Complete(List.of(
                new ItemSummary("1", "Luke Skywalker"),
                new ItemSummary("11", "Anakin Skywalker"),
                new ItemSummary("43", "Shmi Skywalker"))));
    }

    @Test
    @DisplayName("A real search of SWAPI without matches is read as a complete list without elements, not as a failure")
    void realSearchWithoutMatchesIsRead() {
        assertThat(source.search(PEOPLE, new NameFilter("zzzz"), new PageQuery(1, 10)))
                .isEqualTo(new Listing.Complete(List.of()));
    }

    @Test
    @DisplayName("SWAPI is asked to search by the name of the entity and the text, with the page and the size of the request, once")
    void swapiIsAskedToSearch() {
        source.search(PEOPLE, SKY, new PageQuery(2, 5));

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE))
                .withQueryParam("name", equalTo("sky"))
                .withQueryParam("page", equalTo("2"))
                .withQueryParam("limit", equalTo("5")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("The search uses the field that the entity declares, to ask and to read the names: for a film, its title")
    void searchUsesTheFieldOfTheEntity() {
        // The recording of the real search of SWAPI for the films whose title contains "hope".
        Listing listing = source.search(FILMS, new NameFilter("hope"), new PageQuery(1, 10));

        assertThat(listing).isEqualTo(new Listing.Complete(List.of(new ItemSummary("1", "A New Hope"))));
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_FILMS)).withQueryParam("title", equalTo("hope")));
        swapi.verify(0, getRequestedFor(urlPathEqualTo(SWAPI_FILMS)).withQueryParam("name", equalTo("hope")));
    }

    @ParameterizedTest(name = "\"{0}\" reaches SWAPI as \"{1}\"")
    @CsvSource(quoteCharacter = '`', value = {
        // A text without special characters does not change.
        "sky, sky",
        "obi-wan kenobi, obi-wan kenobi",
        "r2-d2, r2-d2",
        "padmé, padmé",
        // SWAPI reads the text as a pattern. A backslash before each special character makes
        // SWAPI look for that character and nothing else.
        "l.ke, l\\.ke",
        "(, \\(",
        "\\, \\\\",
        "a|b, a\\|b",
        "^luke$, \\^luke\\$",
        "c-3po?, c-3po\\?",
        "sky*, sky\\*",
        "sky+, sky\\+",
        "[sky], \\[sky\\]",
        "{2}, \\{2\\}",
        "(sky), \\(sky\\)",
        "\\d, \\\\d",
        // Characters with a meaning in an address travel as part of the text, not of the address.
        "a&b=c, a&b=c",
        "50% #1, 50% #1",
        "a/b:c, a/b:c"
    })
    @DisplayName("The text is searched as it is: the characters that SWAPI would read as a pattern are escaped")
    void textReachesSwapiWithItsSpecialCharactersEscaped(String text, String received) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON).withBody(NO_MATCHES)));

        source.search(PEOPLE, new NameFilter(text), new PageQuery(1, 10));

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_PEOPLE)).withQueryParam("name", equalTo(received)));
    }

    @Test
    @DisplayName("If SWAPI answered a search with a page and its total, it is read as a page")
    void searchThatSwapiCutsInPagesIsReadAsAPage() {
        // Not a recording: SWAPI does not do this today. The form of the answer decides.
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON).withBody("""
                        {"message":"ok","total_records":3,"total_pages":2,"results":[{"uid":"43","name":"Shmi Skywalker"}]}
                        """)));

        assertThat(source.search(PEOPLE, SKY, new PageQuery(2, 2)))
                .isEqualTo(new Listing.OnePage(List.of(new ItemSummary("43", "Shmi Skywalker")), 3));
    }

    @ParameterizedTest(name = "SWAPI answers {0}")
    // 400 is what SWAPI answers to a text it cannot read as a pattern. And 404 is not "no
    // matches": a search without matches is answered with 200 and an empty list.
    @ValueSource(ints = {400, 404, 429, 500, 503})
    @DisplayName("An error answer of SWAPI to a search is a failure of the data source")
    void errorAnswerToASearchIsAFailure(int status) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(status)
                        .withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBody("{\"message\":\"it failed\"}")));

        assertThatThrownBy(() -> source.search(PEOPLE, SKY, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);
    }

    @ParameterizedTest(name = "SWAPI answers 200 with: {0}")
    @ValueSource(strings = {
        "this is not JSON",
        "{}",
        "{\"message\":\"ok\"}",
        "{\"message\":\"ok\",\"result\":{\"uid\":\"1\",\"properties\":{\"name\":\"Luke Skywalker\"}}}",
        "{\"message\":\"ok\",\"result\":[{\"uid\":\"1\",\"properties\":{}}]}",
        "{\"message\":\"ok\",\"result\":[{\"uid\":\"1\"}]}",
        "{\"message\":\"ok\",\"result\":[{\"properties\":{\"name\":\"Luke Skywalker\"}}]}"
    })
    @DisplayName("An answer to a search that cannot be read is a failure of the data source")
    void answerToASearchThatCannotBeReadIsAFailure(String body) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON).withBody(body)));

        assertThatThrownBy(() -> source.search(PEOPLE, SKY, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An answer to a search without a body is a failure of the data source")
    void answerToASearchWithoutABodyIsAFailure() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON)));

        assertThatThrownBy(() -> source.search(PEOPLE, SKY, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("When nothing listens at the address of SWAPI, a search is a failure of the data source")
    void nothingListeningIsAFailureOfASearch() {
        SwapiCatalogSource unreachable = sourceAt("http://localhost:1/api");

        assertThatThrownBy(() -> unreachable.search(PEOPLE, SKY, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An answer to a search that takes longer than the read timeout is a timeout")
    void slowAnswerToASearchIsATimeout() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBodyFile("people-name-sky.json")
                        .withFixedDelay(3000)));

        assertThatThrownBy(() -> impatientSource.search(PEOPLE, SKY, new PageQuery(1, 10)))
                .isInstanceOf(GatewayTimeoutException.class);
    }

    @Test
    @DisplayName("The failure of a search keeps what failed for the log, without the text that the client sent")
    void failureOfASearchKeepsWhatFailedForTheLogWithoutTheText() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> source.search(PEOPLE, new NameFilter("a text\nwith two lines"), new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class)
                .hasMessageContaining("people")
                .hasMessageContaining("name")
                .hasMessageNotContaining("two lines")
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("The real list of films, which SWAPI does not cut in pages, is read as the complete list, with each title as the name")
    void realListOfFilmsIsReadAsComplete() {
        // The recording answers the six films whatever the page and the size, like SWAPI.
        Listing listing = source.list(FILMS, new PageQuery(2, 2));

        assertThat(listing).isEqualTo(new Listing.Complete(List.of(
                new ItemSummary("1", "A New Hope"),
                new ItemSummary("2", "The Empire Strikes Back"),
                new ItemSummary("3", "Return of the Jedi"),
                new ItemSummary("4", "The Phantom Menace"),
                new ItemSummary("5", "Attack of the Clones"),
                new ItemSummary("6", "Revenge of the Sith"))));
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_FILMS))
                .withQueryParam("page", equalTo("2"))
                .withQueryParam("limit", equalTo("2")));
    }

    @Test
    @DisplayName("A real film of SWAPI is read: every field of its detail, the episode as a number and its relations as identifiers")
    void realFilmIsRead() {
        assertThat(source.find(FILMS, new ItemId("1"))).contains(new FilmDetail(
                "1", "A New Hope", 4,
                "It is a period of civil war.\r\nRebel spaceships, striking\r\nfrom a hidden base, have won\r\n"
                        + "their first victory against\r\nthe evil Galactic Empire.\r\n\r\nDuring the battle, Rebel\r\n"
                        + "spies managed to steal secret\r\nplans to the Empire's\r\nultimate weapon, the DEATH\r\n"
                        + "STAR, an armored space\r\nstation with enough power\r\nto destroy an entire planet.\r\n\r\n"
                        + "Pursued by the Empire's\r\nsinister agents, Princess\r\nLeia races home aboard her\r\n"
                        + "starship, custodian of the\r\nstolen plans that can save her\r\npeople and restore\r\n"
                        + "freedom to the galaxy....",
                "George Lucas", "Gary Kurtz, Rick McCallum", "1977-05-25",
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "12", "13", "14", "15", "16", "18", "19", "81"),
                List.of("2", "3", "5", "9", "10", "11", "12", "13"),
                List.of("4", "6", "7", "8")));

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_FILMS + "/1")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("A film with only its identifier and its title is read: what SWAPI does not send is null or empty, the episode too")
    void filmWithOnlyItsTitleIsRead() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_FILMS + "/1")).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBody("{\"result\":{\"uid\":\"1\",\"properties\":{\"title\":\"A New Hope\",\"episode_id\":null}}}")));

        assertThat(source.find(FILMS, new ItemId("1"))).contains(new FilmDetail(
                "1", "A New Hope", null, null, null, null, null, List.of(), List.of(), List.of()));
    }

    @ParameterizedTest(name = "\"episode_id\": {0}")
    // A text, even with digits; a number with decimals, even zero; a number too large; a boolean.
    @ValueSource(strings = {"\"4\"", "\"IV\"", "4.5", "4.0", "3000000000", "true"})
    @DisplayName("A film whose episode is not a whole number is a failure of the data source: it is not converted")
    void filmWhoseEpisodeIsNotAWholeNumberIsAFailure(String episode) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_FILMS + "/1")).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBody("{\"result\":{\"uid\":\"1\",\"properties\":{\"title\":\"A New Hope\","
                                + "\"episode_id\":" + episode + "}}}")));

        assertThatThrownBy(() -> source.find(FILMS, new ItemId("1"))).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("A real page of starships, which SWAPI cuts in pages, is read as one page with the total, in the order of SWAPI")
    void realPageOfStarshipsIsRead() {
        Listing listing = source.list(STARSHIPS, new PageQuery(1, 5));

        // The order of SWAPI is not the one of the identifiers, and they have gaps.
        assertThat(listing).isEqualTo(new Listing.OnePage(List.of(
                new ItemSummary("2", "CR90 corvette"),
                new ItemSummary("3", "Star Destroyer"),
                new ItemSummary("5", "Sentinel-class landing craft"),
                new ItemSummary("9", "Death Star"),
                new ItemSummary("11", "Y-wing")), 36));
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_STARSHIPS))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("limit", equalTo("5")));
    }

    @Test
    @DisplayName("A real starship of SWAPI is read: every field of its detail as text, and its relations as identifiers")
    void realStarshipIsRead() {
        assertThat(source.find(STARSHIPS, new ItemId("9"))).contains(new StarshipDetail(
                "9", "Death Star", "DS-1 Orbital Battle Station", "Deep Space Mobile Battlestation",
                "Imperial Department of Military Research, Sienar Fleet Systems", "1000000000000", "120000",
                "342,953", "843,342", "n/a", "4.0", "10", "1000000000000", "3 years",
                List.of("1"), List.of()));

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_STARSHIPS + "/9")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("Each data of a starship is read from its name in SWAPI, MGLT in capital letters too, and its pilots become identifiers")
    void eachDataOfAStarshipIsReadFromItsNameInSwapi() {
        // Not a recording. Starship 9 of the recording has no pilots, and its cost and its cargo
        // capacity are the same text. Here every value is different, so one read from the name
        // of another shows.
        swapi.stubFor(get(urlPathEqualTo(SWAPI_STARSHIPS + "/12"))
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBody("""
                                {"message":"ok","result":{"properties":{
                                  "created":"2026-11-14T16:52:06.918Z","edited":"2026-11-14T16:52:06.918Z",
                                  "consumables":"1 week","name":"X-wing","cargo_capacity":"110","passengers":"0",
                                  "max_atmosphering_speed":"1050","crew":"1","length":"12.5","model":"T-65 X-wing",
                                  "cost_in_credits":"149999","manufacturer":"Incom Corporation",
                                  "pilots":["https://www.swapi.tech/api/people/1","https://www.swapi.tech/api/people/9"],
                                  "MGLT":"100","starship_class":"Starfighter","hyperdrive_rating":"1.0",
                                  "films":["https://www.swapi.tech/api/films/1","https://www.swapi.tech/api/films/2"],
                                  "url":"https://www.swapi.tech/api/starships/12"},
                                  "description":"A Starship","uid":"12"}}
                                """)));

        assertThat(source.find(STARSHIPS, new ItemId("12"))).contains(new StarshipDetail(
                "12", "X-wing", "T-65 X-wing", "Starfighter", "Incom Corporation", "149999", "12.5", "1", "0",
                "1050", "1.0", "100", "110", "1 week", List.of("1", "2"), List.of("1", "9")));
    }

    @Test
    @DisplayName("A starship with only its identifier and its name is read: what SWAPI does not send is null or empty")
    void starshipWithOnlyItsNameIsRead() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_STARSHIPS + "/9")).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBody("{\"result\":{\"uid\":\"9\",\"properties\":{\"name\":\"Death Star\"}}}")));

        assertThat(source.find(STARSHIPS, new ItemId("9"))).contains(new StarshipDetail(
                "9", "Death Star", null, null, null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of()));
    }

    @Test
    @DisplayName("A starship with a pilot whose address has no identifier is a failure of the data source")
    void starshipWithABadAddressIsAFailure() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_STARSHIPS + "/9")).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBody("{\"result\":{\"uid\":\"9\",\"properties\":{\"name\":\"Death Star\","
                                + "\"pilots\":[\"https://www.swapi.tech/api/people/\"]}}}")));

        assertThatThrownBy(() -> source.find(STARSHIPS, new ItemId("9"))).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("A real page of vehicles, which SWAPI cuts in pages, is read as one page with the total, in the order of SWAPI")
    void realPageOfVehiclesIsRead() {
        Listing listing = source.list(VEHICLES, new PageQuery(1, 10));

        // The order of SWAPI is not the one of the identifiers, and they have gaps.
        assertThat(listing).isEqualTo(new Listing.OnePage(List.of(
                new ItemSummary("4", "Sand Crawler"),
                new ItemSummary("7", "X-34 landspeeder"),
                new ItemSummary("6", "T-16 skyhopper"),
                new ItemSummary("8", "TIE/LN starfighter"),
                new ItemSummary("14", "Snowspeeder"),
                new ItemSummary("18", "AT-AT"),
                new ItemSummary("16", "TIE bomber"),
                new ItemSummary("19", "AT-ST"),
                new ItemSummary("20", "Storm IV Twin-Pod cloud car"),
                new ItemSummary("24", "Sail barge")), 39));
        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_VEHICLES))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("limit", equalTo("10")));
    }

    @Test
    @DisplayName("A real vehicle of SWAPI is read: every field of its detail as text, its length without the space that SWAPI sends at its end, and its relations as identifiers")
    void realVehicleIsRead() {
        // The recording has the length as "36.8 ", with a space at the end.
        assertThat(source.find(VEHICLES, new ItemId("4"))).contains(new VehicleDetail(
                "4", "Sand Crawler", "Digger Crawler", "wheeled", "Corellia Mining Corporation", "150000", "36.8",
                "46", "30", "30", "50000", "2 months", List.of("1", "5"), List.of()));

        swapi.verify(1, getRequestedFor(urlPathEqualTo(SWAPI_VEHICLES + "/4")));
        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("Each data of a vehicle is read from its name in SWAPI, and its pilots become identifiers")
    void eachDataOfAVehicleIsReadFromItsNameInSwapi() {
        // Not a recording. Vehicle 4 of the recording has no pilots, and its passengers and its
        // maximum speed are the same text. Here every value is different, so one read from the
        // name of another shows.
        swapi.stubFor(get(urlPathEqualTo(SWAPI_VEHICLES + "/14"))
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBody("""
                                {"message":"ok","result":{"properties":{
                                  "created":"2026-11-14T16:52:06.918Z","edited":"2026-11-14T16:52:06.918Z",
                                  "consumables":"none","name":"Snowspeeder","cargo_capacity":"10","passengers":"0",
                                  "max_atmosphering_speed":"650","crew":"2","length":"4.5","model":"t-47 airspeeder",
                                  "cost_in_credits":"75000","manufacturer":"Incom corporation",
                                  "vehicle_class":"airspeeder",
                                  "pilots":["https://www.swapi.tech/api/people/1","https://www.swapi.tech/api/people/18"],
                                  "films":["https://www.swapi.tech/api/films/2"],
                                  "url":"https://www.swapi.tech/api/vehicles/14"},
                                  "description":"A vehicle","uid":"14"}}
                                """)));

        assertThat(source.find(VEHICLES, new ItemId("14"))).contains(new VehicleDetail(
                "14", "Snowspeeder", "t-47 airspeeder", "airspeeder", "Incom corporation", "75000", "4.5", "2", "0",
                "650", "10", "none", List.of("2"), List.of("1", "18")));
    }

    @Test
    @DisplayName("A vehicle with only its identifier and its name is read: what SWAPI does not send is null or empty")
    void vehicleWithOnlyItsNameIsRead() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_VEHICLES + "/4")).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBody("{\"result\":{\"uid\":\"4\",\"properties\":{\"name\":\"Sand Crawler\"}}}")));

        assertThat(source.find(VEHICLES, new ItemId("4"))).contains(new VehicleDetail(
                "4", "Sand Crawler", null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of()));
    }

    @Test
    @DisplayName("A vehicle with a pilot whose address has no identifier is a failure of the data source")
    void vehicleWithABadAddressIsAFailure() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_VEHICLES + "/4")).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                        .withBody("{\"result\":{\"uid\":\"4\",\"properties\":{\"name\":\"Sand Crawler\","
                                + "\"pilots\":[\"https://www.swapi.tech/api/people/\"]}}}")));

        assertThatThrownBy(() -> source.find(VEHICLES, new ItemId("4"))).isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("An entity whose format was not declared is a mistake in the code: it fails before SWAPI is asked")
    void entityWithoutAFormatFailsBeforeAskingSwapi() {
        assertThatThrownBy(() -> source.find(new CatalogEntity("planets", "name"), LUKE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("planets");

        swapi.verify(0, anyRequestedFor(anyUrl()));
    }

    private static SwapiCatalogSource sourceAt(String baseUrl) {
        return new SwapiCatalogSource(new SwapiProperties(baseUrl, Duration.ofSeconds(3), READ_TIMEOUT));
    }

}
