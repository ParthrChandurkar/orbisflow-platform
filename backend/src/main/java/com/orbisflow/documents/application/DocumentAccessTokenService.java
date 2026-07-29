package com.orbisflow.documents.application;

import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import com.orbisflow.documents.persistence.S3Properties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DocumentAccessTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] secret;
    private final Clock clock;
    private final S3Properties properties;

    public DocumentAccessTokenService(S3Properties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.secret = Base64.getDecoder().decode(properties.signedLinkSecret());
    }

    public IssuedToken issue(UUID subjectId, UUID documentId) {
        Instant expiresAt = Instant.now(clock).plus(properties.signedLinkTtl());
        return new IssuedToken(issue(subjectId, documentId, expiresAt), expiresAt);
    }

    public String issue(UUID subjectId, UUID documentId, Instant expiresAt) {
        String payload = subjectId + ":" + documentId + ":" + expiresAt.getEpochSecond();
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public void verify(String token, UUID subjectId, UUID documentId) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 2
                    || !MessageDigest.isEqual(
                    sign(parts[0]).getBytes(StandardCharsets.US_ASCII),
                    parts[1].getBytes(StandardCharsets.US_ASCII))) {
                throw invalid();
            }
            String payload = new String(
                    Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] values = payload.split(":", -1);
            if (values.length != 3
                    || !UUID.fromString(values[0]).equals(subjectId)
                    || !UUID.fromString(values[1]).equals(documentId)
                    || !Instant.now(clock).isBefore(
                    Instant.ofEpochSecond(Long.parseLong(values[2])))) {
                throw invalid();
            }
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign document access token.", exception);
        }
    }

    private static ApiException invalid() {
        return new ApiException(
                HttpStatus.FORBIDDEN, ApiErrorCode.SIGNED_LINK_INVALID,
                "The signed document link is missing, expired, or invalid.");
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
