package com.orbisflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbisflow.auth.api.AuthController;
import com.orbisflow.documents.application.DocumentAccessTokenService;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DocumentIntegrationTest {
    private static final String BUCKET = "orbisflow-test-invoices";
    private static final String MINIO_ACCESS_KEY = "orbisflow-test";
    private static final String MINIO_SECRET_KEY = "orbisflow-test-secret";
    private static final UUID EMPLOYEE_ONE =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("orbisflow_documents_test")
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
        registry.add("orbisflow.storage.s3.endpoint", DocumentIntegrationTest::minioEndpoint);
        registry.add("orbisflow.storage.s3.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("orbisflow.storage.s3.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("orbisflow.storage.s3.path-style", () -> "true");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DocumentAccessTokenService accessTokens;
    @Autowired S3Client s3;

    @BeforeAll
    static void createPrivateBucket() {
        try (S3Client client = minioClient()) {
            client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    @Test
    void validPdfCreatesRequestDocumentAndPrivateS3Object() throws Exception {
        byte[] pdf = validPdf("initial");
        UploadResult uploaded = upload("employee1", "invoice.pdf", "application/pdf", pdf);

        assertThat(uploaded.response().getResponse().getStatus()).isEqualTo(202);
        assertThat(uploaded.body().get("status").asText()).isEqualTo("uploaded_extracting");
        assertThat(uploaded.body().get("version").asLong()).isZero();
        assertThat(uploaded.body().get("employee_id").asText())
                .isEqualTo(EMPLOYEE_ONE.toString());
        assertThat(uploaded.body().get("manager_id").asText())
                .isEqualTo("10000000-0000-0000-0000-000000000001");

        String persistedStatus = jdbc.queryForObject(
                "SELECT status::text FROM requests WHERE id = ?",
                String.class, uploaded.requestId());
        Integer extractionRows = jdbc.queryForObject(
                "SELECT count(*) FROM extracted_invoice_data WHERE request_id = ?",
                Integer.class, uploaded.requestId());
        String key = jdbc.queryForObject(
                "SELECT s3_object_key FROM documents WHERE id = ?",
                String.class, uploaded.documentId());
        byte[] stored = s3.getObject(
                GetObjectRequest.builder().bucket(BUCKET).key(key).build(),
                ResponseTransformer.toBytes()).asByteArray();
        HttpResponse<Void> anonymousRead = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(
                                URI.create(minioEndpoint() + "/" + BUCKET + "/" + key))
                        .GET().build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(persistedStatus).isEqualTo("uploaded_extracting");
        assertThat(extractionRows).isZero();
        assertThat(key).matches("documents/[0-9a-f-]{36}");
        assertThat(stored).isEqualTo(pdf);
        assertThat(anonymousRead.statusCode()).isEqualTo(403);
    }

    @Test
    void wrongMimeTypeIsRejected() throws Exception {
        performCreate(
                login("employee1"),
                new MockMultipartFile(
                        "file", "invoice.txt", "text/plain", validPdf("mime")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code", is("UNSUPPORTED_MEDIA_TYPE")));
    }

    @Test
    void oversizedFileIsRejected() throws Exception {
        byte[] bytes = new byte[(10 * 1024 * 1024) + 1];
        byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        byte[] footer = "\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, bytes, 0, header.length);
        System.arraycopy(footer, 0, bytes, bytes.length - footer.length, footer.length);

        performCreate(
                login("employee1"),
                new MockMultipartFile(
                        "file", "oversized.pdf", "application/pdf", bytes))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code", is("FILE_TOO_LARGE")));
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        performCreate(
                login("employee1"),
                new MockMultipartFile(
                        "file", "empty.pdf", "application/pdf", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_FILE")));
    }

    @Test
    void corruptFileWithValidExtensionIsRejectedBySignature() throws Exception {
        performCreate(
                login("employee1"),
                new MockMultipartFile(
                        "file", "invoice.pdf", "application/pdf",
                        "not a PDF".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_FILE")));
    }

    @Test
    void createRequestRequiresCsrfToken() throws Exception {
        LoginCookies cookies = login("employee1");
        mvc.perform(multipart("/api/v1/requests")
                        .file(new MockMultipartFile(
                                "file", "invoice.pdf", "application/pdf",
                                validPdf("csrf")))
                        .cookie(cookies.session(), cookies.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("CSRF_INVALID")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"manager1", "finance1"})
    void nonEmployeeCannotCreateRequest(String login) throws Exception {
        performCreate(
                login(login),
                new MockMultipartFile(
                        "file", "invoice.pdf", "application/pdf", validPdf(login)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ACCESS_DENIED")));
    }

    @Test
    void ownerCanCreateAccessLinkAndRetrieveContent() throws Exception {
        byte[] pdf = validPdf("content");
        UploadResult uploaded = upload(
                "employee1", "quarterly invoice.pdf", "application/pdf", pdf);

        MvcResult linkResult = mvc.perform(get(
                        "/api/v1/documents/{documentId}/access-link",
                        uploaded.documentId())
                        .cookie(uploaded.cookies().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").isString())
                .andExpect(jsonPath("$.expires_at").isString())
                .andReturn();
        JsonNode link = objectMapper.readTree(linkResult.getResponse().getContentAsByteArray());
        String token = link.get("url").asText().split("token=", 2)[1];

        mvc.perform(get(
                        "/api/v1/documents/{documentId}/content",
                        uploaded.documentId())
                        .param("token", token)
                        .cookie(uploaded.cookies().session()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(
                        result.getResponse().getContentAsByteArray()).isEqualTo(pdf))
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader("Cache-Control"))
                        .contains("no-store"));
    }

    @Test
    void nonOwnerReceivesNotFoundForAccessLink() throws Exception {
        UploadResult uploaded = upload(
                "employee1", "invoice.pdf", "application/pdf", validPdf("owner"));
        LoginCookies nonOwner = login("employee2");

        mvc.perform(get(
                        "/api/v1/documents/{documentId}/access-link",
                        uploaded.documentId())
                        .cookie(nonOwner.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    void tamperedSignedLinkIsRejected() throws Exception {
        UploadResult uploaded = upload(
                "employee1", "invoice.pdf", "application/pdf", validPdf("tamper"));
        String token = accessTokens.issue(EMPLOYEE_ONE, uploaded.documentId()).value();
        int signatureStart = token.indexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, signatureStart)
                + replacement + token.substring(signatureStart + 1);

        mvc.perform(get(
                        "/api/v1/documents/{documentId}/content",
                        uploaded.documentId())
                        .param("token", tampered)
                        .cookie(uploaded.cookies().session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("SIGNED_LINK_INVALID")));
    }

    @Test
    void expiredSignedLinkIsRejected() throws Exception {
        UploadResult uploaded = upload(
                "employee1", "invoice.pdf", "application/pdf", validPdf("expired"));
        String expired = accessTokens.issue(
                EMPLOYEE_ONE, uploaded.documentId(), Instant.now().minusSeconds(1));

        mvc.perform(get(
                        "/api/v1/documents/{documentId}/content",
                        uploaded.documentId())
                        .param("token", expired)
                        .cookie(uploaded.cookies().session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("SIGNED_LINK_INVALID")));
    }

    @Test
    void employeeWithoutManagerCannotCreateRequest() throws Exception {
        jdbc.update("""
                UPDATE users SET manager_id = NULL
                WHERE login_identifier = 'employee3'
                """);

        performCreate(
                login("employee3"),
                new MockMultipartFile(
                        "file", "invoice.pdf", "application/pdf",
                        validPdf("managerless")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("MANAGER_NOT_ASSIGNED")));
    }

    @Test
    void replacementCreatesNewCurrentDocumentAndPreservesWorkflowState() throws Exception {
        UploadResult initial = upload(
                "employee1", "original.pdf", "application/pdf", validPdf("old"));
        jdbc.update(
                "UPDATE requests SET status = 'employee_review' WHERE id = ?",
                initial.requestId());

        LoginCookies cookies = login("employee1");
        MvcResult result = mvc.perform(multipart(
                        "/api/v1/requests/{requestId}/documents", initial.requestId())
                        .file(new MockMultipartFile(
                                "file", "replacement.pdf", "application/pdf",
                                validPdf("new")))
                        .param("expected_version", "0")
                        .cookie(cookies.session(), cookies.csrf())
                        .header("X-XSRF-TOKEN", cookies.csrf().getValue()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("employee_review")))
                .andExpect(jsonPath("$.version", is(1)))
                .andReturn();

        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM documents WHERE request_id = ?",
                Integer.class, initial.requestId());
        Integer current = jdbc.queryForObject(
                "SELECT count(*) FROM documents WHERE request_id = ? AND is_current",
                Integer.class, initial.requestId());
        Boolean oldCurrent = jdbc.queryForObject(
                "SELECT is_current FROM documents WHERE id = ?",
                Boolean.class, initial.documentId());

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        assertThat(total).isEqualTo(2);
        assertThat(current).isEqualTo(1);
        assertThat(oldCurrent).isFalse();
    }

    @Test
    void replacementUploadRequiresCsrfToken() throws Exception {
        UploadResult initial = upload(
                "employee1", "original.pdf", "application/pdf", validPdf("csrf-original"));
        jdbc.update(
                "UPDATE requests SET status = 'employee_review' WHERE id = ?",
                initial.requestId());
        LoginCookies cookies = login("employee1");
        MockMultipartFile replacement = new MockMultipartFile(
                "file", "replacement.pdf", "application/pdf",
                validPdf("csrf-replacement"));

        mvc.perform(multipart(
                        "/api/v1/requests/{requestId}/documents", initial.requestId())
                        .file(replacement)
                        .param("expected_version", "0")
                        .cookie(cookies.session(), cookies.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("CSRF_INVALID")));

        mvc.perform(multipart(
                        "/api/v1/requests/{requestId}/documents", initial.requestId())
                        .file(replacement)
                        .param("expected_version", "0")
                        .cookie(cookies.session(), cookies.csrf())
                        .header("X-XSRF-TOKEN", "mismatched-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("CSRF_INVALID")));
    }

    @ParameterizedTest
    @CsvSource({
        "manager1, 403, ACCESS_DENIED",
        "finance1, 403, ACCESS_DENIED",
        "employee2, 404, RESOURCE_NOT_FOUND"
    })
    void nonOwnerCannotReplaceDocument(
            String login, int expectedStatus, String expectedErrorCode) throws Exception {
        UploadResult initial = upload(
                "employee1", "original.pdf", "application/pdf", validPdf("owner-original"));
        jdbc.update(
                "UPDATE requests SET status = 'employee_review' WHERE id = ?",
                initial.requestId());
        LoginCookies nonOwner = login(login);

        mvc.perform(multipart(
                        "/api/v1/requests/{requestId}/documents", initial.requestId())
                        .file(new MockMultipartFile(
                                "file", "replacement.pdf", "application/pdf",
                                validPdf("non-owner-replacement")))
                        .param("expected_version", "0")
                        .cookie(nonOwner.session(), nonOwner.csrf())
                        .header("X-XSRF-TOKEN", nonOwner.csrf().getValue()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code", is(expectedErrorCode)));
    }

    @Test
    void replacementOutsideEmployeeReviewOrRejectedIsRejected() throws Exception {
        UploadResult uploaded = upload(
                "employee1", "original.pdf", "application/pdf", validPdf("state"));
        LoginCookies cookies = login("employee1");

        mvc.perform(multipart(
                        "/api/v1/requests/{requestId}/documents", uploaded.requestId())
                        .file(new MockMultipartFile(
                                "file", "replacement.pdf", "application/pdf",
                                validPdf("replacement")))
                        .param("expected_version", "0")
                        .cookie(cookies.session(), cookies.csrf())
                        .header("X-XSRF-TOKEN", cookies.csrf().getValue()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("STATE_CONFLICT")));
    }

    private UploadResult upload(
            String login, String filename, String mimeType, byte[] content) throws Exception {
        LoginCookies cookies = login(login);
        MvcResult result = performCreate(
                cookies,
                new MockMultipartFile("file", filename, mimeType, content))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        UUID requestId = UUID.fromString(body.get("id").asText());
        UUID documentId = jdbc.queryForObject(
                "SELECT id FROM documents WHERE request_id = ? AND is_current",
                UUID.class, requestId);
        return new UploadResult(result, body, requestId, documentId, cookies);
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(
            LoginCookies cookies, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/api/v1/requests")
                .file(file)
                .cookie(cookies.session(), cookies.csrf())
                .header("X-XSRF-TOKEN", cookies.csrf().getValue()));
    }

    private LoginCookies login(String login) throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "login_identifier", login,
                        "password", "OrbisFlow123!"));
        MvcResult result = mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return new LoginCookies(
                result.getResponse().getCookie(AuthController.SESSION_COOKIE),
                result.getResponse().getCookie(AuthController.CSRF_COOKIE));
    }

    private static byte[] validPdf(String marker) {
        return ("%PDF-1.4\n"
                + "1 0 obj\n<< /Type /Catalog >>\nendobj\n"
                + "% " + marker + "\n"
                + "trailer\n<<>>\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static String minioEndpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static S3Client minioClient() {
        return S3Client.builder()
                .endpointOverride(URI.create(minioEndpoint()))
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

    private record UploadResult(
            MvcResult response,
            JsonNode body,
            UUID requestId,
            UUID documentId,
            LoginCookies cookies
    ) {
    }
}
