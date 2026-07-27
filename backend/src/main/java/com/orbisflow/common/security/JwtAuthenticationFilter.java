package com.orbisflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbisflow.auth.api.AuthController;
import com.orbisflow.auth.domain.JwtService;
import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.auth.domain.JwtService.InvalidJwtException;
import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/api/v1/auth/login".equals(request.getRequestURI())
                || "/api/v1/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = cookie(request, AuthController.SESSION_COOKIE);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            AuthenticatedUser principal = jwtService.validate(token);
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, token,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (InvalidJwtException exception) {
            SecurityContextHolder.clearContext();
            ApiErrorWriter.write(
                    objectMapper, request, response, 401, ApiErrorCode.AUTH_REQUIRED,
                    "Authentication is required.");
        }
    }

    static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
