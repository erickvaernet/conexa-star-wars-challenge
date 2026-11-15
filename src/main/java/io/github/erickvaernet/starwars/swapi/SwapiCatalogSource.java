package io.github.erickvaernet.starwars.swapi;

import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.CatalogItem;
import io.github.erickvaernet.starwars.catalog.CatalogSource;
import io.github.erickvaernet.starwars.catalog.ItemId;
import io.github.erickvaernet.starwars.catalog.Listing;
import io.github.erickvaernet.starwars.catalog.PageQuery;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import io.github.erickvaernet.starwars.shared.GatewayTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The data source of the catalog: it asks SWAPI over HTTP and translates what it answers.
 *
 * <p>It is the only class that talks to SWAPI. Every failure leaves here as one of two errors,
 * so the rest of the application does not need to know how the HTTP client fails.
 */
@Component
class SwapiCatalogSource implements CatalogSource {

    /**
     * How the answer for one element of each entity is read, by the name of the entity in SWAPI.
     *
     * <p>The outside of the answer is the same for every entity; the data inside is not. An
     * entity that is added to the catalog adds its line here.
     */
    private static final Map<String, ParameterizedTypeReference<? extends SwapiItemResponse<?>>> ITEM_FORMATS =
            Map.of("people", new ParameterizedTypeReference<SwapiItemResponse<SwapiPerson>>() { });

    private final RestClient swapi;

    SwapiCatalogSource(SwapiProperties properties) {
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
    public Listing list(CatalogEntity entity, PageQuery query) {
        String request = "GET /" + entity.sourceName() + "?page=" + query.page() + "&limit=" + query.size();
        try {
            SwapiListResponse response = swapi.get()
                    .uri("/{entity}?page={page}&limit={limit}", entity.sourceName(), query.page(), query.size())
                    .retrieve()
                    .body(SwapiListResponse.class);
            if (response == null) {
                throw new BadGatewayException("SWAPI answered " + request + " without a body", null);
            }
            return response.toListing();
        } catch (RestClientException error) {
            throw failure(request, error);
        }
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
        ParameterizedTypeReference<? extends SwapiItemResponse<?>> format = ITEM_FORMATS.get(entity.sourceName());
        if (format == null) {
            throw new IllegalStateException("No format is declared for one element of " + entity.sourceName());
        }
        String request = "GET /" + entity.sourceName() + "/" + id.value();
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
            throw failure(request, error);
        }
    }

    /** Turns a failure of the HTTP client into one of the two errors of the data source. */
    private static RuntimeException failure(String request, RestClientException error) {
        if (error instanceof ResourceAccessException) {
            // The request could not be completed. Running out of time, to connect or to read, is
            // told apart from the rest: it is the only case that is answered with a 504.
            if (error.getCause() instanceof HttpTimeoutException) {
                return new GatewayTimeoutException("SWAPI did not answer " + request + " in time", error);
            }
            return new BadGatewayException("SWAPI could not be reached for " + request, error);
        }
        // An error status, such as 500 or 429, or a body that is not the JSON that was expected.
        return new BadGatewayException("SWAPI answered " + request + " with an error or an unreadable body", error);
    }

}
