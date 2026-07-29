package com.orbisflow.requests.application;

import com.orbisflow.audit.persistence.AuditLogRepository;
import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import com.orbisflow.notifications.persistence.NotificationRepository;
import com.orbisflow.requests.api.RequestDtos.RequestDetail;
import com.orbisflow.requests.domain.PaymentStatus;
import com.orbisflow.requests.domain.Request;
import com.orbisflow.requests.domain.RequestStatus;
import com.orbisflow.requests.persistence.RequestRepository;
import com.orbisflow.users.domain.UserRole;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FinanceProcessingService {
    private final RequestRepository requests;
    private final RequestQueryService requestQueries;
    private final NotificationRepository notifications;
    private final AuditLogRepository audit;
    private final TransactionTemplate transactions;

    public FinanceProcessingService(
            RequestRepository requests,
            RequestQueryService requestQueries,
            NotificationRepository notifications,
            AuditLogRepository audit,
            TransactionTemplate transactions) {
        this.requests = requests;
        this.requestQueries = requestQueries;
        this.notifications = notifications;
        this.audit = audit;
        this.transactions = transactions;
    }

    public RequestDetail process(
            AuthenticatedUser principal,
            UUID requestId,
            Long expectedVersion,
            String paymentStatusValue) {
        requireFinance(principal);
        long version = requireExpectedVersion(expectedVersion);
        PaymentStatus paymentStatus = requirePaymentStatus(paymentStatusValue);
        Request current = requests.findById(requestId)
                .orElseThrow(RequestQueryService::notFound);
        requireProcessStateAndVersion(current, version);

        transactions.executeWithoutResult(status -> {
            if (requests.process(
                    requestId,
                    principal.id(),
                    version,
                    paymentStatus.value()) != 1) {
                throw conflictAfterConcurrentChange(requestId, version);
            }
            audit.appendUser(
                    requestId,
                    principal.id(),
                    "processing",
                    RequestStatus.FINANCE_REVIEW,
                    RequestStatus.PROCESSED,
                    Map.of("payment_status", paymentStatus.value()));
            notifications.insert(
                    current.employeeId(), requestId, "processed");
        });
        return requestQueries.detail(
                requests.findById(requestId).orElseThrow(RequestQueryService::notFound));
    }

    private void requireProcessStateAndVersion(
            Request request,
            long expectedVersion) {
        if (request.status() != RequestStatus.FINANCE_REVIEW) {
            throw stateConflict();
        }
        if (request.version() != expectedVersion) {
            throw versionConflict();
        }
    }

    private ApiException conflictAfterConcurrentChange(
            UUID requestId,
            long expectedVersion) {
        Request current = requests.findById(requestId)
                .orElseThrow(RequestQueryService::notFound);
        if (current.status() != RequestStatus.FINANCE_REVIEW) {
            return stateConflict();
        }
        if (current.version() != expectedVersion) {
            return versionConflict();
        }
        return stateConflict();
    }

    private void requireFinance(AuthenticatedUser principal) {
        if (principal.role() != UserRole.FINANCE) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.ACCESS_DENIED,
                    "This action is not permitted.");
        }
    }

    private long requireExpectedVersion(Long value) {
        if (value == null || value < 0) {
            throw invalid("expected_version must be greater than or equal to zero.");
        }
        return value;
    }

    private PaymentStatus requirePaymentStatus(String value) {
        try {
            return PaymentStatus.fromApi(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("payment_status must be paid or scheduled.");
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_REQUEST,
                message);
    }

    private ApiException stateConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.STATE_CONFLICT,
                "Processing is not allowed in the current Request state.");
    }

    private ApiException versionConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.VERSION_CONFLICT,
                "The Request was modified by another operation.");
    }
}
