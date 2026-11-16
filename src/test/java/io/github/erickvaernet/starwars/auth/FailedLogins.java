package io.github.erickvaernet.starwars.auth;

import org.springframework.context.ApplicationContext;

/**
 * The count of failed logins of a running application, in the hands of the tests.
 *
 * <p>The application counts the failed logins of each source address and, at the fifth one,
 * blocks the login of that address for a while. The acceptance tests that share a configuration
 * share one running application too, and all their requests come from the same address: the
 * failed logins of one test would count against the next one.
 *
 * <p>So a test that makes failed logins on purpose empties the count before and after, with
 * {@link #forget}. It is public because tests of any package need it, and it lives in this
 * package because the piece that counts is not visible outside it.
 */
public final class FailedLogins {

    private FailedLogins() {
    }

    /**
     * Forgets every failed login counted so far, of every address: no address is blocked after
     * this, and each one starts again from zero.
     *
     * @param application the running application, which a test gets with {@code @Autowired}
     */
    public static void forget(ApplicationContext application) {
        application.getBean(LoginAttemptLimiter.class).forgetAll();
    }

}
