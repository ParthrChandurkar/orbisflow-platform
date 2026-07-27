package com.orbisflow.documents.persistence;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orbisflow.storage.s3")
public record S3Properties(
        String bucket,
        String region,
        String endpoint,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        String signedLinkSecret,
        Duration signedLinkTtl
) {
}
