package com.orbisflow.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(
            @JsonProperty("login_identifier") @NotBlank String loginIdentifier,
            @NotBlank String password
    ) {
    }
}
