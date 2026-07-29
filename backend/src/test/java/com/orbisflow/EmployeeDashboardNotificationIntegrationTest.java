package com.orbisflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbisflow.auth.api.AuthController;
import com.orbisflow.integration.ai.FastApiExtractionDtos.ClientResult;
import com.orbisflow.integration.ai.FastApiExtractionDtos.ExtractionResponse;
import com.orbisflow.integration.ai.FastApiExtractionDtos.LineItem;
import com.orbisflow.requests.application.ExtractionWorkflowService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
class EmployeeDashboardNotificationIntegrationTest {
    private static final UUID MANAGER_ONE =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID FINANCE_ONE =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE_ONE =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE_TWO =
            UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("orbisflow_notifications_test")
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
    @Autowired ExtractionWorkflowService extractionWorkflow;

    @Test
    void employeeDashboardScopesFiltersPaginatesAndSorts() throws Exception {
        Instant base = Instant.parse("2026-07-29T08:00:00Z");
        UUID oldest = insertRequest(
                EMPLOYEE_ONE, "uploaded_extracting", 0,
                base, base, "30.00");
        UUID middle = insertRequest(
                EMPLOYEE_ONE, "manager_review", 1,
                base.plusSeconds(60), base.plusSeconds(90), "10.00");
        UUID newest = insertRequest(
                EMPLOYEE_ONE, "manager_review", 1,
                base.plusSeconds(120), base.plusSeconds(150), "20.00");
        insertRequest(
                EMPLOYEE_TWO, "manager_review", 1,
                base.plusSeconds(180), base.plusSeconds(180), "1.00");
        LoginCookies employee = login("employee1");

        mvc.perform(get("/api/v1/dashboards/employee/requests")
                        .queryParam("page", "0")
                        .queryParam("size", "2")
                        .cookie(employee.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements", is(3)))
                .andExpect(jsonPath("$.total_pages", is(2)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id", is(newest.toString())))
                .andExpect(jsonPath("$.items[1].id", is(middle.toString())))
                .andExpect(jsonPath("$.sort.field", is("submitted_at")))
                .andExpect(jsonPath("$.sort.direction", is("desc")));

        mvc.perform(get("/api/v1/dashboards/employee/requests")
                        .queryParam("status", "manager_review")
                        .queryParam("sort", "total_amount")
                        .queryParam("direction", "asc")
                        .cookie(employee.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements", is(2)))
                .andExpect(jsonPath("$.items[0].id", is(middle.toString())))
                .andExpect(jsonPath("$.items[1].id", is(newest.toString())));

        mvc.perform(get("/api/v1/dashboards/employee/requests")
                        .queryParam("page", "1")
                        .queryParam("size", "2")
                        .cookie(employee.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id", is(oldest.toString())));
    }

    @Test
    void nonEmployeeCannotReadEmployeeDashboard() throws Exception {
        for (String login : List.of("manager1", "finance1")) {
            LoginCookies actor = login(login);
            mvc.perform(get("/api/v1/dashboards/employee/requests")
                            .cookie(actor.session()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("ACCESS_DENIED")));
        }
    }

    @Test
    void recentNotificationsAreOwnerScopedOrderedAndCappedAtFifty()
            throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE, "uploaded_extracting", 0,
                Instant.parse("2026-07-29T07:00:00Z"),
                Instant.parse("2026-07-29T07:00:00Z"), null);
        UUID otherRequest = insertRequest(
                EMPLOYEE_TWO, "uploaded_extracting", 0,
                Instant.parse("2026-07-29T07:00:00Z"),
                Instant.parse("2026-07-29T07:00:00Z"), null);
        Instant base = Instant.parse("2026-07-29T09:00:00Z");
        List<UUID> ownIds = new ArrayList<>();
        for (int index = 0; index < 60; index++) {
            ownIds.add(insertNotification(
                    EMPLOYEE_ONE, requestId, "employee_correction",
                    base.plusSeconds(index), null));
        }
        UUID otherId = insertNotification(
                EMPLOYEE_TWO, otherRequest, "employee_correction",
                base.plusSeconds(100), null);
        LoginCookies employee = login("employee1");

        mvc.perform(get("/api/v1/notifications")
                        .queryParam("size", "20")
                        .cookie(employee.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements", is(50)))
                .andExpect(jsonPath("$.total_pages", is(3)))
                .andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath(
                        "$.items[0].id",
                        is(ownIds.get(59).toString())))
                .andExpect(jsonPath(
                        "$.items[*].id",
                        not(hasItem(otherId.toString()))));

        mvc.perform(get("/api/v1/notifications")
                        .queryParam("page", "2")
                        .queryParam("size", "20")
                        .cookie(employee.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath(
                        "$.items[9].id",
                        is(ownIds.get(10).toString())));
    }

    @Test
    void unreadNotificationsAreUncappedAndFiltered() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE, "uploaded_extracting", 0,
                Instant.parse("2026-07-29T07:00:00Z"),
                Instant.parse("2026-07-29T07:00:00Z"), null);
        Instant base = Instant.parse("2026-07-29T09:00:00Z");
        for (int index = 0; index < 60; index++) {
            insertNotification(
                    EMPLOYEE_ONE,
                    requestId,
                    "employee_correction",
                    base.plusSeconds(index),
                    index < 5 ? base.plusSeconds(100 + index) : null);
        }
        LoginCookies employee = login("employee1");

        mvc.perform(get("/api/v1/notifications")
                        .queryParam("view", "unread")
                        .queryParam("page", "0")
                        .queryParam("size", "50")
                        .cookie(employee.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements", is(55)))
                .andExpect(jsonPath("$.total_pages", is(2)))
                .andExpect(jsonPath("$.items", hasSize(50)))
                .andExpect(jsonPath("$.items[*].read_at", hasSize(50)));

        mvc.perform(get("/api/v1/notifications")
                        .queryParam("view", "unread")
                        .queryParam("page", "1")
                        .queryParam("size", "50")
                        .cookie(employee.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)));
    }

    @Test
    void markReadIsOwnerOnlyCsrfProtectedAndIdempotent() throws Exception {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE, "uploaded_extracting", 0,
                Instant.now(), Instant.now(), null);
        UUID notificationId = insertNotification(
                EMPLOYEE_ONE, requestId, "employee_correction",
                Instant.now(), null);
        LoginCookies owner = login("employee1");
        LoginCookies other = login("employee2");

        mvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .cookie(owner.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("CSRF_INVALID")));
        mvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .cookie(owner.session(), owner.csrf())
                        .header("X-XSRF-TOKEN", "mismatched"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("CSRF_INVALID")));
        mvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .cookie(other.session(), other.csrf())
                        .header("X-XSRF-TOKEN", other.csrf().getValue()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));

        String firstReadAt = markRead(owner, notificationId);
        String secondReadAt = markRead(owner, notificationId);
        assertThat(secondReadAt).isEqualTo(firstReadAt);
        assertThat(jdbc.queryForObject(
                "SELECT read_at IS NOT NULL FROM notifications WHERE id = ?",
                Boolean.class,
                notificationId)).isTrue();
    }

    @Test
    void lifecycleNotificationsAreRetrievableByTheCorrectUsers()
            throws Exception {
        UUID requestId = insertPendingExtractionRequest();
        extractionWorkflow.complete(
                requestId,
                0,
                ClientResult.completed(successfulExtraction(requestId, "125.00")));
        LoginCookies manager = login("manager1");
        LoginCookies employee = login("employee1");
        LoginCookies finance = login("finance1");

        assertNotificationVisible(manager, requestId, "manager_assignment");

        performStateChange(
                manager,
                "/api/v1/requests/" + requestId + "/reject",
                Map.of("expected_version", 1, "reason", "Needs correction"))
                .andExpect(status().isOk());
        assertNotificationVisible(employee, requestId, "employee_rejection");

        performStateChange(
                employee,
                "/api/v1/requests/" + requestId + "/resubmit",
                Map.of("expected_version", 2))
                .andExpect(status().isOk());
        assertThat(notificationCount(
                MANAGER_ONE, requestId, "manager_assignment")).isEqualTo(2);

        performStateChange(
                manager,
                "/api/v1/requests/" + requestId + "/approve",
                Map.of("expected_version", 4))
                .andExpect(status().isOk());
        assertNotificationVisible(finance, requestId, "finance_assignment");

        performStateChange(
                finance,
                "/api/v1/requests/" + requestId + "/process",
                Map.of("expected_version", 5, "payment_status", "paid"))
                .andExpect(status().isOk());
        assertNotificationVisible(employee, requestId, "processed");

        assertThat(notificationCount(
                EMPLOYEE_TWO, requestId, "processed")).isZero();
    }

    @Test
    void flaggedExtractionCreatesEmployeeCorrectionNotification()
            throws Exception {
        UUID requestId = insertPendingExtractionRequest();
        extractionWorkflow.complete(
                requestId,
                0,
                ClientResult.completed(new ExtractionResponse(
                        requestId,
                        "1",
                        "succeeded",
                        null,
                        new BigDecimal("25.00"),
                        LocalDate.of(2026, 7, 29),
                        List.of(),
                        List.of(),
                        null)));
        LoginCookies employee = login("employee1");

        assertThat(requestStatus(requestId)).isEqualTo("employee_review");
        assertNotificationVisible(employee, requestId, "employee_correction");
    }

    private UUID insertPendingExtractionRequest() {
        UUID requestId = insertRequest(
                EMPLOYEE_ONE,
                "uploaded_extracting",
                0,
                Instant.now(),
                Instant.now(),
                null);
        jdbc.update("""
                INSERT INTO extracted_invoice_data (
                    id, request_id, schema_version, extraction_status,
                    validation_flags
                ) VALUES (?, ?, '1', 'pending', '[]'::jsonb)
                """, UUID.randomUUID(), requestId);
        return requestId;
    }

    private ExtractionResponse successfulExtraction(
            UUID requestId,
            String amount) {
        return new ExtractionResponse(
                requestId,
                "1",
                "succeeded",
                "Lifecycle Vendor",
                new BigDecimal(amount),
                LocalDate.of(2026, 7, 29),
                List.of(new LineItem(
                        1, "Consulting", new BigDecimal(amount))),
                List.of(),
                null);
    }

    private UUID insertRequest(
            UUID employeeId,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt,
            String totalAmount) {
        UUID requestId = UUID.randomUUID();
        boolean rejected = status.equals("rejected");
        boolean approvedPath = status.equals("finance_review")
                || status.equals("processed");
        String decision = rejected
                ? "rejected"
                : approvedPath ? "approved" : null;
        UUID decidedBy = decision == null ? null : MANAGER_ONE;
        Timestamp decidedAt = decision == null
                ? null
                : Timestamp.from(updatedAt.minusSeconds(10));
        String rejectionReason = rejected ? "Needs correction" : null;
        String paymentStatus = status.equals("processed") ? "paid" : null;
        UUID processedBy = status.equals("processed") ? FINANCE_ONE : null;
        Timestamp processedAt = status.equals("processed")
                ? Timestamp.from(updatedAt)
                : null;
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
                    ?::payment_status, ?, ?, ?, ?
                )
                """,
                requestId,
                employeeId,
                MANAGER_ONE,
                status,
                version,
                decision,
                decidedBy,
                decidedAt,
                rejectionReason,
                paymentStatus,
                processedBy,
                processedAt,
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt));
        if (totalAmount != null) {
            jdbc.update("""
                    INSERT INTO extracted_invoice_data (
                        id, request_id, schema_version, extraction_status,
                        vendor, total_amount, invoice_date, validation_flags
                    ) VALUES (
                        ?, ?, '1', 'succeeded', 'Dashboard Vendor',
                        ?, '2026-07-29', '[]'::jsonb
                    )
                    """,
                    UUID.randomUUID(),
                    requestId,
                    new BigDecimal(totalAmount));
        }
        return requestId;
    }

    private UUID insertNotification(
            UUID userId,
            UUID requestId,
            String type,
            Instant createdAt,
            Instant readAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO notifications (
                    id, user_id, request_id, type, read_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                userId,
                requestId,
                type,
                readAt == null ? null : Timestamp.from(readAt),
                Timestamp.from(createdAt));
        return id;
    }

    private String markRead(LoginCookies owner, UUID notificationId)
            throws Exception {
        MvcResult result = mvc.perform(
                        patch("/api/v1/notifications/{id}/read", notificationId)
                                .cookie(owner.session(), owner.csrf())
                                .header(
                                        "X-XSRF-TOKEN",
                                        owner.csrf().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(notificationId.toString())))
                .andExpect(jsonPath("$.read_at").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(
                        result.getResponse().getContentAsString())
                .get("read_at")
                .asText();
    }

    private void assertNotificationVisible(
            LoginCookies actor,
            UUID requestId,
            String type) throws Exception {
        JsonNode response = notificationList(actor);
        boolean found = false;
        for (JsonNode item : response.get("items")) {
            if (requestId.toString().equals(item.get("request_id").asText())
                    && type.equals(item.get("type").asText())) {
                found = true;
            }
        }
        assertThat(found)
                .as("%s notification for request %s", type, requestId)
                .isTrue();
    }

    private JsonNode notificationList(LoginCookies actor) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/notifications")
                        .queryParam("size", "50")
                        .cookie(actor.session()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions performStateChange(
            LoginCookies actor,
            String path,
            Map<String, Object> body) throws Exception {
        return mvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .cookie(actor.session(), actor.csrf())
                .header("X-XSRF-TOKEN", actor.csrf().getValue()));
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

    private int notificationCount(
            UUID userId,
            UUID requestId,
            String type) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM notifications
                WHERE user_id = ? AND request_id = ? AND type = ?
                """, Integer.class, userId, requestId, type);
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
