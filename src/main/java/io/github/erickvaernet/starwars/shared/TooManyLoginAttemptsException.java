package io.github.erickvaernet.starwars.shared;

/**
 * A login that is not even checked, because its source address already failed too many times or
 * has too many logins being checked at once. It is answered with a 429.
 *
 * <p>It carries only how long to wait: the text of the answer is fixed. It is here, and not next
 * to the login, because every error of the API is answered from this package, which uses no other.
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    /**
     * @param retryAfterSeconds the seconds until a login from that address is checked again; it
     *                          goes to the client in the {@code Retry-After} header
     */
    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

}
