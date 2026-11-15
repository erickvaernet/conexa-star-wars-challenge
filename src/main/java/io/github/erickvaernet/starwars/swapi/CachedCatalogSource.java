package io.github.erickvaernet.starwars.swapi;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.erickvaernet.starwars.catalog.CatalogEntity;
import io.github.erickvaernet.starwars.catalog.CatalogItem;
import io.github.erickvaernet.starwars.catalog.CatalogSource;
import io.github.erickvaernet.starwars.catalog.ItemId;
import io.github.erickvaernet.starwars.catalog.ItemSummary;
import io.github.erickvaernet.starwars.catalog.Listing;
import io.github.erickvaernet.starwars.catalog.NameFilter;
import io.github.erickvaernet.starwars.catalog.PageQuery;
import java.util.Optional;
import java.util.function.Supplier;

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
 * <p>A list or a search with the complete data of its elements is stored apart from the same one
 * with their summaries, and each of its elements is stored too, as if it had been asked for
 * alone.
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

    /** Returns the stored answer for that list, or asks SWAPI and stores what it answers. */
    @Override
    public Listing<ItemSummary> list(CatalogEntity entity, PageQuery query) {
        return listing(entity, null, query, false, () -> swapi.list(entity, query));
    }

    /**
     * Returns the stored answer for that list with the complete data of its elements, or asks
     * SWAPI and stores what it answers.
     *
     * <p>It is stored apart from the answer with the summaries of the same list: one does not
     * serve the other, and asking for both is two requests to SWAPI. Each element that arrives
     * is stored on its own as well.
     */
    @Override
    public Listing<CatalogItem> listExpanded(CatalogEntity entity, PageQuery query) {
        return listing(entity, null, query, true, () -> storingEachItem(entity, swapi.listExpanded(entity, query)));
    }

    /**
     * Returns the stored answer for that search, or asks SWAPI and stores what it answers.
     *
     * <p>A search without matches is stored like any other: SWAPI answers it as correct, with an
     * empty list, and it is as worth not repeating as one with matches.
     */
    @Override
    public Listing<ItemSummary> search(CatalogEntity entity, NameFilter name, PageQuery query) {
        return listing(entity, name, query, false, () -> swapi.search(entity, name, query));
    }

    /**
     * Returns the stored answer for that search with the complete data of its matches, or asks
     * SWAPI and stores what it answers, and each of its elements on its own.
     */
    @Override
    public Listing<CatalogItem> searchExpanded(CatalogEntity entity, NameFilter name, PageQuery query) {
        return listing(
                entity, name, query, true, () -> storingEachItem(entity, swapi.searchExpanded(entity, name, query)));
    }

    /**
     * The same steps for a list and for a search, which is a list with a text to look for, and
     * for both with the summaries of the elements or with their complete data.
     *
     * <p>How an answer is stored depends on how SWAPI answered, never on which entity it is.
     * When SWAPI ignored the page and the size and returned everything, that answer serves any
     * page: it is stored under a key without page nor size, and looked up first. So walking the
     * pages of a search, or of a list that SWAPI does not cut, is a single request to SWAPI.
     * When SWAPI cut the page, the answer is stored under the key of that page.
     *
     * <p>Looking up and storing are two separate steps, on purpose. The library can also do both
     * in one, but then it waits for SWAPI while it holds a lock of the cache, and a virtual
     * thread that waits inside such a lock keeps its system thread busy. The price is that two
     * identical requests arriving together on an empty cache both go to SWAPI.
     *
     * @param name     the text of the search, or {@code null} for a list without it
     * @param expanded whether the answer has the complete data of each element. It is part of
     *                 the two keys, so an answer of one kind is never returned for the other
     * @param askSwapi the request to make if nothing is stored
     */
    private <T> Listing<T> listing(
            CatalogEntity entity, NameFilter name, PageQuery query, boolean expanded, Supplier<Listing<T>> askSwapi) {
        WholeKey wholeKey = new WholeKey(entity, name, expanded);
        Listing<T> wholeAnswer = stored(wholeKey);
        if (wholeAnswer != null) {
            return wholeAnswer;
        }
        PageKey pageKey = new PageKey(entity, name, query, expanded);
        Listing<T> pageAnswer = stored(pageKey);
        if (pageAnswer != null) {
            return pageAnswer;
        }
        Listing<T> fresh = askSwapi.get();
        answers.put(fresh instanceof Listing.Complete ? wholeKey : pageKey, fresh);
        return fresh;
    }

    /**
     * The answer stored under the key of a list or of a search, or {@code null} if there is none.
     *
     * <p>The cache holds every kind of answer as a plain object, so the type of the elements is
     * not checked by the compiler here. The key is what guarantees it: it says whether the
     * answer has summaries or complete elements, and an answer is only ever stored under the key
     * that was built for its own request.
     */
    @SuppressWarnings("unchecked")
    private <T> Listing<T> stored(Object key) {
        return (Listing<T>) answers.getIfPresent(key);
    }

    /**
     * Stores each element of an answer that brings the complete data under the key of that
     * element alone, the one {@link #find} looks up: after such a list, the detail of any of its
     * elements, and the list filtered by its identifier, do not go back to SWAPI.
     *
     * <p>It is done only when the answer arrives from SWAPI, so each element expires with the
     * answer that brought it. An element whose identifier does not have the form of one is left
     * out: it cannot be asked for by its identifier, so nothing would ever read that entry.
     *
     * @return the same answer, to be stored and returned
     */
    private Listing<CatalogItem> storingEachItem(CatalogEntity entity, Listing<CatalogItem> fresh) {
        for (CatalogItem item : fresh.items()) {
            String id = item.summary().id();
            if (ItemId.isValid(id)) {
                answers.put(new ItemKey(entity, new ItemId(id)), item);
            }
        }
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
     * What makes two requests for a page "the same request": the entity, the text if it is a
     * search, the page, the size, and whether the complete data of each element is asked for.
     *
     * <p>Being a record, two keys are equal when all of those are. The text is already without
     * the spaces around it and in lower case, so two searches that differ only in that have the
     * same key. Another kind of request gets a record of its own, and records of different types
     * are never equal: their answers cannot be mixed up.
     *
     * @param name     the text of the search, or {@code null} for a list without it
     * @param expanded whether the answer has the complete data of each element, not its summary
     */
    private record PageKey(CatalogEntity entity, NameFilter name, PageQuery query, boolean expanded) {
    }

    /**
     * The key of an answer that has every element, so it has neither page nor size: the entity,
     * the text if it is a search, and whether the complete data of each element is asked for.
     *
     * @param name     the text of the search, or {@code null} for a list without it
     * @param expanded whether the answer has the complete data of each element, not its summary
     */
    private record WholeKey(CatalogEntity entity, NameFilter name, boolean expanded) {
    }

    /** What makes two requests for one element "the same request": the entity and the identifier. */
    private record ItemKey(CatalogEntity entity, ItemId id) {
    }

}
