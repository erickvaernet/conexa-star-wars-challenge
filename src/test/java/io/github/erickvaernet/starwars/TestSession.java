package io.github.erickvaernet.starwars;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The session of the test user, for the acceptance tests of requests that need one.
 *
 * <p>It logs in through the real login, like any client of the API. The test has to run with the
 * seed switched on ({@code SEED_ENABLED=true}), because the seed is what loads the test user.
 */
public final class TestSession {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String USERNAME = "demo";

    /** The password published in the README. */
    private static final String PASSWORD = "demo1234";

    private TestSession() {
    }

    /**
     * Logs in as the test user, checks that the login answers 200 and returns the token.
     *
     * @param client a client of the running application, without a session
     */
    public static String login(RestTestClient client) {
        String body = client.post().uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", USERNAME, "password", PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        return JsonPath.read(body, "$.accessToken");
    }

    /**
     * Logs in as the test user and returns a client that sends the token with every request.
     *
     * @param client a client of the running application, without a session
     */
    public static RestTestClient open(RestTestClient client) {
        return client.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + login(client))
                .build();
    }

}
