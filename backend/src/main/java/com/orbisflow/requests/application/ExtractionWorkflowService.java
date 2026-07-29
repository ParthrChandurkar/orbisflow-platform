package com.orbisflow.requests.application;

import com.orbisflow.audit.persistence.AuditLogRepository;
import com.orbisflow.integration.ai.FastApiExtractionDtos.ClientResult;
import com.orbisflow.notifications.persistence.NotificationRepository;
import com.orbisflow.requests.domain.ExtractedInvoiceData.InvoiceLineItem;
import com.orbisflow.requests.domain.Request;
import com.orbisflow.requests.domain.RequestStatus;
import com.orbisflow.requests.persistence.ExtractedInvoiceDataRepository;
import com.orbisflow.requests.persistence.RequestRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExtractionWorkflowService {
    private final RequestRepository requests;
    private final ExtractedInvoiceDataRepository extractedData;
    private final InvoiceValidationService validation;
    private final AuditLogRepository audit;
    private final NotificationRepository notifications;
    private final TransactionTemplate transactions;

    public ExtractionWorkflowService(
            RequestRepository requests,
            ExtractedInvoiceDataRepository extractedData,
            InvoiceValidationService validation,
            AuditLogRepository audit,
            NotificationRepository notifications,
            TransactionTemplate transactions) {
        this.requests = requests;
        this.extractedData = extractedData;
        this.validation = validation;
        this.audit = audit;
        this.notifications = notifications;
        this.transactions = transactions;
    }

    public void complete(UUID requestId, long attemptVersion, ClientResult result) {
        transactions.executeWithoutResult(status -> {
            Request current = requests.findById(requestId).orElse(null);
            if (current == null
                    || current.status() != RequestStatus.UPLOADED_EXTRACTING
                    || current.version() != attemptVersion) {
                return;
            }
            if (result.response() == null) {
                persistFailure(requestId, result.failureCategory());
                return;
            }
            var response = result.response();
            List<InvoiceLineItem> items = response.lineItems().stream()
                    .map(item -> new InvoiceLineItem(
                            item.lineNumber(), item.description(), item.amount()))
                    .toList();
            InvoiceValidationService.ValidationResult checked;
            try {
                checked = validation.validate(
                        response.vendor(),
                        response.totalAmount(),
                        response.invoiceDate(),
                        items);
            } catch (RuntimeException exception) {
                persistFailure(requestId, "invalid_response");
                return;
            }
            RequestStatus target = checked.flags().isEmpty()
                    ? RequestStatus.MANAGER_REVIEW
                    : RequestStatus.EMPLOYEE_REVIEW;
            if (requests.transitionAfterExtraction(requestId, attemptVersion, target) != 1) {
                return;
            }
            extractedData.saveSucceeded(
                    requestId,
                    checked.vendor(),
                    checked.totalAmount(),
                    checked.invoiceDate(),
                    checked.lineItems(),
                    checked.flags());
            audit.appendSystem(
                    requestId,
                    "extraction",
                    RequestStatus.UPLOADED_EXTRACTING,
                    RequestStatus.UPLOADED_EXTRACTING,
                    Map.of("result", "succeeded", "schema_version", "1"));
            audit.appendSystem(
                    requestId,
                    "validation",
                    RequestStatus.UPLOADED_EXTRACTING,
                    RequestStatus.UPLOADED_EXTRACTING,
                    Map.of("validation_flags", checked.flags()));
            audit.appendSystem(
                    requestId,
                    "routing",
                    RequestStatus.UPLOADED_EXTRACTING,
                    target,
                    Map.of("route", target.value()));
            if (target == RequestStatus.MANAGER_REVIEW) {
                notifications.insert(
                        current.managerId(), requestId, "manager_assignment");
            } else {
                notifications.insert(
                        current.employeeId(), requestId, "employee_correction");
            }
        });
    }

    public void markSchedulingFailure(UUID requestId, long attemptVersion) {
        complete(requestId, attemptVersion, ClientResult.failed("service_unavailable"));
    }

    private void persistFailure(UUID requestId, String failureCategory) {
        extractedData.saveFailed(requestId, failureCategory);
        audit.appendSystem(
                requestId,
                "extraction",
                RequestStatus.UPLOADED_EXTRACTING,
                RequestStatus.UPLOADED_EXTRACTING,
                Map.of("result", "failed", "failure_category", failureCategory));
    }
}
