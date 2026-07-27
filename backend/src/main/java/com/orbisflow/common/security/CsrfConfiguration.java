package com.orbisflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbisflow.auth.api.AuthController;
import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CsrfConfiguration extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final CsrfTokenService tokenService;
    private final ObjectMapper objectMapper;

    public CsrfConfiguration(CsrfTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (SAFE_METHODS.contains(request.getMethod())
                || "/api/v1/auth/login".equals(request.getRequestURI())
                || authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            filterChain.doFilter(request, response);
            return;
        }
        String cookie = JwtAuthenticationFilter.cookie(request, AuthController.CSRF_COOKIE);
        String header = request.getHeader("X-XSRF-TOKEN");
        if (!tokenService.isValid(cookie, header, principal.id())) {
            ApiErrorWriter.write(
                    objectMapper, request, response, 403, ApiErrorCode.CSRF_INVALID,
                    "The CSRF token is missing or invalid.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
