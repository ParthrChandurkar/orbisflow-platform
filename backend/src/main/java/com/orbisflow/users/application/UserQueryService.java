package com.orbisflow.users.application;

import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import com.orbisflow.users.domain.User;
import com.orbisflow.users.persistence.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserQueryService {
    private final UserRepository users;

    public UserQueryService(UserRepository users) {
        this.users = users;
    }

    public User currentUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.AUTH_REQUIRED,
                "Authentication is required."));
    }
}
