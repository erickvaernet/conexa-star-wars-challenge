package io.github.erickvaernet.starwars.auth;

import jakarta.servlet.DispatcherType;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Access rules of the API: everything is closed unless a rule opens it.
 *
 * <p>A request that is added later and has no rule here requires a session. The public requests
 * are the ones listed in the specification: the login, the API documentation and the health
 * check.
 *
 * <p>A request has a session when its {@code Authorization: Bearer} header brings a token that
 * the login issued and that has not expired. The server keeps nothing between requests.
 *
 * <p>One request is kept for a role: only an administrator creates users. The role is read from
 * the token, where the login wrote it. The order is always the same: first the session (401),
 * then the role (403), and only then the content of the request (400).
 */
@Configuration(proxyBeanMethods = false)
// The access rules cannot work without the signing key. Declaring it here makes the token settings
// available wherever these rules are loaded, including the tests that load only the web layer.
@EnableConfigurationProperties(JwtProperties.class)
class SecurityConfiguration {

    /** The role that the creation of users is kept for, as the token and the database write it. */
    private static final String ADMIN = "ADMIN";

    /** The claim of the token where the login writes the role of the user. */
    private static final String ROLE_CLAIM = "role";

    @Bean
    SecurityFilterChain accessRules(
            HttpSecurity http, AuthenticationEntryPoint notAuthenticated, AccessDeniedHandler notAllowed)
            throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        // A request that the web server or these rules reject is handed over to
                        // /error to be answered. That second pass brings no token with it:
                        // without this rule it would be answered with 401, whatever the reason
                        // of the rejection. It opens nothing: only the server starts such a
                        // pass, and asking for /error directly is not one.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // The only request kept for a role. Every other one is for any user with a session.
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").hasRole(ADMIN)
                        .anyRequest().authenticated())
                // Spring Security reads the token of each request and validates it with tokenValidator().
                // A rejected token is answered here; a missing one, by the lines that follow. Both
                // use the same answer, so it does not tell why the request was rejected.
                .oauth2ResourceServer(tokens -> tokens
                        .jwt(token -> token.jwtAuthenticationConverter(roleFromTheToken()))
                        .authenticationEntryPoint(notAuthenticated))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(notAuthenticated)
                        // A session whose role does not allow the request. Without this line, the
                        // answer would have no body, and a header that describes the error.
                        .accessDeniedHandler(notAllowed))
                // The server keeps no session and uses no cookies: each request stands on its own.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF abuses a session kept in a cookie. There is none, so its protection only gets in the way.
                .csrf(AbstractHttpConfigurer::disable)
                // Spring Security adds a logout request by default. With no session there is nothing to log out of.
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * Answers a request that needs a session and has none, and a request whose token is rejected.
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
     * Answers a request whose session is valid and whose role does not allow it.
     *
     * <p>Like {@link #notAuthenticated}, it hands the error to the handlers that format the
     * errors of the controllers, so a 403 has the same Problem Details format as any other error.
     */
    @Bean
    AccessDeniedHandler notAllowed(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver errorHandlers) {
        return (request, response, error) -> errorHandlers.resolveException(request, response, null, error);
    }

    /**
     * Tells Spring Security where the role of the user is in a token.
     *
     * <p>By default it looks for permissions in the claims {@code scope} and {@code scp}, which
     * these tokens do not have. With this, it reads the claim {@code role} and names what it
     * finds the way its rules expect a role: {@code ADMIN} becomes {@code ROLE_ADMIN}, which is
     * what {@code hasRole("ADMIN")} asks for. A token without the claim gets no role.
     */
    private static JwtAuthenticationConverter roleFromTheToken() {
        JwtGrantedAuthoritiesConverter role = new JwtGrantedAuthoritiesConverter();
        role.setAuthoritiesClaimName(ROLE_CLAIM);
        role.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(role);
        return converter;
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
