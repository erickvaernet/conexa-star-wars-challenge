package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.JsonPath;
import io.github.erickvaernet.starwars.shared.UsernameAlreadyExistsException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Checks the request that creates a user: who is let in, which bodies are accepted, how each
 * outcome is answered and how the request is documented.
 *
 * <p>Only the web layer is loaded, with the real access rules, the real tokens, the real error
 * format and the generated API documentation; the rules of the creation are replaced. It needs
 * neither the database nor Docker.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfiguration.class, TokenIssuer.class})
// The API documentation is not part of what a test of the web layer loads by default.
@ImportAutoConfiguration({
    SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
class UserControllerTest {

    private static final String USERS = "/api/v1/users";

    /** The text of each field in the list of errors, as the specification writes it. */
    private static final String USERNAME_RULE =
            "must have 3 to 50 characters: lowercase letters, digits, '.', '_' or '-'";

    private static final String PASSWORD_RULE =
            "must have 8 to 72 characters, not only spaces, and at most 72 bytes in UTF-8";

    private static final String ROLE_RULE = "must be USER or ADMIN";

    private static final String VALID_BODY = """
            {"username": "leia", "password": "alderaan-1977"}
            """;

    private static final Instant CREATED_AT = Instant.parse("2026-11-16T14:03:27.512Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenIssuer issuer;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private UserCreationService service;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc).build();
    }

    @Test
    @DisplayName("An administrator creates a user: 201, with the user name, the role and the moment of creation, and no Location header")
    void administratorCreatesAUser() {
        when(service.create("leia", "alderaan-1977", null)).thenReturn(new UserResponse("leia", "USER", CREATED_AT));

        createAsAdmin(VALID_BODY)
                .expectStatus().isCreated()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().doesNotExist(HttpHeaders.LOCATION)
                .expectBody().json("""
                        {"username": "leia", "role": "USER", "createdAt": "2026-11-16T14:03:27.512Z"}
                        """, JsonCompareMode.STRICT);
    }

    @ParameterizedTest(name = "role {0}")
    @ValueSource(strings = {"USER", "ADMIN"})
    @DisplayName("The role that is sent reaches the rules of the creation as it is")
    void roleThatIsSentReachesTheRules(String role) {
        when(service.create("leia", "alderaan-1977", role)).thenReturn(new UserResponse("leia", role, CREATED_AT));

        createAsAdmin("{\"username\": \"leia\", \"password\": \"alderaan-1977\", \"role\": \"" + role + "\"}")
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.role").isEqualTo(role);
    }

    @Test
    @DisplayName("The password reaches the rules of the creation as it arrived, with its spaces")
    void passwordIsNotTrimmed() {
        when(service.create(any(), any(), any())).thenReturn(new UserResponse("leia", "USER", CREATED_AT));

        createAsAdmin("""
                {"username": "leia", "password": "  two words  ", "role": null}
                """).expectStatus().isCreated();

        verify(service).create("leia", "  two words  ", null);
    }

    @Test
    @DisplayName("Without a token, the request is answered with 401, and nothing is created")
    void requestWithoutATokenIsNotAuthenticated() {
        create(null, VALID_BODY)
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Authentication is required to access this resource.")
                .jsonPath("$.instance").isEqualTo(USERS);

        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bodiesOfAnyKind")
    @DisplayName("A common user is answered with 403 whatever the body: the role is checked before the content")
    void commonUserIsNotAllowed(String what, String body) {
        expectForbidden(create(issuer.issue("demo", "USER"), body));
    }

    /** A valid body, and bodies that an administrator would get a 400 for. */
    static Stream<Arguments> bodiesOfAnyKind() {
        return Stream.of(
                Arguments.of("a valid body", VALID_BODY),
                Arguments.of("three invalid fields",
                        "{\"username\": \"ab\", \"password\": \"1234567\", \"role\": \"ROOT\"}"),
                Arguments.of("an empty object", "{}"),
                Arguments.of("not JSON", "this is not JSON"),
                Arguments.of("no body", ""));
    }

    @Test
    @DisplayName("A common user is answered with 403 also when the body is not sent as JSON")
    void commonUserIsNotAllowedWhateverTheContentType() {
        expectForbidden(client.post().uri(USERS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issue("demo", "USER"))
                .contentType(MediaType.TEXT_PLAIN)
                .body("username=leia")
                .exchange());
    }

    @ParameterizedTest(name = "role \"{0}\" in the token")
    // Only the exact name of the role of the administrators opens the request.
    @ValueSource(strings = {"admin", "Admin", "ROOT", "ROLE_ADMIN", "ADMINISTRATOR"})
    @DisplayName("A token with a role that is not exactly ADMIN is answered with 403")
    void tokenWithAnotherRoleIsNotAllowed(String role) {
        expectForbidden(create(issuer.issue("someone", role), VALID_BODY));
    }

    @Test
    @DisplayName("A valid token that carries no role is answered with 403")
    void tokenWithoutARoleIsNotAllowed() {
        expectForbidden(create(tokenWithoutARole(), VALID_BODY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUserNames")
    @DisplayName("An invalid user name is in errors once, with the same text whichever of its rules it breaks")
    void invalidUserNameHasTheSameTextWhicheverRuleItBreaks(String what, String body) {
        expectInvalid(createAsAdmin(body), Map.of("field", "username", "message", USERNAME_RULE));
    }

    static Stream<Arguments> invalidUserNames() {
        return Stream.of(
                Arguments.of("missing", "{\"password\": \"alderaan-1977\"}"),
                Arguments.of("null", "{\"username\": null, \"password\": \"alderaan-1977\"}"),
                Arguments.of("empty", withUserName("")),
                Arguments.of("2 characters", withUserName("ab")),
                Arguments.of("51 characters", withUserName("u".repeat(51))),
                Arguments.of("a capital letter", withUserName("Leia")),
                Arguments.of("only capital letters", withUserName("LEIA")),
                Arguments.of("a space", withUserName("leia organa")),
                Arguments.of("a space at the end", withUserName("leia ")),
                Arguments.of("a letter with an accent", withUserName("leía")),
                Arguments.of("a slash", withUserName("leia/organa")),
                Arguments.of("an at sign", withUserName("leia@alderaan")),
                // Written as JSON writes a control character: a backslash, "u" and its code.
                Arguments.of("a line break at the end", withUserName("leia\\u000A")),
                Arguments.of("a null character", withUserName("le\\u0000ia")),
                Arguments.of("too long and with a capital letter", withUserName("U".repeat(51))));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"abc", "leia", "mon.mothma", "c-3po", "r2_d2", "1138", "...", "-_-"})
    @DisplayName("A user name of lowercase letters, digits, '.', '_' and '-' is accepted")
    void validUserNameIsAccepted(String username) {
        when(service.create(any(), any(), any())).thenReturn(new UserResponse(username, "USER", CREATED_AT));

        createAsAdmin(withUserName(username)).expectStatus().isCreated();

        verify(service).create(username, "alderaan-1977", null);
    }

    @Test
    @DisplayName("A user name of 50 characters, the longest that the database holds, is accepted")
    void longestUserNameIsAccepted() {
        String username = "u".repeat(50);
        when(service.create(any(), any(), any())).thenReturn(new UserResponse(username, "USER", CREATED_AT));

        createAsAdmin(withUserName(username)).expectStatus().isCreated();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPasswords")
    @DisplayName("An invalid password is in errors once, with the same text whichever of its rules it breaks")
    void invalidPasswordHasTheSameTextWhicheverRuleItBreaks(String what, String body) {
        expectInvalid(createAsAdmin(body), Map.of("field", "password", "message", PASSWORD_RULE));
    }

    static Stream<Arguments> invalidPasswords() {
        return Stream.of(
                Arguments.of("missing", "{\"username\": \"leia\"}"),
                Arguments.of("null", "{\"username\": \"leia\", \"password\": null}"),
                Arguments.of("empty", withPassword("")),
                Arguments.of("7 characters", withPassword("1234567")),
                Arguments.of("73 characters", withPassword("p".repeat(73))),
                Arguments.of("8 spaces", withPassword(" ".repeat(8))),
                Arguments.of("72 spaces", withPassword(" ".repeat(72))),
                Arguments.of("3 spaces: too short and only spaces", withPassword("   ")),
                Arguments.of("8 tabs", withPassword("\\t".repeat(8))),
                // 2 bytes each: 80 bytes in 40 characters.
                Arguments.of("40 letters with an accent", withPassword("é".repeat(40))),
                // 3 bytes each: 75 bytes in 25 characters.
                Arguments.of("25 euro signs", withPassword("€".repeat(25))),
                // 4 bytes each, and two characters as Java counts them: 76 bytes in 38 characters.
                Arguments.of("19 emojis", withPassword("🚀".repeat(19))),
                Arguments.of("100 letters with an accent: too long in characters and in bytes",
                        withPassword("é".repeat(100))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPasswords")
    @DisplayName("A password of 8 to 72 characters that fits in 72 bytes is accepted, whatever its characters")
    void validPasswordIsAccepted(String what, String password) {
        when(service.create(any(), any(), any())).thenReturn(new UserResponse("leia", "USER", CREATED_AT));

        client.post().uri(USERS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issue("admin", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", "leia", "password", password))
                .exchange()
                .expectStatus().isCreated();

        verify(service).create("leia", password, null);
    }

    static Stream<Arguments> validPasswords() {
        return Stream.of(
                Arguments.of("8 characters", "12345678"),
                Arguments.of("72 characters", "p".repeat(72)),
                Arguments.of("only digits", "00000000"),
                Arguments.of("spaces at both ends", "  two words  "),
                Arguments.of("7 spaces and a letter", "       x"),
                // 2 bytes each: 72 bytes in 36 characters.
                Arguments.of("36 letters with an accent", "é".repeat(36)),
                // 3 bytes each: 72 bytes in 24 characters.
                Arguments.of("24 euro signs", "€".repeat(24)),
                // 4 bytes each: 72 bytes in 36 characters, as Java counts them.
                Arguments.of("18 emojis", "🚀".repeat(18)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRoles")
    @DisplayName("A role that is sent and is not exactly USER or ADMIN is in errors, and it is not corrected")
    void invalidRoleIsRejected(String what, String body) {
        expectInvalid(createAsAdmin(body), Map.of("field", "role", "message", ROLE_RULE));
    }

    static Stream<Arguments> invalidRoles() {
        return Stream.of(
                Arguments.of("lowercase", withRole("\"admin\"")),
                Arguments.of("another name", withRole("\"ROOT\"")),
                Arguments.of("empty", withRole("\"\"")),
                Arguments.of("a space", withRole("\" \"")),
                Arguments.of("a space before", withRole("\" USER\"")),
                Arguments.of("a space after", withRole("\"ADMIN \"")),
                Arguments.of("both roles", withRole("\"USER ADMIN\"")),
                Arguments.of("the name that Spring Security gives it", withRole("\"ROLE_ADMIN\"")),
                // A number is read as its text.
                Arguments.of("a number", withRole("5")));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "{\"username\": null, \"password\": null, \"role\": \"\"}",
        "{\"username\": \"Leia\", \"password\": \"1234567\", \"role\": \"ROOT\"}"
    })
    @DisplayName("A body with the three fields invalid has the three in errors: password, role and username, in that order")
    void threeInvalidFieldsAreAllInErrors(String body) {
        createAsAdmin(body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors").value(List.class, errors -> assertThat(errors).containsExactly(
                        Map.of("field", "password", "message", PASSWORD_RULE),
                        Map.of("field", "role", "message", ROLE_RULE),
                        Map.of("field", "username", "message", USERNAME_RULE)));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("A body without fields has the two mandatory ones in errors, and not the role, which is optional")
    void bodyWithoutFieldsHasTheMandatoryOnesInErrors() {
        createAsAdmin("{}")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors").value(List.class, errors -> assertThat(errors).containsExactly(
                        Map.of("field", "password", "message", PASSWORD_RULE),
                        Map.of("field", "username", "message", USERNAME_RULE)));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("The answer to an invalid body repeats nothing of what was received")
    void invalidBodyRepeatsNothingOfWhatWasReceived() {
        String body = createAsAdmin("{\"username\": \"" + "X9".repeat(26) + "\", \"password\": \"" + "k4".repeat(37)
                + "\", \"role\": \"" + "Q7".repeat(5) + "\"}")
                .expectStatus().isBadRequest()
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).doesNotContain("X9X9").doesNotContain("k4k4").doesNotContain("Q7Q7");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "this is not JSON", "[]", "{\"username\": {}, \"password\": \"alderaan-1977\"}"})
    @DisplayName("A body that cannot be read has no errors: no field was checked")
    void bodyThatCannotBeReadHasNoErrors(String body) {
        String answer = createAsAdmin(body)
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class).returnResult().getResponseBody();
        Map<String, Object> problem = JsonPath.read(answer, "$");

        assertThat(problem).containsOnlyKeys("type", "title", "status", "detail", "instance");
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Fields that the request does not have are ignored")
    void fieldsThatTheRequestDoesNotHaveAreIgnored() {
        when(service.create(any(), any(), any())).thenReturn(new UserResponse("leia", "USER", CREATED_AT));

        createAsAdmin("""
                {"id": 1, "username": "leia", "password": "alderaan-1977", "createdAt": "1977-05-25T00:00:00Z"}
                """).expectStatus().isCreated();

        verify(service).create("leia", "alderaan-1977", null);
    }

    @Test
    @DisplayName("A user name that is already taken is answered with 409 and the fixed text")
    void userNameThatIsTakenIsAConflict() {
        when(service.create(any(), any(), any())).thenThrow(new UsernameAlreadyExistsException());

        createAsAdmin(VALID_BODY)
                .expectStatus().isEqualTo(409)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Conflict",
                          "status": 409,
                          "detail": "A user with that username already exists.",
                          "instance": "/api/v1/users"
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("The documentation shows the request in its own group, and says that it requires the role of the administrators")
    void documentationShowsTheRequestInItsGroup() {
        String documentation = apiDocumentation();
        Map<String, Object> request = JsonPath.read(documentation, "$.paths['/api/v1/users'].post");
        List<String> descriptionOfTheGroup = JsonPath.read(documentation, "$.tags[?(@.name=='Users')].description");

        assertThat(request).containsEntry("operationId", "createUser").containsEntry("tags", List.of("Users"));
        assertThat((String) request.get("summary")).isNotBlank();
        assertThat((String) request.get("description")).contains("`ADMIN`", "403");
        assertThat(descriptionOfTheGroup).hasSize(1).allSatisfy(text -> assertThat(text).contains("administrator"));
        // The padlock comes from the whole document: the request does not override it.
        assertThat(request).doesNotContainKey("security");
    }

    @Test
    @DisplayName("The documentation shows the body of the request, with its three fields and which ones are mandatory")
    void documentationShowsTheBody() {
        String documentation = apiDocumentation();
        String schema = JsonPath.read(documentation,
                "$.paths['/api/v1/users'].post.requestBody.content['application/json'].schema['$ref']");
        Map<String, Map<String, Object>> fields = JsonPath.read(documentation,
                "$.components.schemas['CreateUserRequest'].properties");
        List<String> required = JsonPath.read(documentation, "$.components.schemas['CreateUserRequest'].required");

        assertThat(schema).isEqualTo("#/components/schemas/CreateUserRequest");
        assertThat(fields).containsOnlyKeys("username", "password", "role");
        assertThat(required).containsExactlyInAnyOrder("username", "password");
        for (Map<String, Object> field : fields.values()) {
            assertThat(field).containsEntry("type", "string");
            assertThat((String) field.get("description")).isNotBlank();
        }
        assertThat(fields.get("role")).containsEntry("enum", List.of("USER", "ADMIN"));
    }

    @Test
    @DisplayName("The documentation shows the five answers of the request: the user as JSON, and the four errors as Problem Details")
    void documentationShowsTheAnswers() {
        String documentation = apiDocumentation();
        Map<String, Map<String, Object>> responses = JsonPath.read(documentation,
                "$.paths['/api/v1/users'].post.responses");

        assertThat(responses).containsOnlyKeys("201", "400", "401", "403", "409");
        for (Map<String, Object> response : responses.values()) {
            assertThat((String) response.get("description")).isNotBlank().isNotEqualTo("Created");
        }
        String created = JsonPath.read(documentation,
                "$.paths['/api/v1/users'].post.responses['201'].content['application/json'].schema['$ref']");
        assertThat(created).isEqualTo("#/components/schemas/UserResponse");
        Map<String, Map<String, Object>> fields = JsonPath.read(documentation,
                "$.components.schemas['UserResponse'].properties");
        assertThat(fields).containsOnlyKeys("username", "role", "createdAt");
        for (Map<String, Object> field : fields.values()) {
            assertThat((String) field.get("description")).isNotBlank();
        }
        assertThat(fields.get("createdAt")).containsEntry("type", "string").containsEntry("format", "date-time");
        // The 400 is the one that can say which fields are invalid.
        assertThat(schemaOfTheError(documentation, "400")).isEqualTo("#/components/schemas/ValidationProblemDetail");
        for (String error : List.of("401", "403", "409")) {
            assertThat(schemaOfTheError(documentation, error)).as(error)
                    .isEqualTo("#/components/schemas/ProblemDetail");
        }
    }

    private static String schemaOfTheError(String documentation, String status) {
        Map<String, Object> content = JsonPath.read(documentation,
                "$.paths['/api/v1/users'].post.responses['" + status + "'].content");
        assertThat(content).as(status).containsOnlyKeys("application/problem+json");
        return JsonPath.read(documentation, "$.paths['/api/v1/users'].post.responses['" + status + "']"
                + ".content['application/problem+json'].schema['$ref']");
    }

    private String apiDocumentation() {
        return client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    private RestTestClient.ResponseSpec createAsAdmin(String body) {
        return create(issuer.issue("admin", "ADMIN"), body);
    }

    /** Asks to create a user, with the body written as it is sent, and with this token if there is one. */
    private RestTestClient.ResponseSpec create(String token, String body) {
        RestTestClient.RequestBodySpec request = client.post().uri(USERS).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.body(body).exchange();
    }

    /** A body with a valid password and this user name, which is written into the JSON as it is. */
    private static String withUserName(String username) {
        return "{\"username\": \"" + username + "\", \"password\": \"alderaan-1977\"}";
    }

    /** A body with a valid user name and this password, which is written into the JSON as it is. */
    private static String withPassword(String password) {
        return "{\"username\": \"leia\", \"password\": \"" + password + "\"}";
    }

    /** A body with a valid user name and password, and this role, written as JSON. */
    private static String withRole(String role) {
        return "{\"username\": \"leia\", \"password\": \"alderaan-1977\", \"role\": " + role + "}";
    }

    /** Checks the 400 of a body with one invalid field, and that nothing was created. */
    private void expectInvalid(RestTestClient.ResponseSpec response, Map<String, String> invalidField) {
        response
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Invalid request content.")
                .jsonPath("$.instance").isEqualTo(USERS)
                .jsonPath("$.errors").value(List.class, errors -> assertThat(errors).containsExactly(invalidField));

        verifyNoInteractions(service);
    }

    /** Checks the 403 as a whole and strictly, and that nothing was created. */
    private void expectForbidden(RestTestClient.ResponseSpec response) {
        response
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectBody().json("""
                        {
                          "type": "about:blank",
                          "title": "Forbidden",
                          "status": 403,
                          "detail": "You do not have permission to perform this request.",
                          "instance": "/api/v1/users"
                        }
                        """, JsonCompareMode.STRICT);

        verifyNoInteractions(service);
    }

    /** A token signed with the key of the application and not expired, as the login issues it, without the role. */
    private String tokenWithoutARole() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("someone")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return NimbusJwtEncoder.withSecretKey(jwtProperties.signingKey()).algorithm(MacAlgorithm.HS256).build()
                .encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

}
