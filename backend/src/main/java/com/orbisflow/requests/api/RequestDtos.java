package com.orbisflow.requests.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orbisflow.requests.domain.Request;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class RequestDtos {
    private RequestDtos() {
    }

    public record RequestSummary(
            UUID id,
            String status,
            long version,
            @JsonProperty("employee_id") UUID employeeId,
            @JsonProperty("manager_id") UUID managerId,
            String vendor,
            @JsonProperty("total_amount") BigDecimal totalAmount,
            @JsonProperty("submitted_at") Instant submittedAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("latest_required_action") String latestRequiredAction
    ) {
        public static RequestSummary from(Request request) {
            String requiredAction = switch (request.status()) {
                case EMPLOYEE_REVIEW, REJECTED -> "correct_or_resubmit";
                case MANAGER_REVIEW -> "manager_review";
                case FINANCE_REVIEW -> "finance_process";
                default -> null;
            };
            return new RequestSummary(
                    request.id(), request.status().value(), request.version(),
                    request.employeeId(), request.managerId(), null, null,
                    request.createdAt(), request.updatedAt(), requiredAction);
        }
    }
}
