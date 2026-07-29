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
class ManagerApprovalIntegrationTest {
    private static final UUID MANAGER_ONE =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_TWO =
            UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID FINANCE_ONE =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE_ONE =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE_TWO =
            UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID EMPLOYEE_THREE =
            UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("orbisflow_manager_test")
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
    void assignedManagerApprovesRequestAtomically() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE, MANAGER_ONE, "manager_review", 4, "125.50",
                Instant.parse("2026-07-28T10:00:00Z"));
        LoginCookies manager = login("manager1");

        mvc.perform(post("/api/v1/requests/{id}/approve", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":4}
                                """)
                        .cookie(manager.session(), manager.csrf())
                        .header("X-XSRF-TOKEN", manager.csrf().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("finance_review")))
                .andExpect(jsonPath("$.version", is(5)))
                .andExpect(jsonPath("$.manager_decision.decision", is("approved")))
                .andExpect(jsonPath(
                        "$.manager_decision.decided_by_user_id",
                        is(MANAGER_ONE.toString())))
                .andExpect(jsonPath("$.manager_decision.rejection_reason").doesNotExist());

        Map<String, Object> decision = jdbc.queryForMap("""
                SELECT status::text, version, manager_decision,
                       manager_decided_by_user_id, manager_decided_at
                FROM requests WHERE id = ?
                """, requestId);
        assertThat(decision.get("status")).isEqualTo("finance_review");
        assertThat(decision.get("version")).isEqualTo(5L);
        assertThat(decision.get("manager_decision")).isEqualTo("approved");
        assertThat(decision.get("manager_decided_by_user_id"))
                .isEqualTo(MANAGER_ONE);
        assertThat(decision.get("manager_decided_at")).isNotNull();
        assertThat(count("""
                SELECT count(*) FROM audit_log
                WHERE request_id = ? AND event_type = 'approval'
                """, requestId)).isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM notifications
                WHERE request_id = ? AND type = 'finance_assignment'
                """, requestId)).isEqualTo(2);

        mvc.perform(get("/api/v1/requests/{id}/audit", requestId)
                        .cookie(manager.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].event_type", is("approval")))
                .andExpect(jsonPath("$.items[0].resulting_status", is("finance_review")));
    }

    @Test
    void assignedManagerRejectsRequestWithReasonAtomically() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE, MANAGER_ONE, "manager_review", 2, "85.00",
                Instant.parse("2026-07-28T10:00:00Z"));
        LoginCookies manager = login("manager1");

        mvc.perform(post("/api/v1/requests/{id}/reject", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":2,"reason":"  Missing receipt details  "}
                                """)
                        .cookie(manager.session(), manager.csrf())
                        .header("X-XSRF-TOKEN", manager.csrf().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("rejected")))
                .andExpect(jsonPath("$.version", is(3)))
                .andExpect(jsonPath("$.manager_decision.decision", is("rejected")))
                .andExpect(jsonPath(
                        "$.manager_decision.rejection_reason",
                        is("Missing receipt details")));

        assertThat(jdbc.queryForObject(
                "SELECT rejection_reason FROM requests WHERE id = ?",
                String.class,
                requestId)).isEqualTo("Missing receipt details");
        assertThat(count("""
                SELECT count(*) FROM audit_log
                WHERE request_id = ? AND event_type = 'rejection'
                """, requestId)).isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM notifications
                WHERE request_id = ?
                  AND user_id = ?
                  AND type = 'employee_rejection'
                """, requestId, EMPLOYEE_ONE)).isEqualTo(1);
    }

    @Test
    void rejectRequiresNonBlankReason() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE, MANAGER_ONE, "manager_review", 0, null, Instant.now());
        LoginCookies manager = login("manager1");

        assertInvalidReject(requestId, manager, """
                {"expected_version":0}
                """);
        assertInvalidReject(requestId, manager, """
                {"expected_version":0,"reason":"   "}
                """);
        assertThat(requestStatus(requestId)).isEqualTo("manager_review");
    }

    @Test
    void repeatedOrWrongStateDecisionReturnsStateConflict() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE, MANAGER_ONE, "manager_review", 0, null, Instant.now());
        LoginCookies manager = login("manager1");

        performDecision(manager, requestId, "approve", """
                {"expected_version":0}
                """).andExpect(status().isOk());
        performDecision(manager, requestId, "approve", """
                {"expected_version":1}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("STATE_CONFLICT")));
        performDecision(manager, requestId, "reject", """
                {"expected_version":1,"reason":"Too late"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("STATE_CONFLICT")));
    }

    @Test
    void managerDecisionAuthorizationUsesSnapshottedAssignment() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE, MANAGER_ONE, "manager_review", 0, null, Instant.now());

        for (String login : new String[] {"employee1", "finance1"}) {
            LoginCookies wrongRole = login(login);
            performDecision(wrongRole, requestId, "approve", """
                    {"expected_version":0}
                    """)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("ACCESS_DENIED")));
            performDecision(wrongRole, requestId, "reject", """
                    {"expected_version":0,"reason":"Denied"}
                    """)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("ACCESS_DENIED")));
        }

        LoginCookies unassignedManager = login("manager2");
        performDecision(unassignedManager, requestId, "approve", """
                {"expected_version":0}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));
        performDecision(unassignedManager, requestId, "reject", """
                {"expected_version":0,"reason":"Denied"}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));
        assertThat(requestStatus(requestId)).isEqualTo("manager_review");
    }

    @Test
    void managerRequestAndAuditReadsEnforcePostRoutingSnapshotScope() throws Exception {
        UUID assignedPostRouting = insertRequest(
                EMPLOYEE_ONE, MANAGER_ONE, "rejected", 1, null, Instant.now());
        UUID assignedPreRouting = insertRequest(
                EMPLOYEE_ONE, MANAGER_ONE, "employee_review", 1, null, Instant.now());
        UUID unassigned = insertRequest(
                EMPLOYEE_THREE, MANAGER_TWO, "manager_review", 1, null, Instant.now());
        LoginCookies manager = login("manager1");

        mvc.perform(get("/api/v1/requests/{id}", assignedPostRouting)
                        .cookie(manager.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("rejected")));
        mvc.perform(get("/api/v1/requests/{id}", assignedPreRouting)
                        .cookie(manager.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));
        mvc.perform(get("/api/v1/requests/{id}", unassigned)
                        .cookie(manager.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));
        mvc.perform(get("/api/v1/requests/{id}/audit", assignedPreRouting)
                        .cookie(manager.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/requests/{id}/audit", unassigned)
                        .cookie(manager.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void managerDashboardFiltersPaginatesAndSortsScopedRequests() throws Exception {
        UUID oldest = insertRequest(
                EMPLOYEE_ONE, MANAGER_ONE, "manager_review", 0, "40.00",
                Instant.parse("2026-07-28T08:00:00Z"));
        UUID newest = insertRequest(
                EMPLOYEE_TWO, MANAGER_ONE, "manager_review", 0, "90.00",
                Instant.parse("2026-07-28T09:00:00Z"));
        UUID approved = insertRequest(
                EMPLOYEE_ONE, MANAGER_ONE, "finance_review", 1, "125.00",
                Instant.parse("2026-07-28T10:00:00Z"));
        insertRequest(
                EMPLOYEE_THREE, MANAGER_TWO, "manager_review", 0, "500.00",
                Instant.parse("2026-07-28T07:00:00Z"));
        LoginCookies manager = login("manager1");

        mvc.perform(get("/api/v1/dashboards/manager/requests")
                        .queryParam("page", "0")
                        .queryParam("size", "1")
                        .cookie(manager.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is(oldest.toString())))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(1)))
                .andExpect(jsonPath("$.total_elements", is(2)))
                .andExpect(jsonPath("$.total_pages", is(2)))
                .andExpect(jsonPath("$.sort.field", is("updated_at")))
                .andExpect(jsonPath("$.sort.direction", is("asc")));

        mvc.perform(get("/api/v1/dashboards/manager/requests")
                        .queryParam("page", "1")
                        .queryParam("size", "1")
                        .cookie(manager.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id", is(newest.toString())));

        mvc.perform(get("/api/v1/dashboards/manager/requests")
                        .queryParam("status", "finance_review")
                        .queryParam("sort", "total_amount")
                        .queryParam("direction", "desc")
                        .cookie(manager.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is(approved.toString())))
                .andExpect(jsonPath("$.items[0].total_amount", is(125.0)));
    }

    @Test
    void teamActivityUsesEmployeesCurrentManagerNotRequestSnapshot() throws Exception {
        insertRequest(
                EMPLOYEE_ONE, MANAGER_ONE, "manager_review", 0, null, Instant.now());
        insertRequest(
                EMPLOYEE_TWO, MANAGER_ONE, "rejected", 1, null, Instant.now());
        insertRequest(
                EMPLOYEE_ONE, MANAGER_ONE, "finance_review", 1, null, Instant.now());
        insertRequest(
                EMPLOYEE_ONE, MANAGER_ONE, "processed", 2, null, Instant.now());

        // Current manager1, but snapshotted request manager2: included for manager1 aggregate.
        insertRequest(
                EMPLOYEE_ONE, MANAGER_TWO, "finance_review", 1, null, Instant.now());
        // Current manager2, but snapshotted request manager1: excluded from manager1 aggregate.
        insertRequest(
                EMPLOYEE_THREE, MANAGER_ONE, "manager_review", 0, null, Instant.now());

        LoginCookies manager = login("manager1");
        mvc.perform(get("/api/v1/dashboards/manager/team-activity")
                        .cookie(manager.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending", is(1)))
                .andExpect(jsonPath("$.approved", is(3)))
                .andExpect(jsonPath("$.rejected", is(1)));
    }

    @Test
    void staleDecisionVersionReturnsVersionConflict() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE, MANAGER_ONE, "manager_review", 5, null, Instant.now());
        LoginCookies manager = login("manager1");

        performDecision(manager, requestId, "approve", """
                {"expected_version":4}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("VERSION_CONFLICT")));
        performDecision(manager, requestId, "reject", """
                {"expected_version":4,"reason":"Stale"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("VERSION_CONFLICT")));
        assertThat(requestStatus(requestId)).isEqualTo("manager_review");
    }

    private UUID insertRequest(
            UUID employeeId,
            UUID managerId,
            String status,
            long version,
            String totalAmount,
            Instant updatedAt) {
        UUID requestId = UUID.randomUUID();
        String decision = null;
        UUID decidedBy = null;
        Timestamp decidedAt = null;
        String rejectionReason = null;
        String paymentStatus = null;
        UUID processedBy = null;
        Timestamp processedAt = null;
        if (status.equals("rejected")) {
            decision = "rejected";
            decidedBy = managerId;
            decidedAt = Timestamp.from(updatedAt.minusSeconds(30));
            rejectionReason = "Seeded rejection";
        } else if (status.equals("finance_review") || status.equals("processed")) {
            decision = "approved";
            decidedBy = managerId;
            decidedAt = Timestamp.from(updatedAt.minusSeconds(30));
        }
        if (status.equals("processed")) {
            paymentStatus = "paid";
            processedBy = FINANCE_ONE;
            processedAt = Timestamp.from(updatedAt.minusSeconds(10));
        }
        jdbc.update("""
                INSERT INTO requests (
                    id, employee_id, manager_id, status, version,
                    manager_decision, manager_decided_by_user_id,
                    manager_decided_at, rejection_reason,
                    payment_status, processed_by_user_id, processed_at,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?::request_status, ?,
                    ?, ?, ?, ?,
                    ?::payment_status, ?, ?,
                    ?, ?
                )
                """,
                requestId, employeeId, managerId, status, version,
                decision, decidedBy, decidedAt, rejectionReason,
                paymentStatus, processedBy, processedAt,
                Timestamp.from(updatedAt.minusSeconds(3600)),
                Timestamp.from(updatedAt));
        if (totalAmount != null) {
            jdbc.update("""
                    INSERT INTO extracted_invoice_data (
                        id, request_id, schema_version, extraction_status,
                        vendor, total_amount, invoice_date, validation_flags
                    ) VALUES (?, ?, '1', 'succeeded', 'Dashboard Vendor',
                              ?, '2026-07-28', '[]'::jsonb)
                    """, UUID.randomUUID(), requestId, new BigDecimal(totalAmount));
        }
        return requestId;
    }

    private void assertInvalidReject(
            UUID requestId,
            LoginCookies manager,
            String body) throws Exception {
        performDecision(manager, requestId, "reject", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_REQUEST")));
    }

    private org.springframework.test.web.servlet.ResultActions performDecision(
            LoginCookies cookies,
            UUID requestId,
            String action,
            String body) throws Exception {
        return mvc.perform(post(
                        "/api/v1/requests/{id}/" + action, requestId)
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
