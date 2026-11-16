package io.github.erickvaernet.starwars;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The session of a test user, for the acceptance tests of requests that need one.
 *
 * <p>It logs in through the real login, like any client of the API. The test has to run with the
 * seed switched on ({@code SEED_ENABLED=true}), because the seed is what loads the test users:
 * {@code demo}, a common user, and {@code admin}, an administrator.
 *
 * <p>Every login here is a correct one. A test that makes failed logins on purpose empties their
 * count before and after, with {@link io.github.erickvaernet.starwars.auth.FailedLogins}: the
 * application blocks the login of an address that fails five times.
 */
public final class TestSession {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String USERNAME = "demo";

    /** The password published in the README. */
    private static final String PASSWORD = "demo1234";

    private static final String ADMIN_USERNAME = "admin";

    /** The password of the administrator, published in the README too. */
    private static final String ADMIN_PASSWORD = "admin1234";

    private TestSession() {
    }

    /**
     * Logs in as the test user, checks that the login answers 200 and returns the token.
     *
     * @param client a client of the running application, without a session
     */
    public static String login(RestTestClient client) {
        return login(client, USERNAME, PASSWORD);
    }

    /**
     * Logs in as the test user and returns a client that sends the token with every request.
     *
     * @param client a client of the running application, without a session
     */
    public static RestTestClient open(RestTestClient client) {
        return withToken(client, login(client));
    }

    /**
     * Logs in as the test administrator, checks that the login answers 200 and returns the token.
     *
     * @param client a client of the running application, without a session
     */
    public static String loginAsAdmin(RestTestClient client) {
        return login(client, ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    /**
     * Logs in as the test administrator and returns a client that sends the token with every
     * request.
     *
     * @param client a client of the running application, without a session
     */
    public static RestTestClient openAsAdmin(RestTestClient client) {
        return withToken(client, loginAsAdmin(client));
    }

    /**
     * Logs in as any user, checks that the login answers 200 and returns the token. It is for
     * the users that a test creates.
     *
     * @param client a client of the running application, without a session
     */
    public static String login(RestTestClient client, String username, String password) {
        String body = client.post().uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        return JsonPath.read(body, "$.accessToken");
    }

    /** A client that sends this token with every request. */
    public static RestTestClient withToken(RestTestClient client, String token) {
        return client.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }

}
