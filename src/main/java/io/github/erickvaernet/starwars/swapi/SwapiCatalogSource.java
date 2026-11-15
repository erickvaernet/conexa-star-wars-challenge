package io.github.erickvaernet.starwars.swapi;

import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.CatalogItem;
import io.github.erickvaernet.starwars.catalog.CatalogSource;
import io.github.erickvaernet.starwars.catalog.ItemId;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.Listing;
import io.github.erickvaernet.starwars.catalog.NameFilter;
import io.github.erickvaernet.starwars.catalog.PageQuery;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import io.github.erickvaernet.starwars.shared.GatewayTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The data source of the catalog: it asks SWAPI over HTTP and translates what it answers.
 *
 * <p>It is the only class that talks to SWAPI. Every failure leaves here as one of two errors,
 * so the rest of the application does not need to know how the HTTP client fails.
 */
@Component
class SwapiCatalogSource implements CatalogSource {

    /**
     * How the data of one element of each entity is read, by the name of the entity in SWAPI.
     *
     * <p>The outside of an answer is the same for every entity; the data of each element is not.
     * The same table serves the element that is asked for alone and each element of a list that
     * brings the complete data. An entity that is added to the catalog adds its line here.
     */
    private static final Map<String, Class<? extends SwapiItemData>> ITEM_FORMATS = Map.of(
            "people", SwapiPerson.class,
            "films", SwapiFilm.class,
            "starships", SwapiStarship.class,
            "vehicles", SwapiVehicle.class);

    /** How the answer to a list of summaries is read. It is the same for every entity. */
    private static final ParameterizedTypeReference<SwapiListResponse> SUMMARIES =
            new ParameterizedTypeReference<>() { };

    /** What is added to the request of a list or of a search to get the complete data of each element. */
    private static final String EXPANDED = "&expanded=true";

    /** The characters that mean something in a pattern: {@code \ ^ $ . | ? * + ( ) [ ] { }}. */
    private static final Pattern PATTERN_CHARACTERS = Pattern.compile("[\\\\^$.|?*+()\\[\\]{}]");

    private final RestClient swapi;

    /** The longest wait for an answer, kept to tell an answer that was cut for taking too long. */
    private final Duration readTimeout;

    SwapiCatalogSource(SwapiProperties properties) {
        this.readTimeout = properties.readTimeout();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(httpClient);
        requests.setReadTimeout(properties.readTimeout());
        this.swapi = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requests)
                .build();
    }

    /**
     * Asks SWAPI for one page of a list.
     *
     * <p>The page and the size are always sent, whatever the entity. SWAPI follows them in some
     * lists and ignores them in others, and that can change without notice.
     */
    @Override
    public Listing<ItemSummary> list(CatalogEntity entity, PageQuery query) {
        return summaries(entity, pageOf(entity, query, ""));
    }

    /**
     * Asks SWAPI for one page of a list with the complete data of each element.
     *
     * <p>It is the request of a list with {@code expanded=true}, which is what SWAPI has for
     * this: one request for the whole page, instead of one per element. It is sent whatever the
     * entity. A list that SWAPI already answers whole and with all the data ignores it.
     *
     * @throws IllegalStateException if no format was declared for the entity, which is a mistake
     *     in the code and not something a request can cause
     */
    @Override
    public Listing<CatalogItem> listExpanded(CatalogEntity entity, PageQuery query) {
        return completeItems(entity, pageOf(entity, query, EXPANDED));
    }

    /**
     * Asks SWAPI for the elements whose name contains a text.
     *
     * <p>It is the request of a list with one more parameter: the field that the entity
     * declares, with the text. The page and the size are sent here too; today SWAPI ignores them
     * in a search and answers every match at once.
     */
    @Override
    public Listing<ItemSummary> search(CatalogEntity entity, NameFilter name, PageQuery query) {
        return summaries(entity, matchesOf(entity, name, query, ""));
    }

    /**
     * Asks SWAPI for the elements whose name contains a text, with the complete data of each one.
     *
     * <p>It is the request of a search with {@code expanded=true}. Today SWAPI ignores it there,
     * because a search already brings all the data; it is sent all the same, so that the request
     * does not depend on what SWAPI does today.
     *
     * @throws IllegalStateException if no format was declared for the entity, which is a mistake
     *     in the code and not something a request can cause
     */
    @Override
    public Listing<CatalogItem> searchExpanded(CatalogEntity entity, NameFilter name, PageQuery query) {
        return completeItems(entity, matchesOf(entity, name, query, EXPANDED));
    }

    /**
     * A request to SWAPI that is ready to be sent, with the text that names it in an error.
     *
     * @param description how the request is named in the log, without anything the client sent
     * @param call        the request itself
     */
    private record Request(String description, RestClient.RequestHeadersSpec<?> call) {
    }

    /**
     * The request for one page of a list.
     *
     * @param more what is added at the end of the request: nothing, or {@link #EXPANDED}
     */
    private Request pageOf(CatalogEntity entity, PageQuery query, String more) {
        return new Request(
                "GET /" + entity.sourceName() + "?page=" + query.page() + "&limit=" + query.size() + more,
                swapi.get().uri("/{entity}?page={page}&limit={limit}" + more,
                        entity.sourceName(), query.page(), query.size()));
    }

    /**
     * The request for the elements whose name contains a text.
     *
     * @param more what is added at the end of the request: nothing, or {@link #EXPANDED}
     */
    private Request matchesOf(CatalogEntity entity, NameFilter name, PageQuery query, String more) {
        return new Request(
                // Without the text: it comes from the client and may have anything in it, line
                // breaks included, and this description ends up in the log.
                "GET /" + entity.sourceName() + "?" + entity.searchField() + "=..."
                        + "&page=" + query.page() + "&limit=" + query.size() + more,
                swapi.get().uri("/{entity}?{field}={text}&page={page}&limit={limit}" + more,
                        entity.sourceName(), entity.searchField(), literal(name.value()),
                        query.page(), query.size()));
    }

    /** Sends a request for a list or for a search, and reads the summary of each element. */
    private Listing<ItemSummary> summaries(CatalogEntity entity, Request request) {
        return read(request, SUMMARIES).toListing(entity.searchField());
    }

    /**
     * Sends a request for a list or for a search, and reads each element with its complete data,
     * with the translation of its entity.
     */
    private Listing<CatalogItem> completeItems(CatalogEntity entity, Request request) {
        // Before the request is sent: without a format, its answer could not be read.
        ParameterizedTypeReference<SwapiExpandedListResponse<?>> format =
                formatOf(SwapiExpandedListResponse.class, entity);
        return read(request, format).toListing();
    }

    /** Sends a request for a list or for a search, and reads its answer, in whichever form it came. */
    private <R> R read(Request request, ParameterizedTypeReference<R> format) {
        long startedAt = System.nanoTime();
        try {
            R response = request.call().retrieve().body(format);
            if (response == null) {
                throw new BadGatewayException("SWAPI answered " + request.description() + " without a body", null);
            }
            return response;
        } catch (RestClientException error) {
            throw failure(request.description(), error, startedAt);
        }
    }

    /**
     * The answer of SWAPI to read for an entity: the outside that is the same for all of them,
     * around the data that the table declares for that one.
     *
     * @param answer the outside of the answer: the one for an element, or the one for a list
     *               with the complete data
     * @throws IllegalStateException if no format was declared for the entity
     */
    private static <R> ParameterizedTypeReference<R> formatOf(Class<? super R> answer, CatalogEntity entity) {
        Class<? extends SwapiItemData> data = ITEM_FORMATS.get(entity.sourceName());
        if (data == null) {
            throw new IllegalStateException("No format is declared for one element of " + entity.sourceName());
        }
        return ParameterizedTypeReference.forType(ResolvableType.forClassWithGenerics(answer, data).getType());
    }

    /**
     * Writes a text so that SWAPI searches it as it is.
     *
     * <p>SWAPI reads the text of a search as a pattern (a regular expression): a point matches
     * any character, and an opening parenthesis alone is an error. A backslash before each
     * character that means something in a pattern makes SWAPI look for that character itself.
     */
    static String literal(String text) {
        return PATTERN_CHARACTERS.matcher(text).replaceAll("\\\\$0");
    }

    /**
     * Asks SWAPI for one element.
     *
     * <p>When SWAPI answers 404, the element does not exist: that is a normal answer, and the
     * result is empty. Any other error status is a failure, like in a list.
     *
     * @throws IllegalStateException if no format was declared for the entity, which is a mistake
     *     in the code and not something a request can cause
     */
    @Override
    public Optional<CatalogItem> find(CatalogEntity entity, ItemId id) {
        ParameterizedTypeReference<SwapiItemResponse<?>> format = formatOf(SwapiItemResponse.class, entity);
        String request = "GET /" + entity.sourceName() + "/" + id.value();
        long startedAt = System.nanoTime();
        try {
            SwapiItemResponse<?> response = swapi.get()
                    .uri("/{entity}/{id}", entity.sourceName(), id.value())
                    .retrieve()
                    .body(format);
            if (response == null) {
                throw new BadGatewayException("SWAPI answered " + request + " without a body", null);
            }
            return Optional.of(response.toItem());
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (RestClientException error) {
            throw failure(request, error, startedAt);
        }
    }

    /**
     * Turns a failure of the HTTP client into one of the two errors of the data source.
     *
     * <p>Running out of time is the only case that is answered with a 504, and it arrives in two
     * ways. If SWAPI did not connect or did not begin to answer in time, the HTTP client says
     * so. If SWAPI began to answer and stopped halfway, the client only says that the answer
     * could not be read, exactly as it does for an answer that is not JSON: what tells the two
     * apart is how long the request had been waiting.
     *
     * @param startedAt when the request was sent, as {@link System#nanoTime()} gives it
     */
    private RuntimeException failure(String request, RestClientException error, long startedAt) {
        if (error instanceof ResourceAccessException && error.getCause() instanceof HttpTimeoutException) {
            return new GatewayTimeoutException("SWAPI did not answer " + request + " in time", error);
        }
        // An error status, such as 500 or 429, arrives with the start of the answer: it is never
        // an answer that was cut, however long it took.
        boolean cutForTakingTooLong = !(error instanceof RestClientResponseException)
                && Duration.ofNanos(System.nanoTime() - startedAt).compareTo(readTimeout) >= 0;
        if (cutForTakingTooLong) {
            return new GatewayTimeoutException(
                    "SWAPI began to answer " + request + " and did not finish in time", error);
        }
        if (error instanceof ResourceAccessException) {
            return new BadGatewayException("SWAPI could not be reached for " + request, error);
        }
        // An error status, or a body that is not the JSON that was expected.
        return new BadGatewayException("SWAPI answered " + request + " with an error or an unreadable body", error);
    }

}
