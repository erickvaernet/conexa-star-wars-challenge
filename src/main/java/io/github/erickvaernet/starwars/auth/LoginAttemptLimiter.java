package io.github.erickvaernet.starwars.auth;

import org.springframework.stereotype.Component;

/**
 * Counts the failed logins of each source address.
 */
@Component
class LoginAttemptLimiter {

    /** Forgets every failed login counted so far, of every address. */
    void forgetAll() {
    }

}
