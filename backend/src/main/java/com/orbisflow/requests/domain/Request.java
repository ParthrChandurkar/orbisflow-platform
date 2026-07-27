package com.orbisflow.requests.domain;

import java.time.Instant;
import java.util.UUID;

public record Request(
        UUID id,
        UUID employeeId,
        UUID managerId,
        RequestStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
