package com.orbisflow.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class CsrfTokenService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();

    public CsrfTokenService(AuthProperties properties) {
        this.secret = Base64.getDecoder().decode(properties.csrfSecret());
    }

    public String issue(UUID subject) {
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        String payload = subject + "." + ENCODER.encodeToString(nonce);
        return payload + "." + sign(payload);
    }

    public boolean isValid(String cookieValue, String headerValue, UUID subject) {
        if (cookieValue == null || headerValue == null
                || !MessageDigest.isEqual(
                        cookieValue.getBytes(StandardCharsets.UTF_8),
                        headerValue.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        String[] parts = cookieValue.split("\\.", -1);
        if (parts.length != 3 || !parts[0].equals(subject.toString())) {
            return false;
        }
        String expected = sign(parts[0] + "." + parts[1]);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create CSRF token", exception);
        }
    }
}
