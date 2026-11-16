package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.erickvaernet.starwars.shared.TooManyLoginAttemptsException;
import io.github.erickvaernet.starwars.swapi.ManualClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Checks the count of login attempts by source address: when an address is blocked, for how long,
 * and what the count forgets.
 *
 * <p>The time goes by only when a test moves the clock, so every number of seconds is exact.
 * "A failed login" is here a place that was taken and then confirmed as a failure; "a login in
 * course" is a place that was taken and whose outcome is not known yet.
 */
@ExtendWith(OutputCaptureExtension.class)
class LoginAttemptLimiterTest {

    private static final String ADDRESS = "203.0.113.7";

    private static final String ANOTHER_ADDRESS = "203.0.113.8";

    private final ManualClock clock = new ManualClock();

    /** The default settings: five failed logins in fifteen minutes. */
    private final LoginAttemptLimiter limiter = limiterOf(5, Duration.ofMinutes(15));

    @Test
    @DisplayName("Five attempts of an address take their place, and the sixth one is rejected with the seconds left of the window")
    void sixthAttemptIsRejected() {
        fail(ADDRESS, 5);

        assertThat(secondsToWait(ADDRESS)).isEqualTo(900);
    }

    @Test
    @DisplayName("A place that is given back can be taken again: a correct login does not add to the count")
    void placeThatIsGivenBackCanBeTakenAgain() {
        for (int login = 1; login <= 100; login++) {
            limiter.acquire(ADDRESS);
            limiter.release(ADDRESS);
        }

        // The five places are still free.
        fail(ADDRESS, 5);
        assertThat(secondsToWait(ADDRESS)).isEqualTo(900);
    }

    @Test
    @DisplayName("Giving a place back does not clear the places that were kept: after four failures and a correct login, one more failure blocks the address")
    void givingAPlaceBackDoesNotClearTheCount() {
        fail(ADDRESS, 4);
        limiter.acquire(ADDRESS);
        limiter.release(ADDRESS);

        fail(ADDRESS, 1);

        assertThat(secondsToWait(ADDRESS)).isPositive();
    }

    @Test
    @DisplayName("A correct login does not open a window: the window starts with the first place that is kept")
    void correctLoginDoesNotOpenAWindow() {
        limiter.acquire(ADDRESS);
        limiter.release(ADDRESS);
        clock.advance(Duration.ofMinutes(14));

        fail(ADDRESS, 5);

        // Fifteen minutes from these failures, not one minute from that correct login.
        assertThat(secondsToWait(ADDRESS)).isEqualTo(900);
    }

    @Test
    @DisplayName("The window starts with the first failure and is not moved by the following ones")
    void windowStartsWithTheFirstFailure() {
        fail(ADDRESS, 1);
        clock.advance(Duration.ofMinutes(10));
        fail(ADDRESS, 4);

        assertThat(secondsToWait(ADDRESS)).isEqualTo(300);
    }

    @Test
    @DisplayName("A rejected attempt does not make the window longer")
    void rejectedAttemptDoesNotMakeTheWindowLonger() {
        fail(ADDRESS, 5);
        clock.advance(Duration.ofMinutes(14));
        assertThat(secondsToWait(ADDRESS)).isEqualTo(60);
        assertThat(secondsToWait(ADDRESS)).isEqualTo(60);

        clock.advance(Duration.ofMinutes(1));

        assertThatCode(() -> limiter.acquire(ADDRESS)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("At exactly the length of the window after the first failure, the window is over and the count starts again from zero")
    void countStartsAgainWhenTheWindowEnds() {
        fail(ADDRESS, 5);
        clock.advance(Duration.ofMinutes(15).minusNanos(1));
        assertThat(secondsToWait(ADDRESS)).isEqualTo(1);

        clock.advance(Duration.ofNanos(1));

        // Five new places, in a window that starts now.
        fail(ADDRESS, 5);
        assertThat(secondsToWait(ADDRESS)).isEqualTo(900);
    }

    @ParameterizedTest(name = "{0} ms after the first failure, {1} seconds")
    @CsvSource({"0, 900", "1, 900", "999, 900", "1000, 899", "1001, 899", "899000, 1", "899001, 1", "899999, 1"})
    @DisplayName("The seconds to wait are rounded up, and are never less than 1: whoever waits them finds the window over")
    void secondsToWaitAreRoundedUp(long millisecondsLater, long seconds) {
        fail(ADDRESS, 5);

        clock.advance(Duration.ofMillis(millisecondsLater));

        assertThat(secondsToWait(ADDRESS)).isEqualTo(seconds);
    }

    @Test
    @DisplayName("Five logins in course and no failure: the sixth is rejected with a wait of one second, not of the whole window")
    void attemptRejectedByLoginsInCourseWaitsOneSecond() {
        for (int login = 1; login <= 5; login++) {
            limiter.acquire(ADDRESS);
        }

        assertThat(secondsToWait(ADDRESS)).isEqualTo(1);
    }

    @Test
    @DisplayName("Logins in course that turn out to be correct leave nothing behind: the address has its five places, and no window was opened")
    void loginsInCourseThatAreCorrectLeaveNothingBehind() {
        for (int login = 1; login <= 5; login++) {
            limiter.acquire(ADDRESS);
        }
        assertThat(secondsToWait(ADDRESS)).isEqualTo(1);
        for (int login = 1; login <= 5; login++) {
            limiter.release(ADDRESS);
        }
        clock.advance(Duration.ofMinutes(14));

        fail(ADDRESS, 5);

        // Fifteen minutes from these failures, not one minute from those correct logins.
        assertThat(secondsToWait(ADDRESS)).isEqualTo(900);
    }

    @Test
    @DisplayName("Four failures and one login in course: the next one waits one second while that login is checked, and the rest of the window once it fails")
    void waitDependsOnWhatFillsTheLastPlace() {
        fail(ADDRESS, 4);
        clock.advance(Duration.ofMinutes(5));
        limiter.acquire(ADDRESS);

        // The last place is taken by a login whose outcome is not known yet.
        assertThat(secondsToWait(ADDRESS)).isEqualTo(1);

        limiter.failed(ADDRESS);

        // Five failures now: the wait is what is left of the window, which started with the first.
        assertThat(secondsToWait(ADDRESS)).isEqualTo(600);
    }

    @Test
    @DisplayName("Four failures and one login in course that turns out to be correct: the place is free again, and the count is still four")
    void correctLoginInCourseGivesItsPlaceBack() {
        fail(ADDRESS, 4);
        limiter.acquire(ADDRESS);
        assertThat(secondsToWait(ADDRESS)).isEqualTo(1);

        limiter.release(ADDRESS);

        assertThatCode(() -> fail(ADDRESS, 1)).doesNotThrowAnyException();
        assertThat(secondsToWait(ADDRESS)).isEqualTo(900);
    }

    @Test
    @DisplayName("A login that started in one window and fails once that window is over is the first failure of a new one")
    void failureConfirmedAfterTheWindowEndsOpensANewWindow() {
        fail(ADDRESS, 4);
        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        limiter.acquire(ADDRESS);
        clock.advance(Duration.ofSeconds(2));

        limiter.failed(ADDRESS);

        // One failure in a window that started now: four more places.
        assertThatCode(() -> fail(ADDRESS, 4)).doesNotThrowAnyException();
        assertThat(secondsToWait(ADDRESS)).isEqualTo(900);
    }

    @Test
    @DisplayName("The window starts with the first failure, not with a login that was already in course when it happened")
    void windowDoesNotStartWithALoginInCourse() {
        limiter.acquire(ADDRESS);
        clock.advance(Duration.ofMinutes(14));

        // That login is still in course. Four places are left: these failures take them.
        fail(ADDRESS, 4);
        limiter.release(ADDRESS);
        fail(ADDRESS, 1);

        // Fifteen minutes from the first failure, not one minute from that login.
        assertThat(secondsToWait(ADDRESS)).isEqualTo(900);
    }

    @Test
    @DisplayName("When the window ends, the failures are forgotten and the logins in course still take their places")
    void loginsInCourseAreKeptWhenTheWindowEnds() {
        fail(ADDRESS, 1);
        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        for (int login = 1; login <= 4; login++) {
            limiter.acquire(ADDRESS);
        }
        clock.advance(Duration.ofSeconds(2));

        // The failure is over, so one place is free; the four logins in course take the others.
        assertThatCode(() -> limiter.acquire(ADDRESS)).doesNotThrowAnyException();
        assertThat(secondsToWait(ADDRESS)).isEqualTo(1);
    }

    @Test
    @DisplayName("Of the failed logins that arrive at the very same moment from one address, exactly five take a place and all five are counted, round after round")
    void attemptsAtTheSameMomentNeverTakeMoreThanFivePlaces() throws Exception {
        int attempts = 16;
        int rounds = 400;

        try (ExecutorService threads = Executors.newFixedThreadPool(attempts)) {
            for (int round = 1; round <= rounds; round++) {
                LoginAttemptLimiter fresh = limiterOf(5, Duration.ofMinutes(15));
                // No attempt goes on until all of them are waiting at the barrier.
                CyclicBarrier allReady = new CyclicBarrier(attempts);
                List<Future<Boolean>> outcomes = new ArrayList<>();
                for (int attempt = 1; attempt <= attempts; attempt++) {
                    outcomes.add(threads.submit(() -> {
                        allReady.await();
                        try {
                            fresh.acquire(ADDRESS);
                        } catch (TooManyLoginAttemptsException rejected) {
                            return false;
                        }
                        // The ones that got a place fail, also at nearly the same moment.
                        fresh.failed(ADDRESS);
                        return true;
                    }));
                }

                int placesTaken = 0;
                for (Future<Boolean> outcome : outcomes) {
                    if (outcome.get()) {
                        placesTaken++;
                    }
                }
                assertThat(placesTaken).as("places taken in round %d", round).isEqualTo(5);
                // Five failures and no login in course: the wait is the whole window. With a
                // failure lost on the way it would be one second, or there would be no wait.
                TooManyLoginAttemptsException blocked =
                        catchThrowableOfType(TooManyLoginAttemptsException.class, () -> fresh.acquire(ADDRESS));
                assertThat(blocked).as("the address after round %d", round).isNotNull();
                assertThat(blocked.retryAfterSeconds()).as("the wait after round %d", round).isEqualTo(900);
            }
        }
    }

    @Test
    @DisplayName("Each address has its own count: the failures of one do not block another")
    void eachAddressHasItsOwnCount() {
        fail(ADDRESS, 5);

        assertThatCode(() -> fail(ANOTHER_ADDRESS, 5)).doesNotThrowAnyException();
        assertThat(secondsToWait(ADDRESS)).isPositive();
        assertThat(secondsToWait(ANOTHER_ADDRESS)).isPositive();
    }

    @Test
    @DisplayName("The address is cut to 45 characters: two texts that share their first 45 characters share one count")
    void addressIsCutTo45Characters() {
        String first45 = "a".repeat(45);
        fail(first45 + "-one-ending", 5);

        // The same 45 characters with another ending, and alone: the same count.
        assertThat(secondsToWait(first45 + "-another-ending")).isPositive();
        assertThat(secondsToWait(first45)).isPositive();
        // A text that differs within its first 45 characters: another count.
        assertThatCode(() -> limiter.acquire("a".repeat(44) + "b")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("An address that is missing is counted like any other, under one name")
    void missingAddressIsCountedUnderOneName() {
        fail(null, 5);

        assertThat(secondsToWait(null)).isPositive();
    }

    @Test
    @DisplayName("At most 10 000 addresses are kept: with more, the count of some address is discarded, and the memory does not grow")
    void atMostTenThousandAddressesAreKept() {
        LoginAttemptLimiter strict = limiterOf(1, Duration.ofMinutes(15));
        int addresses = 12_000;
        for (int number = 0; number < addresses; number++) {
            strict.acquire("address-" + number);
            strict.failed("address-" + number);
        }

        // Every address was blocked by its only failure. The ones still blocked are the ones kept.
        int stillBlocked = 0;
        for (int number = 0; number < addresses; number++) {
            String address = "address-" + number;
            if (catchThrowableOfType(TooManyLoginAttemptsException.class, () -> strict.acquire(address)) != null) {
                stillBlocked++;
            }
        }
        assertThat(stillBlocked).isPositive().isLessThanOrEqualTo(LoginAttemptLimiter.MOST_ADDRESSES);
        assertThat(LoginAttemptLimiter.MOST_ADDRESSES).isEqualTo(10_000);
    }

    @Test
    @DisplayName("With the limit switched off, nothing is counted and no attempt is rejected")
    void limitSwitchedOffRejectsNothing() {
        LoginAttemptLimiter off = limiterOf(0, Duration.ofMinutes(15));

        assertThatCode(() -> {
            for (int attempt = 1; attempt <= 1000; attempt++) {
                off.acquire(ADDRESS);
                off.failed(ADDRESS);
            }
            off.release(ADDRESS);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Other settings are followed: with a limit of 2 and a window of one minute, the third attempt waits up to 60 seconds")
    void otherSettingsAreFollowed() {
        LoginAttemptLimiter other = limiterOf(2, Duration.ofMinutes(1));
        for (int attempt = 1; attempt <= 2; attempt++) {
            other.acquire(ADDRESS);
            other.failed(ADDRESS);
        }

        TooManyLoginAttemptsException rejected =
                catchThrowableOfType(TooManyLoginAttemptsException.class, () -> other.acquire(ADDRESS));

        assertThat(rejected.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("Forgetting everything leaves no address blocked")
    void forgettingEverythingLeavesNoAddressBlocked() {
        fail(ADDRESS, 5);
        fail(ANOTHER_ADDRESS, 5);

        limiter.forgetAll();

        assertThatCode(() -> {
            limiter.acquire(ADDRESS);
            limiter.acquire(ANOTHER_ADDRESS);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("When the failure that blocks an address is confirmed, one warning goes to the log with the address, and none before that")
    void warningIsLoggedWhenAnAddressIsBlocked(CapturedOutput output) {
        for (int attempt = 1; attempt <= 4; attempt++) {
            limiter.acquire(ADDRESS);
            limiter.failed(ADDRESS);
        }
        assertThat(output.getAll()).doesNotContain("blocked");

        limiter.acquire(ADDRESS);
        limiter.failed(ADDRESS);

        assertThat(output.getAll()).contains("WARN").contains("blocked").contains(ADDRESS);
    }

    @Test
    @DisplayName("A correct login in the last free place blocks nobody, and writes no warning")
    void correctLoginInTheLastPlaceWritesNoWarning(CapturedOutput output) {
        fail(ADDRESS, 4);

        limiter.acquire(ADDRESS);
        limiter.release(ADDRESS);

        assertThat(output.getAll()).doesNotContain("blocked");
    }

    @Test
    @DisplayName("What goes to the log is the address as it is counted, cut to 45 characters, when it has the form of an address")
    void addressGoesToTheLogWhenItHasTheFormOfOne() {
        assertThat(LoginAttemptLimiter.forLog("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(LoginAttemptLimiter.forLog("2001:db8::ff00:42:8329")).isEqualTo("2001:db8::ff00:42:8329");
        assertThat(LoginAttemptLimiter.forLog("2001:DB8::FF00:42:8329")).isEqualTo("2001:DB8::FF00:42:8329");
        // Behind a proxy, an IPv6 address arrives between square brackets.
        assertThat(LoginAttemptLimiter.forLog("[2001:db8::ff00:42:8329]")).isEqualTo("[2001:db8::ff00:42:8329]");
        assertThat(LoginAttemptLimiter.forLog("1".repeat(60))).isEqualTo("1".repeat(45));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
        "no-es-una-direccion",
        "203.0.113.7\r\n2026-11-15 WARN a line that the client wrote",
        "203.0.113.7\n",
        "203.0.113.7 ",
        "fe80::1%eth0",
        "<script>",
        "${jndi:ldap://example.com/a}"
    })
    @DisplayName("A text that does not have the form of an address goes to the log with every other character replaced: behind a proxy, the client may choose that text")
    void textThatIsNotAnAddressIsWrittenWithoutWhatTheClientChose(String text) {
        String written = LoginAttemptLimiter.forLog(text);

        // Only digits, the letters of a hexadecimal number, dots, colons, square brackets and the
        // replacement.
        assertThat(written).matches("[0-9a-fA-F.:\\[\\]?]*").contains("?");
        assertThat(written).hasSize(Math.min(text.length(), 45));
    }

    @Test
    @DisplayName("The warning of a blocked address does not let the client write lines in the log")
    void warningDoesNotLetTheClientWriteLinesInTheLog(CapturedOutput output) {
        String forged = "192.0.2.9\nFORGED-LINE";
        fail(forged, 4);

        limiter.acquire(forged);
        limiter.failed(forged);

        assertThat(output.getAll()).contains("blocked").contains("192.0.2.9?").doesNotContain("FORGED-LINE");
    }

    /** Makes that many failed logins of an address: each one takes a place and then fails. */
    private void fail(String address, int times) {
        for (int attempt = 1; attempt <= times; attempt++) {
            limiter.acquire(address);
            limiter.failed(address);
        }
    }

    /** Checks that one more attempt of that address is rejected, and returns the seconds it has to wait. */
    private long secondsToWait(String address) {
        TooManyLoginAttemptsException rejected =
                catchThrowableOfType(TooManyLoginAttemptsException.class, () -> limiter.acquire(address));
        assertThat(rejected).as("an attempt of a blocked address").isNotNull();
        return rejected.retryAfterSeconds();
    }

    private LoginAttemptLimiter limiterOf(int maxFailedAttempts, Duration window) {
        return new LoginAttemptLimiter(new LoginAttemptProperties(maxFailedAttempts, window), Optional.of(clock));
    }

}
