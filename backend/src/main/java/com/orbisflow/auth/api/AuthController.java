package com.orbisflow.auth.api;

import com.orbisflow.auth.application.AuthService;
import com.orbisflow.auth.domain.JwtService;
import com.orbisflow.common.security.AuthProperties;
import com.orbisflow.common.security.CsrfTokenService;
import com.orbisflow.users.api.UserDtos.UserView;
import com.orbisflow.users.domain.User;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public static final String SESSION_COOKIE = "ORBIS_SESSION";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";

    private final AuthService authService;
    private final JwtService jwtService;
    private final CsrfTokenService csrfTokenService;
    private final AuthProperties properties;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            CsrfTokenService csrfTokenService,
            AuthProperties properties) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.csrfTokenService = csrfTokenService;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<UserView> login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletResponse response) {
        User user = authService.authenticate(request.loginIdentifier(), request.password());
        addCookie(response, SESSION_COOKIE, jwtService.issue(user), "/api", true, properties.jwtTtl());
        addCookie(response, CSRF_COOKIE, csrfTokenService.issue(user.id()), "/", false, properties.jwtTtl());
        return ResponseEntity.ok(UserView.from(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        addCookie(response, SESSION_COOKIE, "", "/api", true, Duration.ZERO);
        addCookie(response, CSRF_COOKIE, "", "/", false, Duration.ZERO);
        return ResponseEntity.ok().build();
    }

    private void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            String path,
            boolean httpOnly,
            Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .path(path)
                .httpOnly(httpOnly)
                .secure(properties.secureCookies())
                .sameSite("Strict")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
