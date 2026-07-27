package com.orbisflow.auth.application;

import com.orbisflow.auth.domain.PasswordHasher;
import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import com.orbisflow.users.domain.User;
import com.orbisflow.users.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final String DUMMY_HASH =
            "$2b$12$KIXQ4T3uU7GGZLq1dcwX5ObYznTPBqRZqzM8J0iXqQUoY7K4FxT0K";
    private final UserRepository users;
    private final PasswordHasher passwordHasher;

    public AuthService(UserRepository users, PasswordHasher passwordHasher) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    public User authenticate(String loginIdentifier, String password) {
        User user = users.findByLoginIdentifier(loginIdentifier).orElse(null);
        String hash = user == null ? DUMMY_HASH : user.passwordHash();
        boolean matches = passwordHasher.matches(password, hash);
        if (user == null || !matches) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ApiErrorCode.INVALID_CREDENTIALS,
                    "The supplied credentials are invalid.");
        }
        return user;
    }
}
