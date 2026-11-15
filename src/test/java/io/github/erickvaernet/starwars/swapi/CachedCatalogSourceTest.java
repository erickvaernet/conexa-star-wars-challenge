package io.github.erickvaernet.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.CatalogItem;
import io.github.erickvaernet.starwars.catalog.CatalogSource;
import io.github.erickvaernet.starwars.catalog.ItemId;
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

    private static final ItemId LUKE = new ItemId("1");

    /** The fake SWAPI has no element with this identifier, whatever the entity. */
    private static final ItemId NOBODY = new ItemId("9999");

    /**
     * Every request that reached the fake SWAPI, in order: {@code people?page=1&limit=10} for a
     * list and {@code people/1} for one element.
     */
    private final List<String> requestsToSwapi = new ArrayList<>();

    private final ManualClock clock = new ManualClock();

    private final Cache<Object, Object> cache = new SwapiCacheConfiguration()
            .swapiCache(new SwapiCacheProperties(ONE_HOUR, MAX_SIZE), Optional.of(clock));

    private final CachedCatalogSource source = new CachedCatalogSource(new FakeSwapi(null), cache);

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

    @Test
    @DisplayName("A repeated request for one element is answered with what was stored, without asking SWAPI again")
    void repeatedRequestForAnElementIsAnsweredFromTheCache() {
        Optional<CatalogItem> first = source.find(PEOPLE, LUKE);
        Optional<CatalogItem> second = source.find(PEOPLE, LUKE);

        assertThat(second).isEqualTo(first).contains(itemOf("people/1"));
        assertThat(requestsToSwapi).containsExactly("people/1");
    }

    @Test
    @DisplayName("Another identifier or another entity is another element: each one is asked to SWAPI")
    void identifierAndEntityTellElementsApart() {
        source.find(PEOPLE, LUKE);
        Optional<CatalogItem> otherIdentifier = source.find(PEOPLE, new ItemId("2"));
        Optional<CatalogItem> otherEntity = source.find(STARSHIPS, LUKE);

        assertThat(requestsToSwapi).containsExactly("people/1", "people/2", "starships/1");
        assertThat(otherIdentifier).contains(itemOf("people/2"));
        assertThat(otherEntity).contains(itemOf("starships/1"));
    }

    @Test
    @DisplayName("An element and a list are never taken for each other, although both are kept in the same cache")
    void elementAndListAreNotMixedUp() {
        Listing list = source.list(PEOPLE, new PageQuery(1, 10));
        Optional<CatalogItem> element = source.find(PEOPLE, LUKE);

        assertThat(requestsToSwapi).containsExactly("people?page=1&limit=10", "people/1");
        assertThat(source.list(PEOPLE, new PageQuery(1, 10))).isEqualTo(list);
        assertThat(source.find(PEOPLE, LUKE)).isEqualTo(element);
        assertThat(requestsToSwapi).hasSize(2);
    }

    @Test
    @DisplayName("An element that does not exist is not stored: every request for it asks SWAPI again")
    void elementThatDoesNotExistIsNotStored() {
        assertThat(source.find(PEOPLE, NOBODY)).isEmpty();
        assertThat(cache.estimatedSize()).isZero();

        assertThat(source.find(PEOPLE, NOBODY)).isEmpty();
        assertThat(requestsToSwapi).containsExactly("people/9999", "people/9999");
        assertThat(cache.estimatedSize()).isZero();
    }

    @Test
    @DisplayName("An error of SWAPI for one element is not stored either")
    void errorOfSwapiForAnElementIsNotStored() {
        BadGatewayException failure = new BadGatewayException("SWAPI answered with an error", null);
        CachedCatalogSource failingOnce = new CachedCatalogSource(failingTheFirstTimeWith(failure), cache);

        assertThatThrownBy(() -> failingOnce.find(PEOPLE, LUKE)).isSameAs(failure);
        assertThat(cache.estimatedSize()).isZero();

        assertThat(failingOnce.find(PEOPLE, LUKE)).contains(itemOf("people/1"));
        assertThat(requestsToSwapi).hasSize(2);
    }

    @Test
    @DisplayName("A stored element expires like any other answer")
    void storedElementExpires() {
        source.find(PEOPLE, LUKE);

        clock.advance(ONE_HOUR.minusSeconds(1));
        source.find(PEOPLE, LUKE);
        assertThat(requestsToSwapi).hasSize(1);

        clock.advance(Duration.ofSeconds(2));
        source.find(PEOPLE, LUKE);
        assertThat(requestsToSwapi).hasSize(2);
    }

    /** A fake SWAPI that fails the first time it is asked, and answers from then on. */
    private CatalogSource failingTheFirstTimeWith(RuntimeException failure) {
        return new FakeSwapi(failure);
    }

    /**
     * The fake SWAPI: it notes each request down and answers with a list or an element that
     * names it.
     */
    private final class FakeSwapi implements CatalogSource {

        /** What the first request fails with, or {@code null} if no request fails. */
        private final RuntimeException firstFailure;

        private FakeSwapi(RuntimeException firstFailure) {
            this.firstFailure = firstFailure;
        }

        @Override
        public Listing list(CatalogEntity entity, PageQuery query) {
            String request = entity.sourceName() + "?page=" + query.page() + "&limit=" + query.size();
            note(request);
            return listingOf(request);
        }

        @Override
        public Optional<CatalogItem> find(CatalogEntity entity, ItemId id) {
            String request = entity.sourceName() + "/" + id.value();
            note(request);
            return id.equals(NOBODY) ? Optional.empty() : Optional.of(itemOf(request));
        }

        private void note(String request) {
            requestsToSwapi.add(request);
            if (firstFailure != null && requestsToSwapi.size() == 1) {
                throw firstFailure;
            }
        }

    }

    /** A list with one element named after the request, so that every request has its own answer. */
    private static Listing listingOf(String request) {
        return new Listing.OnePage(List.of(new ItemSummary("1", request)), 82);
    }

    /** An element named after the request, for the same reason. */
    private static CatalogItem itemOf(String request) {
        return new FakeItem(request);
    }

    /** A record, so that two elements named the same are equal. */
    private record FakeItem(String name) implements CatalogItem {

        @Override
        public ItemSummary summary() {
            return new ItemSummary("1", name);
        }

    }

}
