package com.orbisflow.users.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orbisflow.users.domain.User;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {
    }

    public record UserView(
            UUID id,
            @JsonProperty("login_identifier") String loginIdentifier,
            String role,
            @JsonProperty("manager_id") UUID managerId
    ) {
        public static UserView from(User user) {
            return new UserView(
                    user.id(), user.loginIdentifier(), user.role().claimValue(), user.managerId());
        }
    }
}
