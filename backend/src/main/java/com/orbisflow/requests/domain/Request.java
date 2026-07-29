package com.orbisflow.requests.domain;

import java.time.Instant;
import java.util.UUID;

public record Request(
        UUID id,
        UUID employeeId,
        UUID managerId,
        RequestStatus status,
        long version,
        String managerDecision,
        UUID managerDecidedByUserId,
        Instant managerDecidedAt,
        String rejectionReason,
        String paymentStatus,
        UUID processedByUserId,
        Instant processedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
