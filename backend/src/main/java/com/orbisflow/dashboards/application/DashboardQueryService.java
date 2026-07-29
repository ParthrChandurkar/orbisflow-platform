package com.orbisflow.dashboards.application;

import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.common.api.PageResponse;
import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import com.orbisflow.dashboards.api.DashboardDtos.TeamActivity;
import com.orbisflow.dashboards.persistence.DashboardQueryRepository;
import com.orbisflow.requests.api.RequestDtos.RequestSummary;
import com.orbisflow.users.domain.UserRole;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DashboardQueryService {
    private static final Set<String> EMPLOYEE_STATUSES = Set.of(
            "uploaded_extracting", "employee_review", "manager_review",
            "rejected", "finance_review", "processed");
    private static final Map<String, String> EMPLOYEE_SORT_COLUMNS = Map.of(
            "submitted_at", "r.created_at",
            "updated_at", "r.updated_at",
            "total_amount", "e.total_amount",
            "status", "r.status");
    private static final Set<String> MANAGER_STATUSES = Set.of(
            "manager_review", "rejected", "finance_review", "processed");
    private static final Map<String, String> MANAGER_SORT_COLUMNS = Map.of(
            "submitted_at", "r.created_at",
            "updated_at", "r.updated_at",
            "total_amount", "e.total_amount",
            "status", "r.status");
    private static final Map<String, String> FINANCE_QUEUE_SORT_COLUMNS = Map.of(
            "updated_at", "r.updated_at",
            "total_amount", "e.total_amount",
            "status", "r.status");
    private static final Map<String, String> FINANCE_PROCESSED_SORT_COLUMNS = Map.of(
            "processed_at", "r.processed_at",
            "updated_at", "r.updated_at",
            "total_amount", "e.total_amount");

    private final DashboardQueryRepository repository;

    public DashboardQueryService(DashboardQueryRepository repository) {
        this.repository = repository;
    }

    public PageResponse<RequestSummary> employeeRequests(
            AuthenticatedUser principal,
            String status,
            Integer page,
            Integer size,
            String sort,
            String direction) {
        requireEmployee(principal);
        String effectiveSort = sort == null ? "submitted_at" : sort;
        String effectiveDirection = direction == null
                ? "desc"
                : direction.toLowerCase();
        int effectivePage = page == null ? 0 : page;
        int effectiveSize = size == null ? 20 : size;
        if ((status != null && !EMPLOYEE_STATUSES.contains(status))
                || !EMPLOYEE_SORT_COLUMNS.containsKey(effectiveSort)
                || (!effectiveDirection.equals("asc")
                        && !effectiveDirection.equals("desc"))
                || effectivePage < 0
                || effectiveSize < 1
                || effectiveSize > 100) {
            throw invalid();
        }
        var items = repository.employeeRequests(
                principal.id(),
                status,
                effectivePage,
                effectiveSize,
                EMPLOYEE_SORT_COLUMNS.get(effectiveSort),
                effectiveDirection.toUpperCase());
        long count = repository.employeeRequestCount(principal.id(), status);
        return PageResponse.of(
                items,
                effectivePage,
                effectiveSize,
                count,
                effectiveSort,
                effectiveDirection);
    }

    public PageResponse<RequestSummary> managerRequests(
            AuthenticatedUser principal,
            String status,
            Integer page,
            Integer size,
            String sort,
            String direction) {
        requireManager(principal);
        String effectiveStatus = status == null ? "manager_review" : status;
        String effectiveSort = sort == null ? "updated_at" : sort;
        String effectiveDirection = direction == null ? "asc" : direction.toLowerCase();
        int effectivePage = page == null ? 0 : page;
        int effectiveSize = size == null ? 20 : size;
        if (!MANAGER_STATUSES.contains(effectiveStatus)
                || !MANAGER_SORT_COLUMNS.containsKey(effectiveSort)
                || (!effectiveDirection.equals("asc")
                        && !effectiveDirection.equals("desc"))
                || effectivePage < 0
                || effectiveSize < 1
                || effectiveSize > 100) {
            throw invalid();
        }
        var items = repository.managerRequests(
                principal.id(),
                effectiveStatus,
                effectivePage,
                effectiveSize,
                MANAGER_SORT_COLUMNS.get(effectiveSort),
                effectiveDirection.toUpperCase());
        long count = repository.managerRequestCount(
                principal.id(), effectiveStatus);
        return PageResponse.of(
                items,
                effectivePage,
                effectiveSize,
                count,
                effectiveSort,
                effectiveDirection);
    }

    public PageResponse<RequestSummary> financeRequests(
            AuthenticatedUser principal,
            String status,
            Integer page,
            Integer size,
            String sort,
            String direction) {
        requireFinance(principal);
        String effectiveStatus = status == null ? "finance_review" : status;
        boolean processed = effectiveStatus.equals("processed");
        Map<String, String> allowedSorts = processed
                ? FINANCE_PROCESSED_SORT_COLUMNS
                : FINANCE_QUEUE_SORT_COLUMNS;
        String effectiveSort = sort == null
                ? (processed ? "processed_at" : "updated_at")
                : sort;
        String effectiveDirection = direction == null
                ? (processed ? "desc" : "asc")
                : direction.toLowerCase();
        int effectivePage = page == null ? 0 : page;
        int effectiveSize = size == null ? 20 : size;
        if ((!effectiveStatus.equals("finance_review") && !processed)
                || !allowedSorts.containsKey(effectiveSort)
                || (!effectiveDirection.equals("asc")
                        && !effectiveDirection.equals("desc"))
                || effectivePage < 0
                || effectiveSize < 1
                || effectiveSize > 100) {
            throw invalid();
        }
        var items = repository.financeRequests(
                effectiveStatus,
                effectivePage,
                effectiveSize,
                allowedSorts.get(effectiveSort),
                effectiveDirection.toUpperCase());
        long count = repository.financeRequestCount(effectiveStatus);
        return PageResponse.of(
                items,
                effectivePage,
                effectiveSize,
                count,
                effectiveSort,
                effectiveDirection);
    }

    public TeamActivity teamActivity(AuthenticatedUser principal) {
        requireManager(principal);
        return repository.teamActivity(principal.id());
    }

    private void requireEmployee(AuthenticatedUser principal) {
        if (principal.role() != UserRole.EMPLOYEE) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.ACCESS_DENIED,
                    "This action is not permitted.");
        }
    }

    private void requireManager(AuthenticatedUser principal) {
        if (principal.role() != UserRole.MANAGER) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.ACCESS_DENIED,
                    "This action is not permitted.");
        }
    }

    private void requireFinance(AuthenticatedUser principal) {
        if (principal.role() != UserRole.FINANCE) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.ACCESS_DENIED,
                    "This action is not permitted.");
        }
    }

    private ApiException invalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_REQUEST,
                "The dashboard query parameters are invalid.");
    }
}
