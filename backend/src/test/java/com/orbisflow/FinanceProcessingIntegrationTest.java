package com.orbisflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbisflow.auth.api.AuthController;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FinanceProcessingIntegrationTest {
    private static final UUID MANAGER_ONE =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID FINANCE_ONE =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID FINANCE_TWO =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID EMPLOYEE_ONE =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE_TWO =
            UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("orbisflow_finance_test")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
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
    @Autowired ObjectMapper objectMapper;

    @Test
    void financeProcessesPaidRequestAtomically() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE,
                "finance_review",
                4,
                "125.50",
                Instant.parse("2026-07-29T08:00:00Z"),
                null);
        LoginCookies finance = login("finance1");

        performProcess(finance, requestId, """
                {"expected_version":4,"payment_status":"paid"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("processed")))
                .andExpect(jsonPath("$.version", is(5)))
                .andExpect(jsonPath("$.current_owner_role").doesNotExist())
                .andExpect(jsonPath("$.processing.payment_status", is("paid")))
                .andExpect(jsonPath(
                        "$.processing.processed_by_user_id",
                        is(FINANCE_ONE.toString())))
                .andExpect(jsonPath("$.processing.processed_at").isNotEmpty());

        Map<String, Object> processing = jdbc.queryForMap("""
                SELECT status::text, version, payment_status::text,
                       processed_by_user_id, processed_at
                FROM requests WHERE id = ?
                """, requestId);
        assertThat(processing.get("status")).isEqualTo("processed");
        assertThat(processing.get("version")).isEqualTo(5L);
        assertThat(processing.get("payment_status")).isEqualTo("paid");
        assertThat(processing.get("processed_by_user_id")).isEqualTo(FINANCE_ONE);
        assertThat(processing.get("processed_at")).isNotNull();
        assertThat(count("""
                SELECT count(*) FROM audit_log
                WHERE request_id = ? AND event_type = 'processing'
                """, requestId)).isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM notifications
                WHERE request_id = ?
                  AND user_id = ?
                  AND type = 'processed'
                """, requestId, EMPLOYEE_ONE)).isEqualTo(1);

        mvc.perform(get("/api/v1/requests/{id}", requestId)
                        .cookie(finance.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processing.payment_status", is("paid")));
        mvc.perform(get("/api/v1/requests/{id}/audit", requestId)
                        .cookie(finance.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].event_type", is("processing")))
                .andExpect(jsonPath("$.items[0].context.payment_status", is("paid")));
    }

    @Test
    void anyFinanceUserCanProcessScheduledRequest() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_TWO,
                "finance_review",
                1,
                "65.00",
                Instant.now(),
                null);
        LoginCookies finance = login("finance2");

        performProcess(finance, requestId, """
                {"expected_version":1,"payment_status":"scheduled"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("processed")))
                .andExpect(jsonPath("$.processing.payment_status", is("scheduled")))
                .andExpect(jsonPath(
                        "$.processing.processed_by_user_id",
                        is(FINANCE_TWO.toString())));
    }

    @Test
    void processRejectsMissingInvalidAndClientDerivedFields() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE,
                "finance_review",
                0,
                null,
                Instant.now(),
                null);
        LoginCookies finance = login("finance1");

        assertInvalidProcess(finance, requestId, """
                {"expected_version":0}
                """);
        assertInvalidProcess(finance, requestId, """
                {"expected_version":0,"payment_status":"partial"}
                """);
        assertInvalidProcess(finance, requestId, """
                {"expected_version":0,"payment_status":"PAID"}
                """);
        assertInvalidProcess(finance, requestId, """
                {
                  "expected_version":0,
                  "payment_status":"paid",
                  "processed_by_user_id":"20000000-0000-0000-0000-000000000002"
                }
                """);
        assertInvalidProcess(finance, requestId, """
                {
                  "expected_version":0,
                  "payment_status":"paid",
                  "processed_at":"2026-07-29T08:00:00Z"
                }
                """);
        assertThat(requestStatus(requestId)).isEqualTo("finance_review");
    }

    @Test
    void repeatedOrWrongStateProcessingReturnsStateConflict() throws Exception {
        UUID eligible = insertRequest(
                EMPLOYEE_ONE,
                "finance_review",
                0,
                null,
                Instant.now(),
                null);
        UUID preApproval = insertRequest(
                EMPLOYEE_ONE,
                "manager_review",
                0,
                null,
                Instant.now(),
                null);
        LoginCookies finance = login("finance1");

        performProcess(finance, eligible, """
                {"expected_version":0,"payment_status":"paid"}
                """).andExpect(status().isOk());
        performProcess(finance, eligible, """
                {"expected_version":1,"payment_status":"paid"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("STATE_CONFLICT")));
        performProcess(finance, preApproval, """
                {"expected_version":0,"payment_status":"scheduled"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("STATE_CONFLICT")));
    }

    @Test
    void employeeAndManagerCannotProcessFinanceRequest() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE,
                "finance_review",
                0,
                null,
                Instant.now(),
                null);

        for (String login : new String[] {"employee1", "manager1"}) {
            LoginCookies wrongRole = login(login);
            performProcess(wrongRole, requestId, """
                    {"expected_version":0,"payment_status":"paid"}
                    """)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("ACCESS_DENIED")));
            mvc.perform(get("/api/v1/dashboards/finance/requests")
                            .cookie(wrongRole.session()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("ACCESS_DENIED")));
        }
        assertThat(requestStatus(requestId)).isEqualTo("finance_review");
    }

    @Test
    void financeCannotReadRequestOrAuditBeforeManagerApproval() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE,
                "manager_review",
                0,
                null,
                Instant.now(),
                null);
        LoginCookies finance = login("finance1");

        mvc.perform(get("/api/v1/requests/{id}", requestId)
                        .cookie(finance.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));
        mvc.perform(get("/api/v1/requests/{id}/audit", requestId)
                        .cookie(finance.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    void financeDashboardUsesDistinctQueueAndProcessedDefaultOrders() throws Exception {
        UUID queueOldest = insertRequest(
                EMPLOYEE_ONE,
                "finance_review",
                1,
                "40.00",
                Instant.parse("2026-07-29T08:00:00Z"),
                null);
        UUID queueNewest = insertRequest(
                EMPLOYEE_TWO,
                "finance_review",
                1,
                "90.00",
                Instant.parse("2026-07-29T09:00:00Z"),
                null);
        UUID processedMostRecent = insertRequest(
                EMPLOYEE_ONE,
                "processed",
                2,
                "120.00",
                Instant.parse("2026-07-29T08:30:00Z"),
                Instant.parse("2026-07-29T12:00:00Z"));
        UUID processedOlder = insertRequest(
                EMPLOYEE_TWO,
                "processed",
                2,
                "75.00",
                Instant.parse("2026-07-29T10:00:00Z"),
                Instant.parse("2026-07-29T11:00:00Z"));
        insertRequest(
                EMPLOYEE_ONE,
                "manager_review",
                0,
                "500.00",
                Instant.parse("2026-07-29T07:00:00Z"),
                null);
        LoginCookies finance = login("finance1");

        mvc.perform(get("/api/v1/dashboards/finance/requests")
                        .queryParam("page", "0")
                        .queryParam("size", "1")
                        .cookie(finance.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is(queueOldest.toString())))
                .andExpect(jsonPath("$.total_elements", is(2)))
                .andExpect(jsonPath("$.sort.field", is("updated_at")))
                .andExpect(jsonPath("$.sort.direction", is("asc")));
        mvc.perform(get("/api/v1/dashboards/finance/requests")
                        .queryParam("page", "1")
                        .queryParam("size", "1")
                        .cookie(finance.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id", is(queueNewest.toString())));

        mvc.perform(get("/api/v1/dashboards/finance/requests")
                        .queryParam("status", "processed")
                        .queryParam("page", "0")
                        .queryParam("size", "1")
                        .cookie(finance.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath(
                        "$.items[0].id",
                        is(processedMostRecent.toString())))
                .andExpect(jsonPath("$.total_elements", is(2)))
                .andExpect(jsonPath("$.sort.field", is("processed_at")))
                .andExpect(jsonPath("$.sort.direction", is("desc")));
        mvc.perform(get("/api/v1/dashboards/finance/requests")
                        .queryParam("status", "processed")
                        .queryParam("page", "1")
                        .queryParam("size", "1")
                        .cookie(finance.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id", is(processedOlder.toString())));
    }

    @Test
    void staleProcessingVersionReturnsVersionConflict() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE,
                "finance_review",
                5,
                null,
                Instant.now(),
                null);
        LoginCookies finance = login("finance1");

        performProcess(finance, requestId, """
                {"expected_version":4,"payment_status":"paid"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("VERSION_CONFLICT")));
        assertThat(requestStatus(requestId)).isEqualTo("finance_review");
    }

    private UUID insertRequest(
            UUID employeeId,
            String status,
            long version,
            String totalAmount,
            Instant updatedAt,
            Instant processedAt) {
        UUID requestId = UUID.randomUUID();
        String paymentStatus = status.equals("processed") ? "paid" : null;
        UUID processedBy = status.equals("processed") ? FINANCE_ONE : null;
        jdbc.update("""
                INSERT INTO requests (
                    id, employee_id, manager_id, status, version,
                    manager_decision, manager_decided_by_user_id,
                    manager_decided_at, rejection_reason,
                    payment_status, processed_by_user_id, processed_at,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?::request_status, ?,
                    ?, ?, ?, NULL,
                    ?::payment_status, ?, ?,
                    ?, ?
                )
                """,
                requestId,
                employeeId,
                MANAGER_ONE,
                status,
                version,
                status.equals("manager_review") ? null : "approved",
                status.equals("manager_review") ? null : MANAGER_ONE,
                status.equals("manager_review")
                        ? null
                        : Timestamp.from(updatedAt.minusSeconds(60)),
                paymentStatus,
                processedBy,
                processedAt == null ? null : Timestamp.from(processedAt),
                Timestamp.from(updatedAt.minusSeconds(3600)),
                Timestamp.from(updatedAt));
        if (totalAmount != null) {
            jdbc.update("""
                    INSERT INTO extracted_invoice_data (
                        id, request_id, schema_version, extraction_status,
                        vendor, total_amount, invoice_date, validation_flags
                    ) VALUES (?, ?, '1', 'succeeded', 'Finance Vendor',
                              ?, '2026-07-29', '[]'::jsonb)
                    """, UUID.randomUUID(), requestId, new BigDecimal(totalAmount));
        }
        return requestId;
    }

    private void assertInvalidProcess(
            LoginCookies finance,
            UUID requestId,
            String body) throws Exception {
        performProcess(finance, requestId, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_REQUEST")));
    }

    private org.springframework.test.web.servlet.ResultActions performProcess(
            LoginCookies cookies,
            UUID requestId,
            String body) throws Exception {
        return mvc.perform(post("/api/v1/requests/{id}/process", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(cookies.session(), cookies.csrf())
                .header("X-XSRF-TOKEN", cookies.csrf().getValue()));
    }

    private LoginCookies login(String login) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "login_identifier", login,
                                "password", "OrbisFlow123!"))))
                .andExpect(status().isOk())
                .andReturn();
        return new LoginCookies(
                result.getResponse().getCookie(AuthController.SESSION_COOKIE),
                result.getResponse().getCookie(AuthController.CSRF_COOKIE));
    }

    private int count(String sql, Object... arguments) {
        Integer count = jdbc.queryForObject(sql, Integer.class, arguments);
        return count == null ? 0 : count;
    }

    private String requestStatus(UUID requestId) {
        return jdbc.queryForObject(
                "SELECT status::text FROM requests WHERE id = ?",
                String.class,
                requestId);
    }

    private record LoginCookies(Cookie session, Cookie csrf) {
    }
}
