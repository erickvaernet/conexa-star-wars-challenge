package io.github.erickvaernet.starwars.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Checks how a request that was rejected before reaching a controller is answered: with the
 * status that was chosen, the address that was asked for and a fixed text.
 *
 * <p>Only the web layer is loaded, without the access rules, which have their own tests. A real
 * web server hands the rejected request over with its status and its address; here the test
 * hands them over itself. That the server really does so is checked by the acceptance tests.
 */
@WebMvcTest(RejectedRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
class RejectedRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "status {0}")
    @CsvSource(delimiter = '|', value = {
        "400 | Bad Request           | The request was rejected.",
        "405 | Method Not Allowed    | The method of the request is not supported.",
        "404 | Not Found             | The requested resource was not found.",
        "414 | URI Too Long          | The request was rejected.",
        "500 | Internal Server Error | An unexpected error occurred.",
        "503 | Service Unavailable   | An unexpected error occurred."
    })
    @DisplayName("The answer has the status that was chosen, a fixed text for it and the address that was asked for")
    void answerHasTheStatusThatWasChosen(int status, String title, String detail) throws Exception {
        handedOver(status, "/api/v1/people/1;x=1")
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").value(detail))
                // Not /error, which is where the request is by now.
                .andExpect(jsonPath("$.instance").value("/api/v1/people/1;x=1"));
    }

    @Test
    @DisplayName("An address that cannot be read as one is answered with what is not allowed in it escaped")
    void addressThatCannotBeReadIsEscaped() throws Exception {
        handedOver(400, "/api/v1/people/a b")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.instance").value("/api/v1/people/a%20b"));
    }

    @Test
    @DisplayName("Asked for directly, without an error handed over, it is a route that does not exist")
    void askedForDirectlyItIsNotARoute() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.detail").value("The requested resource was not found."))
                .andExpect(jsonPath("$.instance").value("/error"));
    }

    @Test
    @DisplayName("A status that is not one of HTTP is not repeated: it is answered like a route that does not exist")
    void statusThatIsNotOneOfHttpIsNotRepeated() throws Exception {
        handedOver(999, "/api/v1/people").andExpect(status().isNotFound());
    }

    /** Sends the request the way the web server does after rejecting one: to /error, with these two. */
    private ResultActions handedOver(int status, String address) throws Exception {
        return mockMvc.perform(get("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, status)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, address));
    }

}
