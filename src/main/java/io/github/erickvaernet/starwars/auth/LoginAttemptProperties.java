package io.github.erickvaernet.starwars.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.convert.DurationStyle;

/**
 * Settings of the limit of login attempts, read from the environment at start-up.
 *
 * <p>An invalid value stops the application from starting, so a wrong setting does not go
 * unnoticed until somebody tries passwords. The window is checked even when the limit is switched
 * off: otherwise a wrong value would wait hidden until the day the limit is switched on.
 *
 * @param maxFailedAttempts    how many failed logins block the login of a source address, from
 *                             {@code LOGIN_MAX_FAILED_ATTEMPTS}; with zero, the limit is off
 * @param failedAttemptsWindow how long those failures are counted for, and so how long the block
 *                             lasts at most, from {@code LOGIN_FAILED_ATTEMPTS_WINDOW}
 */
@ConfigurationProperties("auth.login")
record LoginAttemptProperties(int maxFailedAttempts, Duration failedAttemptsWindow) {

    /** Above this, the limit would stop nobody: it is almost surely a typing mistake. */
    private static final int MOST_ATTEMPTS = 100;

    /** A longer block is not a wait any more, and the count is lost on every restart anyway. */
    private static final Duration LONGEST_WINDOW = Duration.ofHours(24);

    private static final String ATTEMPTS_RULE = "LOGIN_MAX_FAILED_ATTEMPTS must be a whole number from 0 to "
            + MOST_ATTEMPTS + "; with 0, the limit of login attempts is switched off.";

    private static final String WINDOW_RULE = "LOGIN_FAILED_ATTEMPTS_WINDOW must be a time greater than zero "
            + "and of 24h at most, such as 15m or 1h.";

    LoginAttemptProperties {
        if (maxFailedAttempts < 0 || maxFailedAttempts > MOST_ATTEMPTS) {
            throw new IllegalArgumentException(ATTEMPTS_RULE);
        }
        if (failedAttemptsWindow == null || failedAttemptsWindow.isZero() || failedAttemptsWindow.isNegative()
                || failedAttemptsWindow.compareTo(LONGEST_WINDOW) > 0) {
            throw new IllegalArgumentException(WINDOW_RULE);
        }
    }

    /**
     * How the settings arrive from the environment: as text.
     *
     * <p>They are read here and not by Spring so that a value that cannot be read, such as
     * {@code many}, gets the same message as one out of range: the one that names the variable
     * to fix. The message of Spring names the internal setting instead.
     */
    @ConstructorBinding
    LoginAttemptProperties(String maxFailedAttempts, String failedAttemptsWindow) {
        this(wholeNumber(maxFailedAttempts), time(failedAttemptsWindow));
    }

    /** With a limit of zero nothing is counted, and no login is ever blocked. */
    boolean enabled() {
        return maxFailedAttempts > 0;
    }

    private static int wholeNumber(String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (RuntimeException unreadable) {
            // Empty, missing or not a number: the text itself is not repeated.
            throw new IllegalArgumentException(ATTEMPTS_RULE);
        }
    }

    /** A number with its unit, as every other time of the settings: {@code 15m}, {@code 1h}. */
    private static Duration time(String text) {
        try {
            return DurationStyle.detectAndParse(text.strip());
        } catch (RuntimeException unreadable) {
            throw new IllegalArgumentException(WINDOW_RULE);
        }
    }

}
