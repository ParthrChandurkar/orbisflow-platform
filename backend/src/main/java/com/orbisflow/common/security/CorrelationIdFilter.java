package com.orbisflow.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String correlationId = validUuid(incoming) ? incoming : UUID.randomUUID().toString();
        request.setAttribute("correlationId", correlationId);
        response.setHeader(HEADER, correlationId);
        filterChain.doFilter(request, response);
    }

    private boolean validUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value.toLowerCase());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
