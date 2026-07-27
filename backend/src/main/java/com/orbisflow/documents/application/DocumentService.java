package com.orbisflow.documents.application;

import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import com.orbisflow.documents.api.DocumentDtos.AccessLink;
import com.orbisflow.documents.domain.Document;
import com.orbisflow.documents.persistence.DocumentRepository;
import com.orbisflow.documents.persistence.DocumentRepository.ScopedDocument;
import com.orbisflow.documents.persistence.S3DocumentStore;
import com.orbisflow.requests.api.RequestDtos.RequestSummary;
import com.orbisflow.requests.domain.Request;
import com.orbisflow.requests.domain.RequestStatus;
import com.orbisflow.requests.persistence.RequestRepository;
import com.orbisflow.users.domain.UserRole;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private final RequestRepository requests;
    private final DocumentRepository documents;
    private final S3DocumentStore objectStore;
    private final InvoiceFileValidator validator;
    private final DocumentAccessTokenService accessTokens;
    private final TransactionTemplate transactions;

    public DocumentService(
            RequestRepository requests,
            DocumentRepository documents,
            S3DocumentStore objectStore,
            InvoiceFileValidator validator,
            DocumentAccessTokenService accessTokens,
            TransactionTemplate transactions) {
        this.requests = requests;
        this.documents = documents;
        this.objectStore = objectStore;
        this.validator = validator;
        this.accessTokens = accessTokens;
        this.transactions = transactions;
    }

    public RequestSummary replace(
            AuthenticatedUser principal,
            UUID requestId,
            long expectedVersion,
            MultipartFile file) {
        if (expectedVersion < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST,
                    "expected_version must be greater than or equal to zero.");
        }
        Request current = ownedRequest(requestId, principal.id());
        if (current.status() != RequestStatus.EMPLOYEE_REVIEW
                && current.status() != RequestStatus.REJECTED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ApiErrorCode.STATE_CONFLICT,
                    "A replacement document is not allowed in the current Request state.");
        }
        if (current.version() != expectedVersion) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ApiErrorCode.VERSION_CONFLICT,
                    "The Request was modified by another operation.");
        }

        var upload = validator.validate(file);
        String objectKey = "documents/" + UUID.randomUUID();
        Instant now = Instant.now();
        Document replacement = new Document(
                UUID.randomUUID(), requestId, principal.id(), objectKey,
                upload.filename(), upload.mimeType(), upload.bytes().length, true, now);

        objectStore.put(objectKey, upload.bytes(), upload.mimeType());
        try {
            transactions.executeWithoutResult(status -> {
                // Stage 14a rotates the document and version only. Stage 14b owns
                // the extraction-driven status/reset transitions and retry attempt.
                int updated = requests.incrementVersionForDocumentReplacement(
                        requestId, principal.id(), expectedVersion);
                if (updated != 1) {
                    throw new ApiException(
                            HttpStatus.CONFLICT, ApiErrorCode.VERSION_CONFLICT,
                            "The Request was modified by another operation.");
                }
                documents.clearCurrent(requestId);
                documents.insert(replacement);
            });
        } catch (RuntimeException exception) {
            objectStore.deleteQuietly(objectKey);
            throw exception;
        }
        return RequestSummary.from(ownedRequest(requestId, principal.id()));
    }

    public AccessLink createAccessLink(AuthenticatedUser principal, UUID documentId) {
        ScopedDocument scoped = scopedForEmployeeOwner(principal, documentId);
        var issued = accessTokens.issue(principal.id(), scoped.document().id());
        return new AccessLink(
                "/api/v1/documents/" + documentId + "/content?token=" + issued.value(),
                issued.expiresAt());
    }

    public DocumentContent content(
            AuthenticatedUser principal, UUID documentId, String token) {
        accessTokens.verify(token, principal.id(), documentId);
        Document document = scopedForEmployeeOwner(principal, documentId).document();
        try (var stored = objectStore.open(document.s3ObjectKey())) {
            return new DocumentContent(
                    document, stored.inputStream().readAllBytes());
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR,
                    "The stored document could not be read.");
        }
    }

    private Request ownedRequest(UUID requestId, UUID employeeId) {
        return requests.findOwnedById(requestId, employeeId)
                .orElseThrow(DocumentService::notFound);
    }

    private ScopedDocument scopedForEmployeeOwner(
            AuthenticatedUser principal, UUID documentId) {
        // Manager-assignment and Finance-state read scopes are activated with
        // their workflow stages. Until then, both roles receive non-disclosing 404s.
        if (principal.role() != UserRole.EMPLOYEE) {
            throw notFound();
        }
        return documents.findScopedById(documentId)
                .filter(scoped -> scoped.employeeId().equals(principal.id()))
                .orElseThrow(DocumentService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND,
                "The requested resource was not found.");
    }

    public record DocumentContent(Document document, byte[] bytes) {
    }
}
