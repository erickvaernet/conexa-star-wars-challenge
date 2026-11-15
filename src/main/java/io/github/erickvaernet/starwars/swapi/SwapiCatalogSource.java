package io.github.erickvaernet.starwars.swapi;

import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.CatalogSource;
import io.github.erickvaernet.starwars.catalog.Listing;
import io.github.erickvaernet.starwars.catalog.PageQuery;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import io.github.erickvaernet.starwars.shared.GatewayTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
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
        } catch (ResourceAccessException error) {
            // The request could not be completed. Running out of time, to connect or to read, is
            // told apart from the rest: it is the only case that is answered with a 504.
            if (error.getCause() instanceof HttpTimeoutException) {
                throw new GatewayTimeoutException("SWAPI did not answer " + request + " in time", error);
            }
            throw new BadGatewayException("SWAPI could not be reached for " + request, error);
        } catch (RestClientException error) {
            // An error status, such as 500 or 429, or a body that is not the JSON that was expected.
            throw new BadGatewayException("SWAPI answered " + request + " with an error or an unreadable body", error);
        }
    }

}
