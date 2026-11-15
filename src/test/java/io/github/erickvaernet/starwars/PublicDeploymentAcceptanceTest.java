package io.github.erickvaernet.starwars;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.yaml.snakeyaml.Yaml;

/**
 * Acceptance test for REQ-20: the application works behind the service that publishes it.
 *
 * <p>The public service answers HTTPS and passes each request on to the application as plain
 * HTTP, with headers that tell how the request arrived. The application runs here on a real HTTP
 * port against a real PostgreSQL, and the test plays the part of that service: it sends those
 * headers itself.
 *
 * <p>The application is configured as in the public deployment. The variables are not copied
 * here: they are read from {@code render.yaml}, the file that describes the deployment, so this
 * test checks the file that is really deployed and the two cannot drift apart. Only the variables
 * whose value is written in that file are read. The secrets are not in it: the database comes
 * from the test container and the signing key from the settings of the tests.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ContextConfiguration(initializers = PublicDeploymentAcceptanceTest.DeploymentVariables.class)
class PublicDeploymentAcceptanceTest {

    /** The file that describes the public deployment, at the root of the repository. */
    private static final Path DEPLOYMENT_FILE = Path.of("render.yaml");

    /** The name the public service would be reached by. A reserved name: nobody answers there. */
    private static final String PUBLIC_NAME = "star-wars.example.com";

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("CA-20.1: behind a proxy that answers HTTPS, the documentation declares its server with https and the public name")
    void documentationDeclaresItsServerWithHttpsAndThePublicName() {
        RestTestClient client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        String documentation = client.get().uri("/v3/api-docs")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", PUBLIC_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();

        // Swagger UI sends its requests to this address: with "http://" the browser would block
        // them, because the page itself was loaded over HTTPS.
        List<String> servers = JsonPath.read(documentation, "$.servers[*].url");
        assertThat(servers).containsExactly("https://" + PUBLIC_NAME);
    }

    /**
     * Gives the application the variables of the deployment file before it starts.
     *
     * <p>They are added as environment variables, not as plain settings, because that is how the
     * public service passes them. It matters: a name such as {@code SERVER_FORWARD_HEADERS_STRATEGY}
     * is matched to its setting only when it comes from the environment.
     */
    static class DeploymentVariables implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(variablesWrittenInTheDeploymentFile())
                    .applyTo(context.getEnvironment(), TestPropertyValues.Type.SYSTEM_ENVIRONMENT);
        }

    }

    /** Reads the variables of the web service that have their value in the file. */
    private static Map<String, String> variablesWrittenInTheDeploymentFile() {
        Object deployment;
        try (Reader reader = Files.newBufferedReader(DEPLOYMENT_FILE)) {
            deployment = new Yaml().load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("The deployment file could not be read: " + DEPLOYMENT_FILE, e);
        }

        // A variable without "value" is a secret: it is typed in, or generated, on the service.
        List<Map<String, Object>> written = JsonPath.read(deployment, "$.services[0].envVars[?(@.value)]");

        Map<String, String> variables = new HashMap<>();
        for (Map<String, Object> variable : written) {
            variables.put(String.valueOf(variable.get("key")), String.valueOf(variable.get("value")));
        }
        return variables;
    }

}
