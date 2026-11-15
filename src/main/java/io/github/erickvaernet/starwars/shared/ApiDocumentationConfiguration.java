package io.github.erickvaernet.starwars.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What the API documentation says about the API as a whole: what it is, its version and how to
 * authenticate. Each controller documents its own requests.
 *
 * <p>The documentation requires the token in every request unless the request says otherwise,
 * the same way the access rules close everything unless a rule opens it. A request that is added
 * later is shown with its padlock, and Swagger UI sends it the token, with nothing to remember.
 * The login, which is public, declares that it requires nothing.
 *
 * <p>This class only describes. It opens and closes nothing: the access rules do that.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "Star Wars API",
                // Not written here: the build puts the version of the project in the settings, and
                // the documentation library reads the setting that this text names.
                version = "${api.version}",
                description = ApiDocumentationConfiguration.DESCRIPTION),
        security = @SecurityRequirement(name = ApiDocumentationConfiguration.BEARER))
@SecurityScheme(
        name = ApiDocumentationConfiguration.BEARER,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        // Shown in the Authorize dialog of Swagger UI.
        description = "The token that `POST /api/v1/auth/login` returns in `accessToken`. Paste only the token: "
                + "the word `Bearer` is added for you.")
class ApiDocumentationConfiguration {

    /** The name by which the documentation refers to the security scheme. */
    static final String BEARER = "bearerAuth";

    /** Swagger UI shows it at the top of the page, with its format. */
    static final String DESCRIPTION = """
            Lists the people, films, starships and vehicles of Star Wars, with the data of \
            [SWAPI](https://www.swapi.tech): one page at a time, filtered by identifier or by name, \
            and the complete data of each element.

            **How to try it**

            1. Run `POST /api/v1/auth/login` with a test user.
            2. Copy the `accessToken` of the answer.
            3. Press **Authorize**, paste the token and confirm.
            4. Run any list or detail: the token is sent with each request, until it expires, \
            60 minutes later by default.

            **Test users**

            There are two, one of each role: `demo`, a common user, and `admin`, an administrator. \
            Both can use the lists and the details. Only `admin` can create users, with \
            `POST /api/v1/users`: the same request answers 403 to `demo`.

            **Answers**

            Every answer is JSON, and every error has the Problem Details format: the examples of \
            each request are the answers that the API gives. Two errors are the same for every \
            request and are not repeated in each one: a request whose `Accept` header does not \
            admit JSON is answered with 406, and a body that is not sent as JSON, by its \
            `Content-Type` header, with 415.
            """;

    /** The content type of an error of the API. */
    private static final String PROBLEM = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    /**
     * Completes the example of each error with its {@code instance}: the address of the request
     * that the example belongs to.
     *
     * <p>An error says which address was asked for, and the same errors are documented once for
     * several requests: the ones of a list are the same for the four lists. So the examples are
     * written without that field, and it is added here, for each request, where the answer has
     * it: after the detail.
     */
    @Bean
    OpenApiCustomizer addressOfEachErrorExample() {
        return documentation -> documentation.getPaths().forEach((path, requests) ->
                requests.readOperations().forEach(request ->
                        request.getResponses().forEach((status, response) -> {
                            MediaType error = response.getContent() == null ? null : response.getContent().get(PROBLEM);
                            if (error != null && error.getExample() != null) {
                                error.setExample(withInstance(error.getExample(), addressOf(path, status)));
                            }
                        })));
    }

    /** The same example, with the address that was asked for after its detail. */
    private static Map<String, Object> withInstance(Object example, String address) {
        Map<String, Object> complete = new LinkedHashMap<>();
        fieldsOf(example).forEach((field, value) -> {
            complete.put(field, value);
            if (field.equals("detail")) {
                complete.put("instance", address);
            }
        });
        return complete;
    }

    /** The fields of an example, in the order in which it was written. */
    private static Map<String, Object> fieldsOf(Object example) {
        // The documentation library reads an example into its own JSON types, with its own reader.
        return Json.mapper().convertValue(example, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    /**
     * The address of a request as a client would write it: a route with an identifier gets one
     * that matches the error, because the same route answers a 400 to an identifier that is not
     * one, a 404 to one that does not exist and any other error to one that does.
     */
    private static String addressOf(String path, String status) {
        String identifier = switch (status) {
            case "400" -> "abc";
            case "404" -> "9999";
            default -> "1";
        };
        return path.replace("{id}", identifier);
    }

}
