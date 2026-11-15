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
 */
@ConfigurationProperties("swapi")
record SwapiProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

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
    }

    /** A wait of zero would mean "wait forever" for the HTTP client, which is never wanted. */
    private static void requirePositive(Duration timeout, String variable) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(variable + " must be a time greater than zero, such as 3s or 500ms.");
        }
    }

}
