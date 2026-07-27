package com.orbisflow.requests.application;

import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import com.orbisflow.documents.application.InvoiceFileValidator;
import com.orbisflow.documents.domain.Document;
import com.orbisflow.documents.persistence.DocumentRepository;
import com.orbisflow.documents.persistence.S3DocumentStore;
import com.orbisflow.requests.api.RequestDtos.RequestSummary;
import com.orbisflow.requests.domain.Request;
import com.orbisflow.requests.domain.RequestStatus;
import com.orbisflow.requests.persistence.RequestRepository;
import com.orbisflow.users.domain.UserRole;
import com.orbisflow.users.persistence.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RequestCommandService {
    private final UserRepository users;
    private final RequestRepository requests;
    private final DocumentRepository documents;
    private final S3DocumentStore objectStore;
    private final InvoiceFileValidator validator;
    private final TransactionTemplate transactions;

    public RequestCommandService(
            UserRepository users,
            RequestRepository requests,
            DocumentRepository documents,
            S3DocumentStore objectStore,
            InvoiceFileValidator validator,
            TransactionTemplate transactions) {
        this.users = users;
        this.requests = requests;
        this.documents = documents;
        this.objectStore = objectStore;
        this.validator = validator;
        this.transactions = transactions;
    }

    public RequestSummary create(AuthenticatedUser principal, MultipartFile file) {
        var employee = users.findById(principal.id())
                .filter(user -> user.role() == UserRole.EMPLOYEE)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED,
                        "This action is not permitted."));
        UUID managerId = employee.managerId();
        boolean assignedToManager = managerId != null
                && users.findById(managerId)
                .map(manager -> manager.role() == UserRole.MANAGER)
                .orElse(false);
        if (!assignedToManager) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ApiErrorCode.MANAGER_NOT_ASSIGNED,
                    "The Employee does not have an assigned Manager.");
        }

        var upload = validator.validate(file);
        UUID requestId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        String objectKey = "documents/" + UUID.randomUUID();
        Instant now = Instant.now();
        Request request = new Request(
                requestId, employee.id(), managerId,
                RequestStatus.UPLOADED_EXTRACTING, 0, now, now);
        Document document = new Document(
                documentId, requestId, employee.id(), objectKey,
                upload.filename(), upload.mimeType(), upload.bytes().length, true, now);

        objectStore.put(objectKey, upload.bytes(), upload.mimeType());
        try {
            transactions.executeWithoutResult(status -> {
                requests.insert(request);
                documents.insert(document);
            });
        } catch (RuntimeException exception) {
            objectStore.deleteQuietly(objectKey);
            throw exception;
        }
        return RequestSummary.from(request);
    }
}
