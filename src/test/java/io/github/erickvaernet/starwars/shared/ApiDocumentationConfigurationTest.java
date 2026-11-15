package io.github.erickvaernet.starwars.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Checks what the API documentation says about the API as a whole: what it is, its version and
 * how to authenticate.
 *
 * <p>Only the web layer is loaded, without any controller: what is checked here does not depend
 * on the requests. The access rules are left out, because they have their own tests. It needs
 * neither the database nor Docker.
 */
@WebMvcTest(useDefaultFilters = false)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiDocumentationConfiguration.class)
// The API documentation is not part of what a test of the web layer loads by default.
@ImportAutoConfiguration({
    SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
class ApiDocumentationConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    private String documentation;

    @BeforeEach
    void setUp() {
        documentation = RestTestClient.bindTo(mockMvc).build()
                .get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    @Test
    @DisplayName("The documentation has the title of the API and says how to try it: log in, then Authorize")
    void documentationHasTheTitleAndSaysHowToTryIt() {
        String title = JsonPath.read(documentation, "$.info.title");
        String description = JsonPath.read(documentation, "$.info.description");

        assertThat(title).isEqualTo("Star Wars API");
        assertThat(description).contains("POST /api/v1/auth/login", "accessToken", "Authorize");
    }

    @Test
    @DisplayName("The documentation says that there are two test users, and that creating users needs the administrator")
    void documentationSaysWhichTestUserCanCreateUsers() {
        String description = JsonPath.read(documentation, "$.info.description");

        assertThat(description).contains("`demo`", "`admin`", "POST /api/v1/users");
    }

    @Test
    @DisplayName("The version is the one of the project, taken from the build and not written a second time")
    void versionIsTheOneOfTheProject() {
        String version = JsonPath.read(documentation, "$.info.version");

        // The build writes the version of the project where the settings have a marker. A marker
        // that was not replaced, or the default version of the documentation ("v0"), fails here.
        assertThat(version).matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
    }

    @Test
    @DisplayName("The documentation declares one security scheme: HTTP Bearer, with a JWT as the token")
    void documentationDeclaresTheBearerScheme() {
        Map<String, Map<String, Object>> schemes = JsonPath.read(documentation, "$.components.securitySchemes");

        assertThat(schemes).containsOnlyKeys("bearerAuth");
        assertThat(schemes.get("bearerAuth"))
                .containsEntry("type", "http")
                .containsEntry("scheme", "bearer")
                .containsEntry("bearerFormat", "JWT");
        // What the Authorize dialog shows: where the token comes from, and to paste it alone.
        assertThat((String) schemes.get("bearerAuth").get("description"))
                .contains("POST /api/v1/auth/login", "accessToken", "Bearer");
    }

    @Test
    @DisplayName("Every request requires the token unless it says otherwise: the requirement is on the whole document")
    void everyRequestRequiresTheTokenByDefault() {
        List<Map<String, List<String>>> security = JsonPath.read(documentation, "$.security");

        assertThat(security).containsExactly(Map.of("bearerAuth", List.of()));
    }

}
