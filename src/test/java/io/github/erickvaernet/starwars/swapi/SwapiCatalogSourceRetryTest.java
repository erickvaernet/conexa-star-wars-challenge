package io.github.erickvaernet.starwars.swapi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.erickvaernet.starwars.SwapiStubConfiguration;
import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.ItemId;
import io.github.erickvaernet.starwars.catalog.NameFilter;
import io.github.erickvaernet.starwars.catalog.PageQuery;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import io.github.erickvaernet.starwars.shared.GatewayTimeoutException;
import java.time.Duration;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Checks which failures of SWAPI make its client send a request again, how many times, and which
 * ones do not.
 *
 * <p>It needs neither the application nor Docker: only the stub is started. The failures are not
 * recordings; each test tells the stub what to answer. Where SWAPI "recovers", the answer is the
 * recording. Each test counts how many requests the stub received.
 *
 * <p>The wait before a retry is of a few milliseconds here, so the tests do not take long. The
 * two tests that need a longer one say why.
 */
@SpringJUnitConfig(SwapiStubConfiguration.class)
class SwapiCatalogSourceRetryTest {

    private static final CatalogEntity PEOPLE = new CatalogEntity("people", "name");

    private static final String SWAPI_PEOPLE = "/api/people";

    private static final String SWAPI_LUKE = "/api/people/1";

    private static final String JSON = "application/json; charset=utf-8";

    /** Long, so that no test depends on how fast the machine is. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /** Short, so that the tests of the slow answer do not take long. Only they use it. */
    private static final Duration SHORT_READ_TIMEOUT = Duration.ofMillis(500);

    private static final Duration RETRY_DELAY = Duration.ofMillis(10);

    /** How every line that the client writes in the log for a retry begins. */
    private static final String RETRY_LINE = "Retrying a request to SWAPI";

    /**
     * The five requests of the client, each one with a recording to answer it and the path where
     * SWAPI receives it.
     */
    enum Call {

        LIST(SWAPI_PEOPLE, source -> source.list(PEOPLE, new PageQuery(1, 10))),

        SEARCH(SWAPI_PEOPLE, source -> source.search(PEOPLE, new NameFilter("sky"), new PageQuery(1, 10))),

        LIST_EXPANDED(SWAPI_PEOPLE, source -> source.listExpanded(PEOPLE, new PageQuery(1, 2))),

        SEARCH_EXPANDED(SWAPI_PEOPLE,
                source -> source.searchExpanded(PEOPLE, new NameFilter("sky"), new PageQuery(1, 10))),

        FIND(SWAPI_LUKE, source -> source.find(PEOPLE, new ItemId("1")));

        private final String path;

        private final Function<SwapiCatalogSource, Object> request;

        Call(String path, Function<SwapiCatalogSource, Object> request) {
            this.path = path;
            this.request = request;
        }

        Object sendWith(SwapiCatalogSource source) {
            return request.apply(source);
        }

    }

    @Autowired
    private WireMockServer swapi;

    @Autowired
    private Environment environment;

    private String baseUrl;

    /** The client with the retries by default: one. */
    private SwapiCatalogSource source;

    @BeforeEach
    void setUp() {
        baseUrl = environment.getRequiredProperty("SWAPI_BASE_URL");
        source = sourceWith(1, RETRY_DELAY, READ_TIMEOUT);
        // The stub is shared with other test classes, and one that ran before may have left its
        // requests written down: every test here counts all the requests that the stub received.
        swapi.resetRequests();
    }

    @AfterEach
    void bringTheRecordingsBack() {
        swapi.resetToDefaultMappings();
        swapi.resetRequests();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Call.class)
    @DisplayName("Each of the five requests is sent again when SWAPI answers 503, and the answer is the one of the retry")
    void everyRequestIsRetried(Call call) {
        swapiFailsOnce(call.path, error(503));

        Object answer = call.sendWith(source);

        // The recording, read as always: every one of them has Luke Skywalker.
        assertThat(answer.toString()).contains("Luke Skywalker");
        swapi.verify(2, getRequestedFor(urlPathEqualTo(call.path)));
        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest(name = "SWAPI always answers {0}")
    @ValueSource(ints = {502, 503, 504})
    @DisplayName("A status that means \"not now\" is retried once: if SWAPI keeps answering it, it is a failure after exactly two requests")
    void statusThatMeansNotNowIsRetriedOnce(int status) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1).willReturn(error(status)));

        assertThatThrownBy(() -> source.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);

        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest(name = "SWAPI always answers {0}")
    @ValueSource(ints = {502, 503, 504})
    @DisplayName("A status that means \"not now\" for one element is retried once too")
    void statusThatMeansNotNowForAnElementIsRetriedOnce(int status) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_LUKE)).atPriority(1).willReturn(error(status)));

        assertThatThrownBy(() -> source.find(PEOPLE, new ItemId("1")))
                .isInstanceOf(BadGatewayException.class);

        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest(name = "SWAPI answers {0}")
    // 429 is what SWAPI answers after 100 requests in 15 minutes: one more would make it worse.
    // 500 is usually an error that repeats itself. 501 is the same answer however many times.
    // 408 is a 4xx like the others, although its name sounds like something worth trying again.
    @ValueSource(ints = {400, 404, 408, 429, 500, 501})
    @DisplayName("Any other error status is not retried: it is a failure after a single request")
    void otherErrorStatusIsNotRetried(int status) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1).willReturn(error(status)));

        assertThatThrownBy(() -> source.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);

        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest(name = "SWAPI answers {0}")
    @ValueSource(ints = {400, 408, 429, 500})
    @DisplayName("Any other error status for one element is not retried either")
    void otherErrorStatusForAnElementIsNotRetried(int status) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_LUKE)).atPriority(1).willReturn(error(status)));

        assertThatThrownBy(() -> source.find(PEOPLE, new ItemId("1")))
                .isInstanceOf(BadGatewayException.class);

        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("An element that does not exist is an answer, not a failure: it is not asked for again")
    void elementThatDoesNotExistIsNotRetried() {
        // A recording: the "not found" that SWAPI really answers for character 9999.
        assertThat(source.find(PEOPLE, new ItemId("9999"))).isEmpty();

        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest(name = "{0}")
    // The first two close the connection without answering. The third sends bytes that are not
    // an HTTP answer, not even its first line, and closes: no answer arrived either, and the HTTP
    // client reports it in the same way as a connection that was cut.
    @EnumSource(value = Fault.class, names = {"CONNECTION_RESET_BY_PEER", "EMPTY_RESPONSE", "RANDOM_DATA_THEN_CLOSE"})
    @DisplayName("A connection that is cut, or that brings something that is not an HTTP answer, is retried")
    void connectionThatIsCutIsRetried(Fault fault) {
        swapiFailsOnce(SWAPI_PEOPLE, aResponse().withFault(fault));

        assertThat(source.list(PEOPLE, new PageQuery(1, 10)).toString()).contains("Luke Skywalker");

        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("An answer that begins as a valid one and goes on with a body that is not, is retried or not according to what the HTTP client reports, and never ends in another error")
    void answerWithAValidStartAndAMalformedBodyEndsInOneOfTwoWays() {
        // The stub sends a valid first line, then bytes that are not a body, and closes. What
        // the HTTP client reports changes from one run to the next, with the same version of
        // Java: most times a connection error, because it fails before it has an answer, and
        // some times an answer whose body cannot be read. It depends on how much had arrived
        // when it read. The rule is the same in both cases, and this test checks only what
        // does not depend on that: the first one is retried and the second one is not.
        swapiFailsOnce(SWAPI_PEOPLE, aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK));

        Throwable failure = catchThrowable(() -> source.list(PEOPLE, new PageQuery(1, 10)));

        if (failure == null) {
            // It was a connection error: the retry got the recording.
            swapi.verify(2, anyRequestedFor(anyUrl()));
        } else {
            // It was an answer that cannot be read: a failure of the data source, at once.
            assertThat(failure).isInstanceOf(BadGatewayException.class);
            swapi.verify(1, anyRequestedFor(anyUrl()));
        }
    }

    @Test
    @DisplayName("A connection that keeps being cut is a failure after exactly two requests")
    void connectionThatKeepsBeingCutIsAFailureAfterOneRetry() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> source.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);

        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("When nothing listens at the address of SWAPI, the request is sent again after the wait")
    void connectionThatIsRefusedIsRetriedAfterTheWait(CapturedOutput log) {
        // Nobody is there to count the requests: the retry is seen in the log and in the time.
        // The wait is longer here than in the other tests so that it can be told apart from the
        // time that the two refused connections take, which is of a few milliseconds.
        Duration delay = Duration.ofMillis(300);
        SwapiCatalogSource unreachable = new SwapiCatalogSource(
                new SwapiProperties("http://localhost:1/api", Duration.ofSeconds(3), READ_TIMEOUT, 1, delay));

        long start = System.nanoTime();
        assertThatThrownBy(() -> unreachable.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);
        Duration waited = Duration.ofNanos(System.nanoTime() - start);

        assertThat(waited).isGreaterThanOrEqualTo(delay);
        assertThat(log.getAll()).containsOnlyOnce(RETRY_LINE);
    }

    @Test
    @DisplayName("An answer that takes longer than the read timeout is not retried: the wait is not doubled")
    void slowAnswerIsNotRetried() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(recordedPage().withFixedDelay(2000)));

        assertThatThrownBy(() -> sourceWith(1, RETRY_DELAY, SHORT_READ_TIMEOUT).list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(GatewayTimeoutException.class);

        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("An answer that starts in time and stops halfway is a timeout too, and is not retried")
    void answerThatStopsHalfwayIsNotRetried() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(recordedPage().withChunkedDribbleDelay(20, 4000)));

        assertThatThrownBy(() -> sourceWith(1, RETRY_DELAY, SHORT_READ_TIMEOUT).list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(GatewayTimeoutException.class);

        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest(name = "SWAPI answers 200 with: \"{0}\"")
    // An answer that arrived and is not what was expected would be the same the second time. The
    // empty text is an answer without a body.
    @ValueSource(strings = {"this is not JSON", "{\"message\":\"ok\"}", ""})
    @DisplayName("An answer that arrived and cannot be read is not retried")
    void answerThatCannotBeReadIsNotRetried(String body) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON).withBody(body)));

        assertThatThrownBy(() -> source.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);

        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("With zero retries nothing is sent again: the first failure is the answer, although SWAPI would have answered the next request")
    void withZeroRetriesNothingIsSentAgain() {
        swapiFailsOnce(SWAPI_PEOPLE, error(503));

        assertThatThrownBy(() -> sourceWith(0, RETRY_DELAY, READ_TIMEOUT).list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);

        swapi.verify(1, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("With three retries, a failure that stays is a failure after exactly four requests")
    void withThreeRetriesFourRequestsAreSent() {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1).willReturn(error(503)));

        assertThatThrownBy(() -> sourceWith(3, RETRY_DELAY, READ_TIMEOUT).list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);

        swapi.verify(4, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("With three retries, the requests stop as soon as one is answered")
    void retriesStopAsSoonAsSwapiAnswers() {
        swapiFailsOnce(SWAPI_PEOPLE, error(503));

        assertThat(sourceWith(3, RETRY_DELAY, READ_TIMEOUT).list(PEOPLE, new PageQuery(1, 10)).toString())
                .contains("Luke Skywalker");

        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("When the retry fails too, the failure is the one of the retry: a 503 and then an answer that takes too long is a timeout")
    void failureIsTheOneOfTheLastAttempt() {
        // The second answer is the recorded page, held back for longer than the client waits.
        swapiFailsOnceAndThenAnswers(error(503), recordedPage().withFixedDelay(2000));

        assertThatThrownBy(() -> sourceWith(1, RETRY_DELAY, SHORT_READ_TIMEOUT).list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(GatewayTimeoutException.class);

        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("When the retry fails too, the failure is the one of the retry: a 503 and then a 500 is a failure after two requests, and the 500 is not retried")
    void failureOfTheRetryIsNotRetriedIfItIsNotWorthIt() {
        swapiFailsOnceAndThenAnswers(error(503), error(500));

        assertThatThrownBy(() -> sourceWith(3, RETRY_DELAY, READ_TIMEOUT).list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);

        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("The time that tells an answer that was cut for taking too long is counted from each attempt, not from the first one")
    void timeIsMeasuredFromEachAttempt() {
        // The first attempt fails at once and the retry gets, at once too, an answer that cannot
        // be read: a failure of the data source, not a timeout. The wait between the two is
        // longer than the read timeout on purpose. If the time were counted from the first
        // attempt, the second failure would look like an answer that was cut for taking too
        // long, and it would be a timeout.
        Duration readTimeout = Duration.ofSeconds(1);
        Duration delay = Duration.ofMillis(1200);
        swapiFailsOnceAndThenAnswers(error(503),
                aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, JSON).withBody("this is not JSON"));

        assertThatThrownBy(() -> sourceWith(1, delay, readTimeout).list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);

        swapi.verify(2, anyRequestedFor(anyUrl()));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("Each retry leaves one line in the log, with the request that failed")
    void eachRetryLeavesOneLineInTheLog(CapturedOutput log) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1).willReturn(error(503)));

        assertThatThrownBy(() -> sourceWith(3, RETRY_DELAY, READ_TIMEOUT).list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);

        assertThat(log.getAll().lines().filter(line -> line.contains(RETRY_LINE)))
                .hasSize(3)
                .allSatisfy(line -> assertThat(line).contains("WARN").contains("GET /people?page=1&limit=10"))
                .satisfiesExactly(
                        first -> assertThat(first).contains("retry 1 of 3"),
                        second -> assertThat(second).contains("retry 2 of 3"),
                        third -> assertThat(third).contains("retry 3 of 3"));
        // One line and nothing else: neither a stack trace nor the error of the HTTP client,
        // whose text is not written by this application.
        assertThat(log.getAll()).doesNotContain("HttpServerErrorException").doesNotContain("\tat ");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("A request that is not sent again leaves nothing in the log")
    void requestThatIsNotRetriedLeavesNothingInTheLog(CapturedOutput log) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1).willReturn(error(500)));

        assertThatThrownBy(() -> source.list(PEOPLE, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);
        // Nor one that is answered at the first attempt.
        source.find(PEOPLE, new ItemId("1"));

        assertThat(log.getAll()).doesNotContain(RETRY_LINE);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("The line of a retry of a search does not have the text that the client sent")
    void lineOfARetryDoesNotHaveTheTextOfTheSearch(CapturedOutput log) {
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1).willReturn(error(503)));
        NameFilter name = new NameFilter("text-of-the-client");

        assertThatThrownBy(() -> source.search(PEOPLE, name, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);
        assertThatThrownBy(() -> source.searchExpanded(PEOPLE, name, new PageQuery(1, 10)))
                .isInstanceOf(BadGatewayException.class);

        assertThat(log.getAll().lines().filter(line -> line.contains(RETRY_LINE)))
                .hasSize(2)
                .allSatisfy(line -> assertThat(line).contains("GET /people?name=..."));
        assertThat(log.getAll()).doesNotContain("text-of-the-client");
    }

    /** The client of SWAPI at the stub, with its retries and its read timeout. */
    private SwapiCatalogSource sourceWith(int maxRetries, Duration retryDelay, Duration readTimeout) {
        return new SwapiCatalogSource(
                new SwapiProperties(baseUrl, Duration.ofSeconds(3), readTimeout, maxRetries, retryDelay));
    }

    /** An error answer of SWAPI with a status. */
    private static ResponseDefinitionBuilder error(int status) {
        return aResponse().withStatus(status)
                .withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                .withBody("{\"message\":\"it failed\"}");
    }

    /** The recorded first page of people, for the tests that hold it back. */
    private static ResponseDefinitionBuilder recordedPage() {
        return aResponse().withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, JSON)
                .withBodyFile("people-page-1-limit-10.json");
    }

    /**
     * Tells the stub to fail the first request to a path and to answer the following ones with
     * its recording: the failure is ahead of the recording only until it is served once.
     */
    private void swapiFailsOnce(String path, ResponseDefinitionBuilder failure) {
        swapi.stubFor(get(urlPathEqualTo(path)).atPriority(1)
                .inScenario("SWAPI fails once")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(failure)
                .willSetStateTo("after the first request"));
    }

    /** Tells the stub what to answer to the first request for the list of people, and what to the following ones. */
    private void swapiFailsOnceAndThenAnswers(ResponseDefinitionBuilder first, ResponseDefinitionBuilder then) {
        swapiFailsOnce(SWAPI_PEOPLE, first);
        swapi.stubFor(get(urlPathEqualTo(SWAPI_PEOPLE)).atPriority(1)
                .inScenario("SWAPI fails once")
                .whenScenarioStateIs("after the first request")
                .willReturn(then));
    }

}
