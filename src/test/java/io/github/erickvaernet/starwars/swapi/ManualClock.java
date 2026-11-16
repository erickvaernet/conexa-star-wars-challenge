package io.github.erickvaernet.starwars.swapi;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A clock that stands still until a test moves it forward.
 *
 * <p>The cache measures the age of what it stores with a clock of its own, and the limit of
 * login attempts measures its window with the same one. With this clock in its place, a test of
 * the expiry makes an hour go by in an instant instead of waiting for it.
 *
 * <p>It is public because the tests of the login, in another package, move it too.
 */
public class ManualClock implements Ticker {

    private final AtomicLong nanoseconds = new AtomicLong();

    @Override
    public long read() {
        return nanoseconds.get();
    }

    /** Moves the clock forward. For whoever reads it, that much time has gone by. */
    public void advance(Duration time) {
        nanoseconds.addAndGet(time.toNanos());
    }

}
