package io.github.erickvaernet.starwars.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.erickvaernet.starwars.shared.TooManyLoginAttemptsException;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Counts the failed logins of each source address and, when an address reaches the limit, rejects
 * its logins until its window ends.
 *
 * <p>Each address has a few places, as many as the limit. A login takes one <em>before</em> its
 * password is checked ({@link #acquire}) and gives it back only if it turns out to be correct
 * ({@link #release}); a failed login keeps it. Counting before checking is what holds the limit
 * when many logins arrive at once: only as many as there are places get to check a password.
 *
 * <p>The window of an address starts with the first place that is kept and lasts a fixed time.
 * Neither the following failures nor the rejected logins move it, so the wait that a rejected
 * login is told stays true.
 *
 * <p>The count lives in the memory of this instance: it is lost on a restart and is not shared
 * with other instances.
 */
@Component
class LoginAttemptLimiter {

    /** How many addresses are counted at most. It is what bounds the memory of the count. */
    static final int MOST_ADDRESSES = 10_000;

    /** The longest an address is as text: an IPv6 address written in full, with an IPv4 ending. */
    static final int LONGEST_ADDRESS = 45;

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    private final int limit;

    private final long windowNanos;

    private final Ticker clock;

    private final Cache<String, Attempts> attemptsByAddress;

    /**
     * @param properties the limit and the length of the window
     * @param clock      the clock that measures the window. The application declares none, so it
     *                   is the one of the system; a test declares its own to make time go by
     *                   without waiting. It is the same clock that the cache of SWAPI reads
     */
    LoginAttemptLimiter(LoginAttemptProperties properties, Optional<Ticker> clock) {
        this.limit = properties.maxFailedAttempts();
        this.windowNanos = properties.failedAttemptsWindow().toNanos();
        this.clock = clock.orElse(Ticker.systemTicker());
        this.attemptsByAddress = Caffeine.newBuilder()
                .maximumSize(MOST_ADDRESSES)
                // Only to free memory: a count that nobody touched for a whole window is over.
                // The window itself is not measured with this expiry, which starts again with
                // every write, but with the time kept next to the count.
                .expireAfterWrite(properties.failedAttemptsWindow())
                .ticker(this.clock)
                // What does not fit is discarded at once, by whoever writes, and not later in
                // the background: the table never holds more than its size.
                .executor(Runnable::run)
                .build();
    }

    /**
     * Takes a place for a login of that address, which is about to check a password.
     *
     * @throws TooManyLoginAttemptsException if the address has no place left; it says how many
     *                                       seconds are left of its window
     */
    synchronized void acquire(String address) {
        if (limit == 0) {
            return;
        }
        String key = key(address);
        long now = clock.read();
        Attempts attempts = attemptsByAddress.getIfPresent(key);

        if (attempts == null || now - attempts.windowStart() >= windowNanos) {
            // The first attempt of a window, or the first one after the window ended.
            attemptsByAddress.put(key, new Attempts(now, 1));
        } else if (attempts.places() < limit) {
            attemptsByAddress.put(key, new Attempts(attempts.windowStart(), attempts.places() + 1));
        } else {
            throw new TooManyLoginAttemptsException(secondsLeft(attempts.windowStart() + windowNanos - now));
        }
    }

    /**
     * Gives back the place of a login that did not fail: it was correct, or it ended in an error
     * that has nothing to do with its credentials.
     *
     * <p>The other places of the address stay as they are: a correct login does not clear the
     * failures that were already counted. When no place is left taken, the address is forgotten,
     * so that a correct login alone does not open a window.
     */
    synchronized void release(String address) {
        String key = key(address);
        Attempts attempts = attemptsByAddress.getIfPresent(key);
        if (attempts == null) {
            return;
        }
        if (attempts.places() <= 1) {
            attemptsByAddress.invalidate(key);
        } else {
            attemptsByAddress.put(key, new Attempts(attempts.windowStart(), attempts.places() - 1));
        }
    }

    /**
     * Notes that a login of that address failed. Its place is kept, so there is nothing to
     * count here: this only writes the warning when that failure leaves the address blocked.
     *
     * <p>The line has the address and nothing else: neither the user name nor the password.
     * Failures that arrive at the very same time may write it more than once.
     */
    synchronized void failed(String address) {
        Attempts attempts = attemptsByAddress.getIfPresent(key(address));
        if (limit > 0 && attempts != null && attempts.places() == limit) {
            log.warn("Login blocked for the address {} until its window ends: {} failed attempts",
                    forLog(key(address)), limit);
        }
    }

    /**
     * Forgets every attempt counted so far, of every address.
     *
     * <p>It exists for the tests, and the application never calls it: the acceptance tests share
     * one running application and one address, and each one has to start with the count empty.
     */
    synchronized void forgetAll() {
        attemptsByAddress.invalidateAll();
    }

    /**
     * The address as it is written in the log: cut to its longest size, and with every character
     * that an address does not have replaced.
     *
     * <p>Behind a proxy the "address" is a text that arrives in a header, and the client may
     * have written it: as it is, it could carry line breaks and write lines of its own in the log.
     * Only digits, the letters of a hexadecimal number, dots and colons are kept.
     */
    static String forLog(String address) {
        return key(address).replaceAll("[^0-9a-fA-F.:]", "?");
    }

    /**
     * The name an address is counted under: its text, cut to the longest an address can be. The
     * cut bounds the memory of each count, because behind a proxy the text can be anything.
     */
    private static String key(String address) {
        if (address == null) {
            return "unknown";
        }
        return address.length() <= LONGEST_ADDRESS ? address : address.substring(0, LONGEST_ADDRESS);
    }

    /**
     * The whole seconds left, rounded up and never less than one: whoever waits that long finds
     * the window over, and is never told to wait zero seconds.
     */
    private static long secondsLeft(long nanosLeft) {
        Duration left = Duration.ofNanos(nanosLeft);
        long seconds = left.toNanosPart() == 0 ? left.toSeconds() : left.toSeconds() + 1;
        return Math.max(1, seconds);
    }

    /**
     * What is kept of an address.
     *
     * @param windowStart when its window started, as the clock reads it, in nanoseconds
     * @param places      how many places are taken: its failed logins, and the logins that are
     *                    being checked right now
     */
    private record Attempts(long windowStart, int places) {
    }

}
