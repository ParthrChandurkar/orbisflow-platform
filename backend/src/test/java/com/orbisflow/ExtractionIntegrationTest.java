package com.orbisflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbisflow.auth.api.AuthController;
import jakarta.servlet.http.Cookie;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExtractionIntegrationTest {
    private static final String BUCKET = "orbisflow-extraction-tests";
    private static final String MINIO_ACCESS_KEY = "orbisflow-test";
    private static final String MINIO_SECRET_KEY = "orbisflow-test-secret";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("orbisflow_extraction_test")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse(
                    "minio/minio:RELEASE.2024-01-18T22-51-28Z"))
                    .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
                    .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
                    .withCommand("server", "/data")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @Container
    static final GenericContainer<?> FAST_API =
            new GenericContainer<>(DockerImageName.parse("orbisflow-ai-stage14b:latest"))
                    .withExposedPorts(8000)
                    .waitingFor(Wait.forHttp("/internal/v1/health").forPort(8000))
                    .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void configureServices(DynamicPropertyRegistry registry) {
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
        registry.add("orbisflow.storage.s3.bucket", () -> BUCKET);
        registry.add("orbisflow.storage.s3.region", () -> "us-east-1");
        registry.add("orbisflow.storage.s3.endpoint", ExtractionIntegrationTest::minioEndpoint);
        registry.add("orbisflow.storage.s3.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("orbisflow.storage.s3.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("orbisflow.storage.s3.path-style", () -> "true");
        registry.add("orbisflow.ai-service.base-url", ExtractionIntegrationTest::fastApiEndpoint);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @BeforeAll
    static void createPrivateBucket() {
        try (S3Client client = minioClient()) {
            client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    @Test
    void realOcrRoutesReadableInvoiceAndStartsExactlyOneAttempt() throws Exception {
        byte[] invoice = invoicePng(
                "Vendor: Acme Consulting",
                "Invoice Date: 2026-07-28",
                "Item: Consulting Service 125.50",
                "Total Amount: 125.50");
        UploadResult uploaded = upload("employee1", invoice);

        await(() -> "manager_review".equals(requestStatus(uploaded.requestId())));

        Map<String, Object> extracted = jdbc.queryForMap("""
                SELECT extraction_status, vendor, total_amount, invoice_date,
                       validation_flags::text
                FROM extracted_invoice_data WHERE request_id = ?
                """, uploaded.requestId());
        Integer attempts = jdbc.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE request_id = ?
                  AND event_type = 'extraction'
                  AND jsonb_exists(context, 'result')
                """, Integer.class, uploaded.requestId());

        assertThat(extracted.get("extraction_status")).isEqualTo("succeeded");
        assertThat(extracted.get("vendor")).isEqualTo("Acme Consulting");
        assertThat(extracted.get("total_amount").toString()).isEqualTo("125.5000");
        assertThat(extracted.get("invoice_date").toString()).isEqualTo("2026-07-28");
        assertThat(extracted.get("validation_flags").toString()).isEqualTo("[]");
        assertThat(attempts).isEqualTo(1);
        assertThat(occurrences(
                FAST_API.getLogs(),
                "extraction_attempt request_id=" + uploaded.requestId())).isEqualTo(1);
    }

    @Test
    void mismatchedInvoiceIsFlaggedThenCorrectedAndResubmitted() throws Exception {
        UploadResult uploaded = upload("employee1", invoicePng(
                "Vendor: Mismatch Supplies",
                "Invoice Date: 2026-07-28",
                "Item: Office Supplies 75.00",
                "Total Amount: 100.00"));
        await(() -> "employee_review".equals(requestStatus(uploaded.requestId())));
        LoginCookies owner = uploaded.cookies();

        mvc.perform(get("/api/v1/requests/{id}/extracted-data", uploaded.requestId())
                        .cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation_flags[0].code",
                        is("LINE_ITEM_SUM_MISMATCH")));

        LoginCookies manager = login("manager1");
        mvc.perform(patch("/api/v1/requests/{id}/extracted-data", uploaded.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":1,"total_amount":"75.00"}
                                """)
                        .cookie(manager.session(), manager.csrf())
                        .header("X-XSRF-TOKEN", manager.csrf().getValue()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ACCESS_DENIED")));

        LoginCookies nonOwner = login("employee2");
        mvc.perform(patch("/api/v1/requests/{id}/extracted-data", uploaded.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":1,"total_amount":"75.00"}
                                """)
                        .cookie(nonOwner.session(), nonOwner.csrf())
                        .header("X-XSRF-TOKEN", nonOwner.csrf().getValue()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));

        mvc.perform(post("/api/v1/requests/{id}/resubmit", uploaded.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":1}
                                """)
                        .cookie(nonOwner.session(), nonOwner.csrf())
                        .header("X-XSRF-TOKEN", nonOwner.csrf().getValue()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));

        mvc.perform(post("/api/v1/requests/{id}/extraction/retry", uploaded.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":1}
                                """)
                        .cookie(manager.session(), manager.csrf())
                        .header("X-XSRF-TOKEN", manager.csrf().getValue()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ACCESS_DENIED")));

        mvc.perform(post("/api/v1/requests/{id}/extraction/retry", uploaded.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":1}
                                """)
                        .cookie(owner.session(), owner.csrf())
                        .header("X-XSRF-TOKEN", owner.csrf().getValue()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("STATE_CONFLICT")));

        mvc.perform(patch("/api/v1/requests/{id}/extracted-data", uploaded.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":1,"total_amount":"75.00"}
                                """)
                        .cookie(owner.session(), owner.csrf())
                        .header("X-XSRF-TOKEN", owner.csrf().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(2)))
                .andExpect(jsonPath("$.extracted_data.validation_flags").isEmpty());

        mvc.perform(post("/api/v1/requests/{id}/resubmit", uploaded.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":2}
                                """)
                        .cookie(owner.session(), owner.csrf())
                        .header("X-XSRF-TOKEN", owner.csrf().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("manager_review")))
                .andExpect(jsonPath("$.version", is(3)));
    }

    @Test
    void nonOwnerCannotPatchExtractedData() throws Exception {
        UploadResult uploaded = uploadFlaggedInvoice("Patch Authorization Vendor");

        assertMutationDeniedForWrongRolesAndNonOwner(
                () -> patch("/api/v1/requests/{id}/extracted-data", uploaded.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":1,"total_amount":"75.00"}
                                """));
    }

    @Test
    void nonOwnerCannotResubmit() throws Exception {
        UploadResult uploaded = uploadFlaggedInvoice("Resubmit Authorization Vendor");

        assertMutationDeniedForWrongRolesAndNonOwner(
                () -> post("/api/v1/requests/{id}/resubmit", uploaded.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":1}
                                """));
    }

    @Test
    void nonOwnerCannotRetryExtraction() throws Exception {
        UploadResult uploaded = upload("employee1", blankPng());
        await(() -> "failed".equals(extractionStatus(uploaded.requestId())));

        assertMutationDeniedForWrongRolesAndNonOwner(
                () -> post("/api/v1/requests/{id}/extraction/retry", uploaded.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":0}
                                """));
    }

    @Test
    void employeeCannotPatchOrResubmitRequestInManagerReview() throws Exception {
        UploadResult uploaded = upload("employee1", invoicePng(
                "Vendor: Routed Vendor",
                "Invoice Date: 2026-07-28",
                "Item: Routed Service 90.00",
                "Total Amount: 90.00"));
        await(() -> "manager_review".equals(requestStatus(uploaded.requestId())));

        mvc.perform(patch("/api/v1/requests/{id}/extracted-data", uploaded.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":1,"total_amount":"90.00"}
                                """)
                        .cookie(
                                uploaded.cookies().session(),
                                uploaded.cookies().csrf())
                        .header(
                                "X-XSRF-TOKEN",
                                uploaded.cookies().csrf().getValue()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("STATE_CONFLICT")));

        mvc.perform(post("/api/v1/requests/{id}/resubmit", uploaded.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":1}
                                """)
                        .cookie(
                                uploaded.cookies().session(),
                                uploaded.cookies().csrf())
                        .header(
                                "X-XSRF-TOKEN",
                                uploaded.cookies().csrf().getValue()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("STATE_CONFLICT")));
    }

    @Test
    void unreadableInvoiceFailsWithoutTransitionAndRetryStartsOneNewAttempt()
            throws Exception {
        UploadResult uploaded = upload("employee1", blankPng());
        await(() -> "failed".equals(extractionStatus(uploaded.requestId())));

        assertThat(requestStatus(uploaded.requestId())).isEqualTo("uploaded_extracting");
        assertThat(failureCategory(uploaded.requestId())).isEqualTo("unreadable_document");

        mvc.perform(post("/api/v1/requests/{id}/extraction/retry", uploaded.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_version":0}
                                """)
                        .cookie(uploaded.cookies().session(), uploaded.cookies().csrf())
                        .header(
                                "X-XSRF-TOKEN",
                                uploaded.cookies().csrf().getValue()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.version", is(1)));

        await(() -> {
            Integer count = jdbc.queryForObject("""
                    SELECT count(*) FROM audit_log
                    WHERE request_id = ?
                      AND event_type = 'extraction'
                      AND jsonb_exists(context, 'result')
                    """, Integer.class, uploaded.requestId());
            return count != null && count == 2
                    && "failed".equals(extractionStatus(uploaded.requestId()));
        });
        assertThat(requestStatus(uploaded.requestId())).isEqualTo("uploaded_extracting");
        assertThat(occurrences(
                FAST_API.getLogs(),
                "extraction_attempt request_id=" + uploaded.requestId())).isEqualTo(2);
    }

    @Test
    void replacementDocumentStartsExactlyOneNewRealExtractionAttempt() throws Exception {
        UploadResult uploaded = upload("employee1", invoicePng(
                "Vendor: First Vendor",
                "Invoice Date: 2026-07-28",
                "Item: Incorrect Total 25.00",
                "Total Amount: 30.00"));
        await(() -> "employee_review".equals(requestStatus(uploaded.requestId())));

        mvc.perform(multipart(
                        "/api/v1/requests/{id}/documents", uploaded.requestId())
                        .file(new MockMultipartFile(
                                "file", "replacement.png", "image/png",
                                invoicePng(
                                        "Vendor: Replacement Vendor",
                                        "Invoice Date: 2026-07-28",
                                        "Item: Correct Service 40.00",
                                        "Total Amount: 40.00")))
                        .param("expected_version", "1")
                        .cookie(
                                uploaded.cookies().session(),
                                uploaded.cookies().csrf())
                        .header(
                                "X-XSRF-TOKEN",
                                uploaded.cookies().csrf().getValue()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("uploaded_extracting")))
                .andExpect(jsonPath("$.version", is(2)));

        await(() -> "manager_review".equals(requestStatus(uploaded.requestId())));
        String vendor = jdbc.queryForObject(
                "SELECT vendor FROM extracted_invoice_data WHERE request_id = ?",
                String.class,
                uploaded.requestId());
        Integer currentDocuments = jdbc.queryForObject("""
                SELECT count(*) FROM documents
                WHERE request_id = ? AND is_current
                """, Integer.class, uploaded.requestId());

        assertThat(vendor).isEqualTo("Replacement Vendor");
        assertThat(currentDocuments).isEqualTo(1);
        assertThat(occurrences(
                FAST_API.getLogs(),
                "extraction_attempt request_id=" + uploaded.requestId())).isEqualTo(2);
    }

    private UploadResult uploadFlaggedInvoice(String vendor) throws Exception {
        UploadResult uploaded = upload("employee1", invoicePng(
                "Vendor: " + vendor,
                "Invoice Date: 2026-07-28",
                "Item: Authorization Test 75.00",
                "Total Amount: 100.00"));
        await(() -> "employee_review".equals(requestStatus(uploaded.requestId())));
        return uploaded;
    }

    private void assertMutationDeniedForWrongRolesAndNonOwner(
            Supplier<MockHttpServletRequestBuilder> request)
            throws Exception {
        for (String login : new String[] {"manager1", "finance1"}) {
            LoginCookies wrongRole = login(login);
            mvc.perform(request.get()
                            .cookie(wrongRole.session(), wrongRole.csrf())
                            .header("X-XSRF-TOKEN", wrongRole.csrf().getValue()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code", is("ACCESS_DENIED")));
        }

        LoginCookies nonOwner = login("employee2");
        mvc.perform(request.get()
                        .cookie(nonOwner.session(), nonOwner.csrf())
                        .header("X-XSRF-TOKEN", nonOwner.csrf().getValue()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));
    }

    private UploadResult upload(String login, byte[] content) throws Exception {
        LoginCookies cookies = login(login);
        MvcResult result = mvc.perform(multipart("/api/v1/requests")
                        .file(new MockMultipartFile(
                                "file", "invoice.png", "image/png", content))
                        .cookie(cookies.session(), cookies.csrf())
                        .header("X-XSRF-TOKEN", cookies.csrf().getValue()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("uploaded_extracting")))
                .andReturn();
        UUID requestId = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsByteArray())
                        .get("id").asText());
        return new UploadResult(requestId, cookies);
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

    private String requestStatus(UUID requestId) {
        return jdbc.queryForObject(
                "SELECT status::text FROM requests WHERE id = ?",
                String.class,
                requestId);
    }

    private String extractionStatus(UUID requestId) {
        return jdbc.queryForObject(
                "SELECT extraction_status FROM extracted_invoice_data WHERE request_id = ?",
                String.class,
                requestId);
    }

    private String failureCategory(UUID requestId) {
        return jdbc.queryForObject(
                "SELECT failure_category FROM extracted_invoice_data WHERE request_id = ?",
                String.class,
                requestId);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for asynchronous extraction");
    }

    private static byte[] invoicePng(String... lines) throws Exception {
        BufferedImage image = new BufferedImage(1400, 700, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 48));
        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.drawString("INVOICE", 60, 80);
        for (int index = 0; index < lines.length; index++) {
            graphics.drawString(lines[index], 60, 180 + index * 110);
        }
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static byte[] blankPng() throws Exception {
        BufferedImage image = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 400, 200);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static int occurrences(String text, String token) {
        return (text.length() - text.replace(token, "").length()) / token.length();
    }

    private static String minioEndpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static String fastApiEndpoint() {
        return "http://" + FAST_API.getHost() + ":" + FAST_API.getMappedPort(8000);
    }

    private static S3Client minioClient() {
        return S3Client.builder()
                .endpointOverride(java.net.URI.create(minioEndpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                MINIO_ACCESS_KEY, MINIO_SECRET_KEY)))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .forcePathStyle(true)
                .build();
    }

    private record LoginCookies(Cookie session, Cookie csrf) {
    }

    private record UploadResult(UUID requestId, LoginCookies cookies) {
    }
}
