package com.orbisflow.integration.ai;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orbisflow.ai-service")
public record FastApiProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public FastApiProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(60) : readTimeout;
    }
}
