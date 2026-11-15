package io.github.erickvaernet.starwars.swapi;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.CatalogItem;
import io.github.erickvaernet.starwars.catalog.CatalogSource;
import io.github.erickvaernet.starwars.catalog.ItemId;
import io.github.erickvaernet.starwars.catalog.Listing;
import io.github.erickvaernet.starwars.catalog.PageQuery;
import java.util.Optional;

/**
 * The data source the catalog actually gets: SWAPI, with its answers kept for a while.
 *
 * <p>It wraps the client of SWAPI and has the same interface, so the catalog does not know that
 * a cache exists. A repeated request is answered with what was stored and never reaches SWAPI,
 * which slows down and cuts off whoever asks too much.
 *
 * <p>Only an answer that arrived well is stored. When SWAPI fails, the client throws, and the
 * exception leaves this class before anything is stored: the next request asks SWAPI again. An
 * element that does not exist is not stored either.
 *
 * <p>{@link SwapiCacheConfiguration} builds it around the client and makes it the source that
 * the rest of the application receives.
 */
class CachedCatalogSource implements CatalogSource {

    private final CatalogSource swapi;

    private final Cache<Object, Object> answers;

    /**
     * @param swapi   the source whose answers are kept: in the application, the client of SWAPI
     * @param answers the cache, already built with its expiry and its maximum size
     */
    CachedCatalogSource(CatalogSource swapi, Cache<Object, Object> answers) {
        this.swapi = swapi;
        this.answers = answers;
    }

    /**
     * Returns the stored answer for that page, or asks SWAPI and stores what it answers.
     *
     * <p>Looking up and storing are two separate steps, on purpose. The library can also do both
     * in one, but then it waits for SWAPI while it holds a lock of the cache, and a virtual
     * thread that waits inside such a lock keeps its system thread busy. The price is that two
     * identical requests arriving together on an empty cache both go to SWAPI.
     */
    @Override
    public Listing list(CatalogEntity entity, PageQuery query) {
        PageKey key = new PageKey(entity, query);
        if (answers.getIfPresent(key) instanceof Listing stored) {
            return stored;
        }
        Listing fresh = swapi.list(entity, query);
        answers.put(key, fresh);
        return fresh;
    }

    /**
     * Returns the stored element, or asks SWAPI for it and stores it if it exists.
     *
     * <p>The filter by identifier and the detail both end here, with the same key: asking for
     * the detail of an element and then filtering its list by that identifier is one request to
     * SWAPI.
     *
     * <p>That an element does not exist is not stored. Hardly anyone asks twice for the same
     * identifier that does not exist, so those entries would only take the place of useful ones.
     */
    @Override
    public Optional<CatalogItem> find(CatalogEntity entity, ItemId id) {
        ItemKey key = new ItemKey(entity, id);
        if (answers.getIfPresent(key) instanceof CatalogItem stored) {
            return Optional.of(stored);
        }
        Optional<CatalogItem> fresh = swapi.find(entity, id);
        fresh.ifPresent(item -> answers.put(key, item));
        return fresh;
    }

    /**
     * What makes two requests for a list "the same request": the entity, the page and the size.
     *
     * <p>Being a record, two keys are equal when those three are. Another kind of request gets
     * a record of its own, and records of different types are never equal: their answers cannot
     * be mixed up.
     */
    private record PageKey(CatalogEntity entity, PageQuery query) {
    }

    /** What makes two requests for one element "the same request": the entity and the identifier. */
    private record ItemKey(CatalogEntity entity, ItemId id) {
    }

}
