package io.github.erickvaernet.starwars.shared;

/**
 * The data source failed: it answered with an error, with something that cannot be read, or
 * could not be reached. It is answered with a 502.
 *
 * <p>The message and the cause are for the log of the application. They never reach the client,
 * because they can name the address of the source or show what it answered.
 */
public class BadGatewayException extends RuntimeException {

    /**
     * @param message what failed, for the log
     * @param cause   the original error, or {@code null} if there is none
     */
    public BadGatewayException(String message, Throwable cause) {
        super(message, cause);
    }

}
