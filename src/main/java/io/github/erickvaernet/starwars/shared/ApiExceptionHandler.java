package io.github.erickvaernet.starwars.shared;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Answers every error of the API as Problem Details (RFC 9457).
 *
 * <p>The parent class, which comes with Spring, already answers the errors of the web layer in
 * that format: an invalid body, a route that does not exist, a method that is not allowed. This
 * class adds the errors that Spring does not know how to answer.
 *
 * <p>No answer shows internal details. The texts are fixed here; the message of the exception
 * never reaches the client.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** The type of an error that has no type of its own, as the RFC names it. */
    private static final URI NO_SPECIFIC_TYPE = URI.create("about:blank");

    /**
     * A login with wrong credentials. The answer does not say whether the user name or the
     * password was wrong.
     */
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException error) {
        return unauthorized("Invalid username or password.");
    }

    /**
     * A request that needs a session and has none: its token is missing, malformed, expired or
     * has a signature that does not match. The answer is the same in the four cases.
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleNotAuthenticated(AuthenticationException error) {
        return unauthorized("Authentication is required to access this resource.");
    }

    /**
     * A parameter with a value that is not accepted. The answer names the parameter and the rule
     * it must follow, and never repeats the value that was received.
     */
    @ExceptionHandler(InvalidParameterException.class)
    ProblemDetail handleInvalidParameter(InvalidParameterException error) {
        return problem(HttpStatus.BAD_REQUEST,
                "Parameter '" + error.parameter() + "' " + error.requirement() + ".");
    }

    /**
     * An element that does not exist, asked for by its address. The answer is the same as for a
     * route that does not exist: for the client, both are an address with nothing behind it.
     */
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException error) {
        return notFound();
    }

    /** The data source failed. What failed goes to the log, not to the answer. */
    @ExceptionHandler(BadGatewayException.class)
    ProblemDetail handleBadGateway(BadGatewayException error) {
        log.warn("The data source failed", error);
        return problem(HttpStatus.BAD_GATEWAY, "The data source failed or returned an unexpected response.");
    }

    /** The data source did not answer in time. */
    @ExceptionHandler(GatewayTimeoutException.class)
    ProblemDetail handleGatewayTimeout(GatewayTimeoutException error) {
        log.warn("The data source did not respond in time", error);
        return problem(HttpStatus.GATEWAY_TIMEOUT, "The data source did not respond in time.");
    }

    /** Any error that has no handler of its own. The cause goes to the log, not to the answer. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception error) {
        log.error("Unexpected error", error);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    /**
     * A route that does not exist. Spring looks for a static file when no controller answers a
     * route, and its own text says so ("No static resource ..."): that is an internal detail,
     * so the text is replaced.
     */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException error, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(error, notFound(), headers, status, request);
    }

    /** The same answer, for the case in which Spring is not looking for static files. */
    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException error, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(error, notFound(), headers, status, request);
    }

    /**
     * Every answer that the parent class builds goes through here.
     *
     * <p>Spring leaves the type out when an error has none, which the RFC allows. It is written
     * out anyway, so that all the errors of the API have the same fields.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getType() == null) {
            problem.setType(NO_SPECIFIC_TYPE);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    /**
     * Builds a 401. HTTP requires every 401 to say how to authenticate, in the
     * {@code WWW-Authenticate} header (RFC 9110).
     *
     * <p>The header names the scheme and nothing else. The standard of Bearer tokens allows
     * parameters that describe the error; they are left out so that the answer does not tell why
     * a token was rejected.
     */
    private static ResponseEntity<ProblemDetail> unauthorized(String detail) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem(HttpStatus.UNAUTHORIZED, detail));
    }

    private static ProblemDetail notFound() {
        return problem(HttpStatus.NOT_FOUND, "The requested resource was not found.");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(NO_SPECIFIC_TYPE);
        return problem;
    }

}
