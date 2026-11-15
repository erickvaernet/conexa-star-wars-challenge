package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.CatalogSource;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.Listing;
import io.github.erickvaernet.starwars.catalog.PageQuery;
import io.github.erickvaernet.starwars.shared.BadGatewayException;
import io.github.erickvaernet.starwars.shared.GatewayTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks what the cache keeps, for how long and how much: which requests count as the same one,
 * when an answer expires, that a failure is never kept, and that the cache stops growing.
 *
 * <p>SWAPI is a fake source written here, which remembers every request it gets. The cache is the
 * real one, built as the application builds it, with a clock that only moves when a test tells
 * it to. No test needs HTTP, Spring or Docker, and none waits.
 */
class CachedCatalogSourceTest {

    private static final CatalogEntity PEOPLE = new CatalogEntity("people");

    private static final CatalogEntity STARSHIPS = new CatalogEntity("starships");

    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private static final int MAX_SIZE = 10;

    /** Every request that reached the fake SWAPI, in order: {@code people?page=1&limit=10}. */
    private final List<String> requestsToSwapi = new ArrayList<>();

    private final ManualClock clock = new ManualClock();

    private final Cache<Object, Object> cache = new SwapiCacheConfiguration()
            .swapiCache(new SwapiCacheProperties(ONE_HOUR, MAX_SIZE), Optional.of(clock));

    private final CachedCatalogSource source = new CachedCatalogSource(this::answerOfSwapi, cache);

    @Test
    @DisplayName("A repeated request is answered with what was stored, without asking SWAPI again")
    void repeatedRequestIsAnsweredFromTheCache() {
        Listing first = source.list(PEOPLE, new PageQuery(1, 10));
        Listing second = source.list(PEOPLE, new PageQuery(1, 10));

        assertThat(second).isEqualTo(first).isEqualTo(listingOf("people?page=1&limit=10"));
        assertThat(requestsToSwapi).containsExactly("people?page=1&limit=10");
    }

    @Test
    @DisplayName("Another page, another size or another entity is another request: each one is asked to SWAPI")
    void pageSizeAndEntityTellRequestsApart() {
        source.list(PEOPLE, new PageQuery(1, 10));
        Listing otherPage = source.list(PEOPLE, new PageQuery(2, 10));
        Listing otherSize = source.list(PEOPLE, new PageQuery(1, 5));
        Listing otherEntity = source.list(STARSHIPS, new PageQuery(1, 10));

        assertThat(requestsToSwapi).containsExactly(
                "people?page=1&limit=10",
                "people?page=2&limit=10",
                "people?page=1&limit=5",
                "starships?page=1&limit=10");
        // And each one gets its own answer, not the one of a neighbour.
        assertThat(otherPage).isEqualTo(listingOf("people?page=2&limit=10"));
        assertThat(otherSize).isEqualTo(listingOf("people?page=1&limit=5"));
        assertThat(otherEntity).isEqualTo(listingOf("starships?page=1&limit=10"));
    }

    @Test
    @DisplayName("An answer is kept until its expiry, and after it SWAPI is asked again")
    void answerExpires() {
        source.list(PEOPLE, new PageQuery(1, 10));

        clock.advance(ONE_HOUR.minusSeconds(1));
        source.list(PEOPLE, new PageQuery(1, 10));
        assertThat(requestsToSwapi).hasSize(1);

        clock.advance(Duration.ofSeconds(2));
        source.list(PEOPLE, new PageQuery(1, 10));
        assertThat(requestsToSwapi).hasSize(2);
    }

    @Test
    @DisplayName("The expiry counts from when the answer was stored: reading it does not keep it alive")
    void readingAnAnswerDoesNotPostponeItsExpiry() {
        source.list(PEOPLE, new PageQuery(1, 10));

        clock.advance(Duration.ofMinutes(40));
        source.list(PEOPLE, new PageQuery(1, 10));
        assertThat(requestsToSwapi).hasSize(1);

        // 80 minutes after it was stored, although only 40 after it was last read.
        clock.advance(Duration.ofMinutes(40));
        source.list(PEOPLE, new PageQuery(1, 10));
        assertThat(requestsToSwapi).hasSize(2);
    }

    @Test
    @DisplayName("An error of SWAPI is not stored: it reaches the caller, and the next request asks SWAPI again")
    void errorOfSwapiIsNotStored() {
        BadGatewayException failure = new BadGatewayException("SWAPI answered with an error", null);
        CachedCatalogSource failingOnce = new CachedCatalogSource(failingTheFirstTimeWith(failure), cache);

        assertThatThrownBy(() -> failingOnce.list(PEOPLE, new PageQuery(1, 10))).isSameAs(failure);
        assertThat(cache.estimatedSize()).isZero();

        assertThat(failingOnce.list(PEOPLE, new PageQuery(1, 10))).isEqualTo(listingOf("people?page=1&limit=10"));
        assertThat(requestsToSwapi).hasSize(2);
    }

    @Test
    @DisplayName("A timeout of SWAPI is not stored either")
    void timeoutOfSwapiIsNotStored() {
        GatewayTimeoutException timeout = new GatewayTimeoutException("SWAPI did not answer in time", null);
        CachedCatalogSource failingOnce = new CachedCatalogSource(failingTheFirstTimeWith(timeout), cache);

        assertThatThrownBy(() -> failingOnce.list(PEOPLE, new PageQuery(1, 10))).isSameAs(timeout);
        assertThat(cache.estimatedSize()).isZero();

        assertThat(failingOnce.list(PEOPLE, new PageQuery(1, 10))).isEqualTo(listingOf("people?page=1&limit=10"));
        assertThat(requestsToSwapi).hasSize(2);
    }

    @Test
    @DisplayName("The cache never keeps more answers than its maximum size, however many requests arrive")
    void cacheDoesNotGrowBeyondItsMaximumSize() {
        int requests = MAX_SIZE * 10;
        for (int page = 1; page <= requests; page++) {
            source.list(PEOPLE, new PageQuery(page, 10));
        }
        // The library discards right after a write, not during it. This makes it finish.
        cache.cleanUp();

        assertThat(requestsToSwapi).hasSize(requests);
        assertThat(cache.estimatedSize()).isEqualTo(MAX_SIZE);
    }

    /** The fake SWAPI: it notes the request down and answers with a list that names it. */
    private Listing answerOfSwapi(CatalogEntity entity, PageQuery query) {
        String request = entity.sourceName() + "?page=" + query.page() + "&limit=" + query.size();
        requestsToSwapi.add(request);
        return listingOf(request);
    }

    /** A fake SWAPI that fails the first time it is asked, and answers from then on. */
    private CatalogSource failingTheFirstTimeWith(RuntimeException failure) {
        return (entity, query) -> {
            Listing answer = answerOfSwapi(entity, query);
            if (requestsToSwapi.size() == 1) {
                throw failure;
            }
            return answer;
        };
    }

    /** A list with one element named after the request, so that every request has its own answer. */
    private static Listing listingOf(String request) {
        return new Listing.OnePage(List.of(new ItemSummary("1", request)), 82);
    }

}
