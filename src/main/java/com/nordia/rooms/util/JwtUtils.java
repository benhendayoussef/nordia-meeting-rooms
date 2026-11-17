package com.nordia.rooms.util;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

import com.nordia.rooms.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;
    private final JwtParser jwtParser;

    public JwtUtils(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = secretKey(properties.secret());
        this.jwtParser = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public String generateAccessJwtToken(String email) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(email)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.expiration())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return jwtParser.parseSignedClaims(token).getPayload().getSubject();
    }

    public Instant extractExpiration(String token) {
        return jwtParser.parseSignedClaims(token).getPayload().getExpiration().toInstant();
    }

    public String extractJwtFromHeader(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    public boolean validateJwtToken(String token) {
        try {
            jwtParser.parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private static SecretKey secretKey(String secret) {
        if (secret == null || secret.isBlank()) {
            logger.warn("NORDIA_JWT_SECRET is not set: using a random secret. "
                    + "Tokens stop working when the application restarts.");
            return Jwts.SIG.HS256.key().build();
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("NORDIA_JWT_SECRET must be at least 32 characters long.");
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
