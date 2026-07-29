package com.orbisflow.documents.persistence;

import java.net.URI;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Configuration {
    @Bean
    S3Client s3Client(S3Properties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .forcePathStyle(properties.pathStyle());

        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        if (properties.accessKey() != null && !properties.accessKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            properties.accessKey(), properties.secretKey())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        return builder.build();
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
