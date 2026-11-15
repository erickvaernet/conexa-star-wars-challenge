package io.github.erickvaernet.starwars.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Access rules of the API: everything is closed unless a rule opens it.
 *
 * <p>A request that is added later and has no rule here requires a session. The public requests
 * are the ones listed in the specification: the login, the API documentation and the health
 * check.
 */
@Configuration(proxyBeanMethods = false)
// The access rules cannot work without the signing key. Declaring it here makes the token settings
// available wherever these rules are loaded, including the tests that load only the web layer.
@EnableConfigurationProperties(JwtProperties.class)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain accessRules(HttpSecurity http, AuthenticationEntryPoint notAuthenticated) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(notAuthenticated))
                // The server keeps no session and uses no cookies: each request stands on its own.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF abuses a session kept in a cookie. There is none, so its protection only gets in the way.
                .csrf(AbstractHttpConfigurer::disable)
                // Spring Security adds a logout request by default. With no session there is nothing to log out of.
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * Answers a request that needs a session and has none.
     *
     * <p>It hands the error to the handlers that format the errors of the controllers, so a 401
     * from the access rules has the same Problem Details format as any other error.
     */
    @Bean
    AuthenticationEntryPoint notAuthenticated(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errorHandlers) {
        return (request, response, error) -> errorHandlers.resolveException(request, response, null, error);
    }

    /**
     * Validates the token of a request: its signature and its expiry.
     *
     * <p>It accepts only HS256 and the key that the login signs with. The validation itself is
     * done by Spring Security.
     */
    @Bean
    JwtDecoder tokenValidator(JwtProperties properties) {
        NimbusJwtDecoder validator = NimbusJwtDecoder
                .withSecretKey(properties.signingKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Spring Security accepts a token up to 60 seconds after it expired, to allow for servers
        // whose clocks differ. Here the same server issues and validates, so there is no tolerance.
        validator.setJwtValidator(new JwtTimestampValidator(Duration.ZERO));
        return validator;
    }

    /** Passwords are stored and compared as BCrypt hashes. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
