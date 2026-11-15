package io.github.erickvaernet.starwars.swapi;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A clock for the cache that stands still until a test moves it forward.
 *
 * <p>The cache measures the age of what it stores with a clock of its own. With this one in its
 * place, a test of the expiry makes an hour go by in an instant instead of waiting for it.
 */
class ManualClock implements Ticker {

    private final AtomicLong nanoseconds = new AtomicLong();

    @Override
    public long read() {
        return nanoseconds.get();
    }

    /** Moves the clock forward. For the cache, that much time has gone by. */
    void advance(Duration time) {
        nanoseconds.addAndGet(time.toNanos());
    }

}
