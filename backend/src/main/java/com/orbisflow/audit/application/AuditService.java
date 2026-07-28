package com.orbisflow.audit.application;

import com.orbisflow.audit.api.AuditDtos.AuditEventView;
import com.orbisflow.audit.persistence.AuditLogRepository;
import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.common.api.PageResponse;
import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import com.orbisflow.requests.application.RequestQueryService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final RequestQueryService requestQueries;
    private final AuditLogRepository audit;

    public AuditService(
            RequestQueryService requestQueries,
            AuditLogRepository audit) {
        this.requestQueries = requestQueries;
        this.audit = audit;
    }

    public PageResponse<AuditEventView> get(
            AuthenticatedUser principal,
            UUID requestId,
            Integer page,
            Integer size) {
        int effectivePage = page == null ? 0 : page;
        int effectiveSize = size == null ? 50 : size;
        if (effectivePage < 0 || effectiveSize < 1 || effectiveSize > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.INVALID_REQUEST,
                    "The audit pagination parameters are invalid.");
        }
        requestQueries.requireScoped(principal, requestId);
        var items = audit.findPage(requestId, effectivePage, effectiveSize).stream()
                .map(record -> new AuditEventView(
                        record.id(),
                        record.eventType(),
                        record.actorKind(),
                        record.actorUserId(),
                        record.previousStatus(),
                        record.resultingStatus(),
                        record.context(),
                        record.createdAt()))
                .toList();
        return PageResponse.of(
                items,
                effectivePage,
                effectiveSize,
                audit.count(requestId),
                "created_at",
                "asc");
    }
}
