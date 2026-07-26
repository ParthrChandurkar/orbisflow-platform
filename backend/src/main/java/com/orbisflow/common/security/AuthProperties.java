package com.orbisflow.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orbisflow.auth")
public record AuthProperties(
        String jwtSecret,
        String csrfSecret,
        Duration jwtTtl,
        boolean secureCookies,
        String allowedOrigin
) {
}
