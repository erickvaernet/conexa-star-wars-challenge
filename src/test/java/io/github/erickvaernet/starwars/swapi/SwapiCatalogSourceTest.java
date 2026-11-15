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
import io.github.erickvaernet.starwars.catalog.people.PersonDetail;
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
        // Not a recording: the shape of the real search, with the names of a film.
        swapi.stubFor(get(urlPathEqualTo("/api/films")).withQueryParam("title", equalTo("hope"))
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON).withBody("""
                        {"message":"ok","result":[{"uid":"1","properties":{"title":"A New Hope","episode_id":4}}]}
                        """)));

        Listing listing = source.search(new CatalogEntity("films", "title"), new NameFilter("hope"), new PageQuery(1, 10));

        assertThat(listing).isEqualTo(new Listing.Complete(List.of(new ItemSummary("1", "A New Hope"))));
        swapi.verify(0, getRequestedFor(urlPathEqualTo("/api/films")).withQueryParam("name", equalTo("hope")));
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
