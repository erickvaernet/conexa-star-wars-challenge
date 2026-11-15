package io.github.erickvaernet.starwars.swapi;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of SWAPI, read from the environment at start-up.
 *
 * <p>An invalid value stops the application from starting, so a wrong setting does not go
 * unnoticed until the first list is asked for.
 *
 * @param baseUrl        where SWAPI is, from {@code SWAPI_BASE_URL}, without a slash at the end
 * @param connectTimeout the longest wait to connect, from {@code SWAPI_CONNECT_TIMEOUT}
 * @param readTimeout    the longest wait for an answer, from {@code SWAPI_READ_TIMEOUT}
 * @param maxRetries     how many times a request that failed is sent again, from
 *                       {@code SWAPI_MAX_RETRIES}; with zero, never
 * @param retryDelay     the wait before each retry, from {@code SWAPI_RETRY_DELAY}
 */
@ConfigurationProperties("swapi")
record SwapiProperties(
        String baseUrl, Duration connectTimeout, Duration readTimeout, Integer maxRetries, Duration retryDelay) {

    /** The most retries that can be asked for: each one is one more request to a SWAPI that limits them. */
    private static final int MOST_RETRIES = 3;

    SwapiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("SWAPI_BASE_URL must not be empty: it is the address of SWAPI.");
        }
        // The paths are added to the address starting with a slash; two in a row would not be found.
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        requirePositive(connectTimeout, "SWAPI_CONNECT_TIMEOUT");
        requirePositive(readTimeout, "SWAPI_READ_TIMEOUT");
        // The number is an Integer and not an int so that an empty variable gets this same message.
        if (maxRetries == null || maxRetries < 0 || maxRetries > MOST_RETRIES) {
            throw new IllegalArgumentException(
                    "SWAPI_MAX_RETRIES must be a number from 0 to " + MOST_RETRIES + "; with 0, nothing is retried.");
        }
        // Unlike the timeouts, zero is valid here: it means that the retry is sent at once.
        if (retryDelay == null || retryDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "SWAPI_RETRY_DELAY must be a time that is not negative, such as 200ms or 1s.");
        }
    }

    /** A wait of zero would mean "wait forever" for the HTTP client, which is never wanted. */
    private static void requirePositive(Duration timeout, String variable) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(variable + " must be a time greater than zero, such as 3s or 500ms.");
        }
    }

}
