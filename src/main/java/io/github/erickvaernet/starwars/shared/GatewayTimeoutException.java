package io.github.erickvaernet.starwars.shared;

/**
 * The data source did not answer within the longest time the application waits for it. It is
 * answered with a 504.
 *
 * <p>The message and the cause are for the log of the application. They never reach the client.
 */
public class GatewayTimeoutException extends RuntimeException {

    /**
     * @param message what was being asked for, for the log
     * @param cause   the original error
     */
    public GatewayTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

}
