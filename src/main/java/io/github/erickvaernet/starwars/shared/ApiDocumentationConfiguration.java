package io.github.erickvaernet.starwars.shared;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
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
            """;

}
