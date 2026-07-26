package com.orbisflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orbisflow.auth.api.AuthController;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {
    private static final String JWT_SECRET =
            "b3JiaXNmbG93LWxvY2FsLWp3dC1zZWNyZXQtbXVzdC1iZS0zMi1ieXRlcw==";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("orbisflow_test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "orbisflow_app");
        registry.add("spring.datasource.password", () -> "test_app_password");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add(
                "spring.flyway.placeholders.applicationDatabasePassword",
                () -> "test_app_password");
        registry.add("orbisflow.auth.secure-cookies", () -> "true");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void successfulLoginReturnsCookiesAndExpectedJwtClaims() throws Exception {
        MvcResult result = login("employee1", "OrbisFlow123!");
        Cookie session = result.getResponse().getCookie(AuthController.SESSION_COOKIE);
        Cookie csrf = result.getResponse().getCookie(AuthController.CSRF_COOKIE);
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getSecure()).isTrue();
        assertThat(session.getPath()).isEqualTo("/api");
        assertThat(session.getMaxAge()).isEqualTo(28_800);
        assertThat(csrf).isNotNull();
        assertThat(csrf.isHttpOnly()).isFalse();
        assertThat(csrf.getSecure()).isTrue();
        assertThat(csrf.getPath()).isEqualTo("/");

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(JWT_SECRET)))
                .build().parseSignedClaims(session.getValue()).getPayload();
        assertThat(claims.getSubject())
                .isEqualTo("30000000-0000-0000-0000-000000000001");
        assertThat(claims.get("role", String.class)).isEqualTo("employee");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    static List<Object[]> invalidCredentials() {
        return List.of(
                new Object[]{"employee1", "wrong-password"},
                new Object[]{"does-not-exist", "OrbisFlow123!"});
    }

    @ParameterizedTest
    @MethodSource("invalidCredentials")
    void invalidCredentialsAreIndistinguishable(String login, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(login, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.error.message", is("The supplied credentials are invalid.")))
                .andExpect(jsonPath("$.correlation_id").exists());
    }

    @Test
    void currentUserReturnsOnlyJwtSubjectUser() throws Exception {
        LoginCookies cookies = loginCookies();
        mvc.perform(get("/api/v1/users/me").cookie(cookies.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("30000000-0000-0000-0000-000000000001")))
                .andExpect(jsonPath("$.login_identifier", is("employee1")))
                .andExpect(jsonPath("$.role", is("employee")))
                .andExpect(jsonPath("$.manager_id",
                        is("10000000-0000-0000-0000-000000000001")));
    }

    @Test
    void currentUserWithoutCookieIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("AUTH_REQUIRED")));
    }

    @Test
    void logoutClearsBothCookies() throws Exception {
        LoginCookies cookies = loginCookies();
        mvc.perform(post("/api/v1/auth/logout")
                        .cookie(cookies.session(), cookies.csrf())
                        .header("X-XSRF-TOKEN", cookies.csrf().getValue()))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(AuthController.SESSION_COOKIE, 0))
                .andExpect(cookie().path(AuthController.SESSION_COOKIE, "/api"))
                .andExpect(cookie().maxAge(AuthController.CSRF_COOKIE, 0))
                .andExpect(cookie().path(AuthController.CSRF_COOKIE, "/"));
    }

    @Test
    void tamperedJwtIsUnauthorized() throws Exception {
        LoginCookies cookies = loginCookies();
        String token = cookies.session().getValue();
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'a' ? 'b' : 'a';
        Cookie tampered = new Cookie(
                AuthController.SESSION_COOKIE,
                token.substring(0, signatureStart)
                        + replacement
                        + token.substring(signatureStart + 1));
        mvc.perform(get("/api/v1/users/me").cookie(tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("AUTH_REQUIRED")));
    }

    @Test
    void publicLoginIgnoresAStaleOrTamperedSessionCookie() throws Exception {
        Cookie stale = new Cookie(AuthController.SESSION_COOKIE, "not-a-valid-jwt");
        mvc.perform(post("/api/v1/auth/login")
                        .cookie(stale)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("employee1", "OrbisFlow123!")))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(AuthController.SESSION_COOKIE));
    }

    @Test
    void stateChangingRequestRejectsMissingAndMismatchedCsrf() throws Exception {
        LoginCookies cookies = loginCookies();
        mvc.perform(post("/api/v1/test/csrf").cookie(cookies.session(), cookies.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("CSRF_INVALID")));
        mvc.perform(post("/api/v1/test/csrf")
                        .cookie(cookies.session(), cookies.csrf())
                        .header("X-XSRF-TOKEN", "mismatched"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("CSRF_INVALID")));
    }

    @Test
    void validCsrfAllowsStateChangingRequest() throws Exception {
        LoginCookies cookies = loginCookies();
        mvc.perform(post("/api/v1/test/csrf")
                        .cookie(cookies.session(), cookies.csrf())
                        .header("X-XSRF-TOKEN", cookies.csrf().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));
    }

    @Test
    void flywayMigratedFullSchemaAndRestrictedAuditGrants() {
        Integer tableCount = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'users', 'requests', 'documents', 'extracted_invoice_data',
                    'invoice_line_items', 'notifications', 'audit_log')
                """, Integer.class);
        Boolean canInsert = jdbc.queryForObject(
                "SELECT has_table_privilege('orbisflow_app', 'audit_log', 'INSERT')",
                Boolean.class);
        Boolean canUpdate = jdbc.queryForObject(
                "SELECT has_table_privilege('orbisflow_app', 'audit_log', 'UPDATE')",
                Boolean.class);
        Boolean canDelete = jdbc.queryForObject(
                "SELECT has_table_privilege('orbisflow_app', 'audit_log', 'DELETE')",
                Boolean.class);
        assertThat(tableCount).isEqualTo(7);
        assertThat(flyway.info().applied().length).isEqualTo(3);
        assertThat(canInsert).isTrue();
        assertThat(canUpdate).isFalse();
        assertThat(canDelete).isFalse();
    }

    private MvcResult login(String login, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(login, password)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(AuthController.SESSION_COOKIE))
                .andExpect(cookie().exists(AuthController.CSRF_COOKIE))
                .andExpect(cookie().sameSite(
                        AuthController.SESSION_COOKIE, containsString("Strict")))
                .andExpect(jsonPath("$.login_identifier", is(login)))
                .andReturn();
    }

    private LoginCookies loginCookies() throws Exception {
        MvcResult result = login("employee1", "OrbisFlow123!");
        return new LoginCookies(
                result.getResponse().getCookie(AuthController.SESSION_COOKIE),
                result.getResponse().getCookie(AuthController.CSRF_COOKIE));
    }

    private String loginBody(String login, String password) {
        return """
                {"login_identifier":"%s","password":"%s"}
                """.formatted(login, password);
    }

    private record LoginCookies(Cookie session, Cookie csrf) {
    }
}
