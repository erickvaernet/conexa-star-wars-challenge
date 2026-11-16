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
 * password is checked ({@link #acquire}), and then either gives it back, if it turns out to be
 * correct ({@link #release}), or keeps it as a failure ({@link #failed}). Counting before checking
 * is what holds the limit when many logins arrive at once: only as many as there are places get
 * to check a password.
 *
 * <p>The failures and the logins that are being checked are kept apart, because a rejected login
 * is told how long to wait. With all the places taken by failures, it waits what is left of the
 * window. If some place is taken by a login whose outcome is not known yet, nobody failed that
 * many times: it is told to try again in a second, which is more than a check lasts.
 *
 * <p>The window of an address starts with its first failure and lasts a fixed time. Neither the
 * following failures nor the rejected logins move it, so the wait that a rejected login is told
 * stays true.
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

    /**
     * What a login is told to wait, in seconds, when what leaves it without a place is a login
     * that is still being checked, and not the failures of its address.
     */
    static final long WAIT_FOR_A_LOGIN_IN_COURSE = 1;

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
     *                                       seconds to wait: what is left of its window if the
     *                                       places are taken by failures, and one second if
     *                                       some is taken by a login that is being checked
     */
    synchronized void acquire(String address) {
        if (limit == 0) {
            return;
        }
        String key = key(address);
        long now = clock.read();
        Attempts attempts = current(key, now);

        if (attempts.failures() >= limit) {
            throw new TooManyLoginAttemptsException(secondsLeft(attempts.windowStart() + windowNanos - now));
        }
        if (attempts.failures() + attempts.inCourse() >= limit) {
            throw new TooManyLoginAttemptsException(WAIT_FOR_A_LOGIN_IN_COURSE);
        }
        attemptsByAddress.put(key, new Attempts(attempts.windowStart(), attempts.failures(), attempts.inCourse() + 1));
    }

    /**
     * Gives back the place of a login that did not fail: it was correct, or it ended in an error
     * that has nothing to do with its credentials.
     *
     * <p>The failures of the address stay as they are: a correct login does not clear them. When
     * an address has neither failures nor logins in course, it is forgotten.
     */
    synchronized void release(String address) {
        if (limit == 0) {
            return;
        }
        String key = key(address);
        Attempts attempts = current(key, clock.read());
        keep(key, new Attempts(attempts.windowStart(), attempts.failures(), Math.max(0, attempts.inCourse() - 1)));
    }

    /**
     * Notes that a login of that address failed: its place stops being the one of a login in
     * course and becomes a failure. The first failure of an address is what starts its window.
     *
     * <p>When that failure leaves the address blocked, a warning goes to the log. The line has
     * the address and nothing else: neither the user name nor the password. Failures that
     * arrive at the very same time may write it more than once.
     */
    synchronized void failed(String address) {
        if (limit == 0) {
            return;
        }
        String key = key(address);
        long now = clock.read();
        Attempts attempts = current(key, now);

        // Without failures there is no window yet: it starts now, also for a login that took its
        // place in a window that ended while its password was being checked.
        long windowStart = attempts.failures() == 0 ? now : attempts.windowStart();
        int failures = attempts.failures() + 1;
        keep(key, new Attempts(windowStart, failures, Math.max(0, attempts.inCourse() - 1)));

        if (failures == limit) {
            log.warn("Login blocked for the address {} until its window ends: {} failed attempts",
                    forLog(key), limit);
        }
    }

    /**
     * What is counted of an address at this moment: nothing if it is not in the table, and no
     * failures if its window is over. The logins in course are kept across windows: each one
     * still has to give its place back or to fail.
     */
    private Attempts current(String key, long now) {
        Attempts attempts = attemptsByAddress.getIfPresent(key);
        if (attempts == null) {
            return new Attempts(now, 0, 0);
        }
        if (attempts.failures() > 0 && now - attempts.windowStart() >= windowNanos) {
            return new Attempts(now, 0, attempts.inCourse());
        }
        return attempts;
    }

    /** Writes the count of an address, or forgets the address if there is nothing left to count. */
    private void keep(String key, Attempts attempts) {
        if (attempts.failures() == 0 && attempts.inCourse() == 0) {
            attemptsByAddress.invalidate(key);
        } else {
            attemptsByAddress.put(key, attempts);
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
     * Only digits, the letters of a hexadecimal number, dots, colons and square brackets are kept:
     * behind a proxy, an IPv6 address arrives between square brackets.
     */
    static String forLog(String address) {
        return key(address).replaceAll("[^0-9a-fA-F.:\\[\\]]", "?");
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
     * What is kept of an address. Its places taken are the failures plus the logins in course.
     *
     * @param windowStart when its window started, as the clock reads it, in nanoseconds: the
     *                    moment of its first failure. It means nothing while there are none
     * @param failures    how many logins failed within that window
     * @param inCourse    how many logins took a place and are being checked right now
     */
    private record Attempts(long windowStart, int failures, int inCourse) {
    }

}
