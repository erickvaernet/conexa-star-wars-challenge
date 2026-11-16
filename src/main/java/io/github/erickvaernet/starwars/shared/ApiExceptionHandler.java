package io.github.erickvaernet.starwars.shared;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
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
 *
 * <p>A 400 that comes from a field of the body or from a parameter also says which one, in a list
 * called {@code errors}: {@link InvalidField} is each of its elements.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** The type of an error that has no type of its own, as the RFC names it. */
    private static final URI NO_SPECIFIC_TYPE = URI.create("about:blank");

    /** The text of every 404: an element and a route that do not exist are answered alike. */
    static final String NOT_FOUND = "The requested resource was not found.";

    /** The text of every 500. What failed goes to the log. */
    static final String UNEXPECTED = "An unexpected error occurred.";

    /** The text of a body with invalid fields. Which ones, and their rules, go in the list. */
    static final String INVALID_BODY = "Invalid request content.";

    /** The text of every 415. The one of Spring repeats the content type that the client sent. */
    static final String UNSUPPORTED_MEDIA_TYPE = "The content type of the request is not supported.";

    /**
     * The text of every 405, whoever answers it: this class, for a method that a route does not
     * have, or {@link RejectedRequestController}, for one that the web server has switched off.
     * The one of Spring repeats the method that the client sent.
     */
    static final String METHOD_NOT_SUPPORTED = "The method of the request is not supported.";

    /** The text of every 403: the session is valid, and its role does not allow the request. */
    static final String FORBIDDEN = "You do not have permission to perform this request.";

    /**
     * The text of the 429 of the login. It says neither how many attempts failed nor how many
     * are allowed: how long to wait goes in the {@code Retry-After} header.
     */
    static final String TOO_MANY_LOGIN_ATTEMPTS = "Too many failed login attempts. Try again later.";

    /** The name of the list that says which fields or parameters are invalid. */
    private static final String ERRORS = "errors";

    /**
     * A login with wrong credentials. The answer does not say whether the user name or the
     * password was wrong.
     */
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException error) {
        return unauthorized("Invalid username or password.");
    }

    /**
     * A login from an address that already failed too many times, or that has that many logins
     * being checked at this moment. It was not checked, so the answer is the same whether its
     * credentials were right or wrong.
     *
     * <p>{@code Retry-After} says, in seconds, when a login from that address is checked again.
     * There is no {@code WWW-Authenticate}: the client already knows how to authenticate, and
     * doing it again changes nothing until then.
     */
    @ExceptionHandler(TooManyLoginAttemptsException.class)
    ResponseEntity<ProblemDetail> handleTooManyLoginAttempts(TooManyLoginAttemptsException error) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(error.retryAfterSeconds()))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_LOGIN_ATTEMPTS));
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
     * A request from a user whose role does not allow it. Unlike a 401, the answer does not say
     * how to authenticate: the user already did, and doing it again changes nothing.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleNotAllowed(AccessDeniedException error) {
        return problem(HttpStatus.FORBIDDEN, FORBIDDEN);
    }

    /** A user that cannot be created because another one already has that name. */
    @ExceptionHandler(UsernameAlreadyExistsException.class)
    ProblemDetail handleUsernameAlreadyExists(UsernameAlreadyExistsException error) {
        return problem(HttpStatus.CONFLICT, "A user with that username already exists.");
    }

    /**
     * A parameter with a value that is not accepted. The answer names the parameter and the rule
     * it must follow, and never repeats the value that was received.
     *
     * <p>The list has one element, that parameter: the validation stops at the first invalid one.
     */
    @ExceptionHandler(InvalidParameterException.class)
    ProblemDetail handleInvalidParameter(InvalidParameterException error) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST,
                "Parameter '" + error.parameter() + "' " + error.requirement() + ".");
        problem.setProperty(ERRORS, List.of(new InvalidField(error.parameter(), error.requirement())));
        return problem;
    }

    /**
     * A parameter that is not correctly encoded: a "%" that is not followed by two hexadecimal
     * digits, as in {@code name=100%}, or bytes that are not UTF-8 text.
     *
     * <p>Tomcat finds it out when the parameters are first read, and by then the request is
     * already inside a controller: without this it would end as an unexpected error, a 500 for a
     * mistake of the client. Nothing goes to the log, as with any other invalid parameter, and
     * the text is fixed: the message of Tomcat repeats what the client sent.
     */
    @ExceptionHandler(org.apache.tomcat.util.http.InvalidParameterException.class)
    ProblemDetail handleParameterThatIsNotCorrectlyEncoded(
            org.apache.tomcat.util.http.InvalidParameterException error) {
        return problem(HttpStatus.BAD_REQUEST, "The request has a parameter that is not correctly encoded.");
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
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED);
    }

    /**
     * A body that was read and has fields that do not follow their rules. The detail is the same
     * for all of them; the list says which fields, each with the rule it must follow.
     *
     * <p>The text of a field is the {@code message} of its validation annotations, which the body
     * declares: the same text in every rule of a field, so that it does not depend on which rule
     * failed. The value that was received is never read here.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException error, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, INVALID_BODY);
        problem.setProperty(ERRORS, invalidFields(error.getFieldErrors()));
        return handleExceptionInternal(error, problem, headers, status, request);
    }

    /**
     * A body sent as something other than JSON. The text of Spring names the content type that
     * the client sent, so it is replaced. The headers are kept: they are written by the server,
     * and name the content types that the request accepts.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException error, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(
                error, problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_MEDIA_TYPE), headers, status, request);
    }

    /**
     * A method that the route does not have. The text of Spring names the method that the client
     * sent, so it is replaced. The headers are kept: they are written by the server, and
     * {@code Allow} names the methods that the route does have.
     */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException error, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return handleExceptionInternal(
                error, problem(HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_SUPPORTED), headers, status, request);
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

    /**
     * Turns the errors of the fields of a body into the list of the answer: each field once,
     * sorted by its name, so that the same body always gets the same answer.
     *
     * <p>A field that breaks several rules has one error for each. They all carry the same text,
     * so the first one is kept.
     */
    private static List<InvalidField> invalidFields(List<FieldError> errors) {
        Map<String, String> ruleOfEachField = new TreeMap<>();
        for (FieldError error : errors) {
            ruleOfEachField.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ruleOfEachField.entrySet().stream()
                .map(entry -> new InvalidField(entry.getKey(), entry.getValue()))
                .toList();
    }

    static ProblemDetail notFound() {
        return problem(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    /** Builds an error with a fixed text and all the fields that every error of the API has. */
    static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(NO_SPECIFIC_TYPE);
        return problem;
    }

}
