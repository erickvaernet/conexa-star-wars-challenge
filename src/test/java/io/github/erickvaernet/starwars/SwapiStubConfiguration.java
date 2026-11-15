package io.github.erickvaernet.starwars;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Simulated SWAPI for the tests, so that none of them calls the real service.
 *
 * <p>It answers with real responses recorded from SWAPI, kept under
 * {@code src/test/resources/swapi}: the {@code mappings} folder says which request each recording
 * answers, and the {@code __files} folder holds the recorded bodies. A request without a
 * recording gets a 404, so a test cannot depend on the real service by accident.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SwapiStubConfiguration {

    /** Classpath folder with the recordings. */
    private static final String RECORDINGS = "swapi";

    /** Starts the stub on a free port and stops it when the test context closes. */
    @Bean(initMethod = "start", destroyMethod = "stop")
    WireMockServer swapiStub() {
        return new WireMockServer(options().dynamicPort().usingFilesUnderClasspath(RECORDINGS));
    }

    /**
     * Points the application to the stub through {@code SWAPI_BASE_URL}, the same variable that
     * holds the address of the real SWAPI outside the tests.
     */
    @Bean
    DynamicPropertyRegistrar swapiStubProperties(WireMockServer swapiStub) {
        return registry -> registry.add("SWAPI_BASE_URL", () -> swapiStub.baseUrl() + "/api");
    }

}
