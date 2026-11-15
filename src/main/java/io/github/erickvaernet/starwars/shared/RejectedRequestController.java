package io.github.erickvaernet.starwars.shared;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

/**
 * Answers the requests that are rejected before they reach a controller, as Problem Details.
 *
 * <p>The web server and the access rules reject some requests on their own: an address with a
 * semicolon or a double slash, a method that is switched off or that is not one of HTTP. They do
 * not answer them: they hand the request over to the internal address {@code /error}, with the
 * status they chose. This class is what answers there, so that those errors have the format of
 * every other one, the status that was chosen and the address that the client asked for.
 *
 * <p>Spring Boot brings an answer of its own for that address, with another format; declaring
 * this class replaces it.
 */
@RestController
// It is not a request of the API: it does not belong in its documentation.
@Hidden
class RejectedRequestController implements ErrorController {

    /**
     * Answers the error that was handed over.
     *
     * <p>A request that asks for {@code /error} directly carries no error: for a client, that
     * address is a route that does not exist.
     */
    @RequestMapping("/error")
    ResponseEntity<ProblemDetail> answer(HttpServletRequest request) {
        HttpStatus status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
                ? HttpStatus.resolve(code)
                : null;
        if (status == null || !(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String asked)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiExceptionHandler.notFound());
        }
        ProblemDetail problem = ApiExceptionHandler.problem(status, detailOf(status));
        // Without this the answer would name /error, which is where the request is now.
        problem.setInstance(addressOf(asked));
        return ResponseEntity.status(status).body(problem);
    }

    /** The texts are fixed, like the ones of every other error: nothing internal gets out. */
    private static String detailOf(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return ApiExceptionHandler.NOT_FOUND;
        }
        if (status == HttpStatus.METHOD_NOT_ALLOWED) {
            // Without the name of the method: by the time the request arrives here, the web
            // server has already replaced it with GET.
            return "The method of the request is not supported.";
        }
        return status.is5xxServerError() ? ApiExceptionHandler.UNEXPECTED : "The request was rejected.";
    }

    /**
     * The address that was asked for, as the web server gives it. If it cannot be read as an
     * address, what is not allowed in one is escaped.
     */
    private static URI addressOf(String asked) {
        try {
            return URI.create(asked);
        } catch (IllegalArgumentException notAnAddress) {
            return URI.create(UriUtils.encodePath(asked, StandardCharsets.UTF_8));
        }
    }

}
