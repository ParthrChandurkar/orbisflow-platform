package com.orbisflow.requests.application;

import com.orbisflow.audit.persistence.AuditLogRepository;
import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import com.orbisflow.notifications.persistence.NotificationRepository;
import com.orbisflow.requests.api.RequestDtos.RequestDetail;
import com.orbisflow.requests.domain.Request;
import com.orbisflow.requests.domain.RequestStatus;
import com.orbisflow.requests.persistence.RequestRepository;
import com.orbisflow.users.domain.UserRole;
import com.orbisflow.users.persistence.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ManagerDecisionService {
    private final RequestRepository requests;
    private final RequestQueryService requestQueries;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final AuditLogRepository audit;
    private final TransactionTemplate transactions;

    public ManagerDecisionService(
            RequestRepository requests,
            RequestQueryService requestQueries,
            UserRepository users,
            NotificationRepository notifications,
            AuditLogRepository audit,
            TransactionTemplate transactions) {
        this.requests = requests;
        this.requestQueries = requestQueries;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
        this.transactions = transactions;
    }

    public RequestDetail approve(
            AuthenticatedUser principal,
            UUID requestId,
            Long expectedVersion) {
        requireManager(principal);
        long version = requireExpectedVersion(expectedVersion);
        Request current = requireAssignedPostRouting(principal.id(), requestId);
        requireDecisionStateAndVersion(current, version);

        transactions.executeWithoutResult(status -> {
            if (requests.approve(requestId, principal.id(), version) != 1) {
                throw conflictAfterConcurrentChange(principal.id(), requestId, version);
            }
            audit.appendUser(
                    requestId,
                    principal.id(),
                    "approval",
                    RequestStatus.MANAGER_REVIEW,
                    RequestStatus.FINANCE_REVIEW,
                    Map.of("decision", "approved"));
            for (UUID financeUserId : users.findIdsByRole(UserRole.FINANCE)) {
                notifications.insert(financeUserId, requestId, "finance_assignment");
            }
        });
        return requestQueries.detail(
                requireAssignedPostRouting(principal.id(), requestId));
    }

    public RequestDetail reject(
            AuthenticatedUser principal,
            UUID requestId,
            Long expectedVersion,
            String reason) {
        requireManager(principal);
        long version = requireExpectedVersion(expectedVersion);
        String normalizedReason = requireReason(reason);
        Request current = requireAssignedPostRouting(principal.id(), requestId);
        requireDecisionStateAndVersion(current, version);

        transactions.executeWithoutResult(status -> {
            if (requests.reject(
                    requestId, principal.id(), version, normalizedReason) != 1) {
                throw conflictAfterConcurrentChange(principal.id(), requestId, version);
            }
            audit.appendUser(
                    requestId,
                    principal.id(),
                    "rejection",
                    RequestStatus.MANAGER_REVIEW,
                    RequestStatus.REJECTED,
                    Map.of("decision", "rejected", "reason", normalizedReason));
            notifications.insert(
                    current.employeeId(), requestId, "employee_rejection");
        });
        return requestQueries.detail(
                requireAssignedPostRouting(principal.id(), requestId));
    }

    private Request requireAssignedPostRouting(UUID managerId, UUID requestId) {
        return requests.findAssignedPostRouting(requestId, managerId)
                .orElseThrow(RequestQueryService::notFound);
    }

    private void requireDecisionStateAndVersion(Request request, long expectedVersion) {
        if (request.status() != RequestStatus.MANAGER_REVIEW) {
            throw stateConflict();
        }
        if (request.version() != expectedVersion) {
            throw versionConflict();
        }
    }

    private ApiException conflictAfterConcurrentChange(
            UUID managerId,
            UUID requestId,
            long expectedVersion) {
        Request current = requireAssignedPostRouting(managerId, requestId);
        if (current.status() != RequestStatus.MANAGER_REVIEW) {
            return stateConflict();
        }
        if (current.version() != expectedVersion) {
            return versionConflict();
        }
        return stateConflict();
    }

    private void requireManager(AuthenticatedUser principal) {
        if (principal.role() != UserRole.MANAGER) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.ACCESS_DENIED,
                    "This action is not permitted.");
        }
    }

    private long requireExpectedVersion(Long value) {
        if (value == null || value < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.INVALID_REQUEST,
                    "expected_version must be greater than or equal to zero.");
        }
        return value;
    }

    private String requireReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.INVALID_REQUEST,
                    "reason must not be blank.");
        }
        return reason.trim();
    }

    private ApiException stateConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.STATE_CONFLICT,
                "The Manager decision is not allowed in the current Request state.");
    }

    private ApiException versionConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.VERSION_CONFLICT,
                "The Request was modified by another operation.");
    }
}
