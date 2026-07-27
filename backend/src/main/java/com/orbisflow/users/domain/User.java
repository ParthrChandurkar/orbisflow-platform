package com.orbisflow.users.domain;

import java.util.UUID;

public record User(
        UUID id,
        String loginIdentifier,
        String passwordHash,
        UserRole role,
        UUID managerId
) {
}
