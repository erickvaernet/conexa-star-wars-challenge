package io.github.erickvaernet.starwars.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checks how the errors that are not about the session are answered: an invalid parameter, a
 * failure of the data source and a route that does not exist.
 *
 * <p>Only the web layer is loaded, with the real error format and without the access rules,
 * which have their own tests. The requests that fail exist only here.
 */
@WebMvcTest(ApiExceptionHandlerTest.FailingRequests.class)
@Import(ApiExceptionHandlerTest.FailingRequests.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiExceptionHandlerTest {

    /** A text that only the cause of the failure has, to check that it does not get out. */
    private static final String INTERNAL_TEXT = "http://internal-host:1234/api/people";

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc).build();
    }

    @Test
    @DisplayName("An invalid parameter is answered with 400, and the detail says which one and what it must be")
    void invalidParameterIsAnsweredWithBadRequest() {
        String body = expectProblem("/test/invalid-parameter", 400, "Bad Request");

        assertThat(detailOf(body)).isEqualTo("Parameter 'size' must be an integer between 1 and 100.");
    }

    @Test
    @DisplayName("A failure of the data source is answered with 502 and a fixed text")
    void failureOfTheDataSourceIsAnsweredWithBadGateway() {
        String body = expectProblem("/test/bad-gateway", 502, "Bad Gateway");

        assertThat(detailOf(body)).isEqualTo("The data source failed or returned an unexpected response.");
    }

    @Test
    @DisplayName("A data source that does not answer in time is answered with 504 and a fixed text")
    void slowDataSourceIsAnsweredWithGatewayTimeout() {
        String body = expectProblem("/test/gateway-timeout", 504, "Gateway Timeout");

        assertThat(detailOf(body)).isEqualTo("The data source did not respond in time.");
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {"/api/v1/no-such-route", "/api/v1/people", "/no-such-file.html"})
    @DisplayName("A route that does not exist is answered with 404 and a fixed text, not the one Spring writes")
    void routeThatDoesNotExistIsAnsweredWithNotFound(String path) {
        String body = expectProblem(path, 404, "Not Found");

        assertThat(detailOf(body)).isEqualTo("The requested resource was not found.");
        // Spring would write "No static resource api/v1/people.", which names an internal detail.
        assertThat(body).doesNotContain("static resource");
    }

    /**
     * Checks an error in Problem Details format that shows nothing of its cause, and returns its
     * body.
     */
    private String expectProblem(String path, int status, String title) {
        byte[] bytes = client.get().uri(path)
                .exchange()
                .expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo(title)
                .jsonPath("$.status").isEqualTo(status)
                .jsonPath("$.instance").isEqualTo(path)
                .returnResult().getResponseBody();
        String body = new String(bytes, StandardCharsets.UTF_8);

        assertThat(body).doesNotContain(INTERNAL_TEXT).doesNotContain("Exception");
        return body;
    }

    private static String detailOf(String body) {
        return JsonPath.read(body, "$.detail");
    }

    /** Requests that fail the way the real ones can. */
    @RestController
    static class FailingRequests {

        @GetMapping("/test/invalid-parameter")
        String invalidParameter() {
            throw new InvalidParameterException("size", "must be an integer between 1 and 100");
        }

        @GetMapping("/test/bad-gateway")
        String badGateway() {
            throw new BadGatewayException("GET " + INTERNAL_TEXT + " answered 500", new IOException(INTERNAL_TEXT));
        }

        @GetMapping("/test/gateway-timeout")
        String gatewayTimeout() {
            throw new GatewayTimeoutException("GET " + INTERNAL_TEXT + " timed out", new IOException(INTERNAL_TEXT));
        }

    }

}
