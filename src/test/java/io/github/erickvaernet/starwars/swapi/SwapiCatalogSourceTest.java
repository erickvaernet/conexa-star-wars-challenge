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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Checks the client of SWAPI against the simulated SWAPI: what it asks for, how it reads a real
 * answer and what it does with each kind of failure.
 *
 * <p>It needs neither the application nor Docker: only the stub is started. The failures are not
 * recordings; each test tells the stub what to answer.
 */
@SpringJUnitConfig(SwapiStubConfiguration.class)
class SwapiCatalogSourceTest {

    private static final CatalogEntity PEOPLE = new CatalogEntity("people");

    private static final String SWAPI_PEOPLE = "/api/people";

    /** The identifier of the character that has a recording. */
    private static final ItemId LUKE = new ItemId("1");

    private static final String JSON = "application/json; charset=utf-8";

    /** Short, so that the test of the slow answer does not take long. */
    private static final Duration READ_TIMEOUT = Duration.ofMillis(500);

    @Autowired
    private WireMockServer swapi;

    @Autowired
    private Environment environment;

    private SwapiCatalogSource source;

    @BeforeEach
    void setUp() {
        source = sourceAt(environment.getRequiredProperty("SWAPI_BASE_URL"));
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
        // The shape of a list that SWAPI does not cut in pages. The list of people never has it.
        "{\"message\":\"ok\",\"result\":[{\"uid\":\"1\",\"properties\":{\"name\":\"Luke Skywalker\"}}]}"
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
        assertThatThrownBy(() -> source.list(PEOPLE, new PageQuery(1, 10)))
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

        assertThatThrownBy(() -> source.find(PEOPLE, LUKE)).isInstanceOf(GatewayTimeoutException.class);
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
    @DisplayName("An entity whose format was not declared is a mistake in the code: it fails before SWAPI is asked")
    void entityWithoutAFormatFailsBeforeAskingSwapi() {
        assertThatThrownBy(() -> source.find(new CatalogEntity("planets"), LUKE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("planets");

        swapi.verify(0, anyRequestedFor(anyUrl()));
    }

    private static SwapiCatalogSource sourceAt(String baseUrl) {
        return new SwapiCatalogSource(new SwapiProperties(baseUrl, Duration.ofSeconds(3), READ_TIMEOUT));
    }

}
