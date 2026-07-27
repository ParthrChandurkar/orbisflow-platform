package com.orbisflow.auth.domain;

import com.orbisflow.common.security.AuthProperties;
import com.orbisflow.users.domain.User;
import com.orbisflow.users.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
    private final AuthProperties properties;
    private final SecretKey key;

    public JwtService(AuthProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.jwtSecret()));
    }

    public String issue(User user) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(user.id().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(properties.jwtTtl())))
                .claim("role", user.role().claimValue())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public AuthenticatedUser validate(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            UUID subject = UUID.fromString(claims.getSubject());
            UserRole role = UserRole.fromDatabase(claims.get("role", String.class));
            return new AuthenticatedUser(subject, role);
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw new InvalidJwtException();
        }
    }

    public record AuthenticatedUser(UUID id, UserRole role) {
    }

    public static final class InvalidJwtException extends RuntimeException {
    }
}
