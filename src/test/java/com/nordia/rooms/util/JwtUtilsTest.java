package com.nordia.rooms.util;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import javax.crypto.SecretKey;

import com.nordia.rooms.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    private static final String SECRET = "a-secret-that-is-long-enough-for-hs256";
    private static final Instant NOW = Instant.parse("2026-11-17T08:00:00Z");

    private final JwtUtils jwtUtils = jwtUtilsAt(NOW, SECRET);

    @Test
    void puts_the_email_a_random_id_and_a_one_hour_expiry_in_the_token() {
        String token = jwtUtils.generateAccessJwtToken("alice@nordia.com");

        Claims claims = Jwts.parser().verifyWith(key(SECRET)).clock(() -> java.util.Date.from(NOW)).build()
                .parseSignedClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo("alice@nordia.com");
        assertThat(UUID.fromString(claims.getId())).isNotNull();
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(jwtUtils.extractEmail(token)).isEqualTo("alice@nordia.com");
        assertThat(jwtUtils.extractId(token)).isEqualTo(claims.getId());
        assertThat(jwtUtils.extractExpiration(token)).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void gives_every_token_its_own_id() {
        String first = jwtUtils.generateAccessJwtToken("alice@nordia.com");
        String second = jwtUtils.generateAccessJwtToken("alice@nordia.com");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void accepts_a_token_until_it_expires() {
        String token = jwtUtils.generateAccessJwtToken("alice@nordia.com");

        assertThat(jwtUtilsAt(NOW.plus(Duration.ofMinutes(59)), SECRET).validateJwtToken(token)).isTrue();
        assertThat(jwtUtilsAt(NOW.plus(Duration.ofMinutes(61)), SECRET).validateJwtToken(token)).isFalse();
    }

    @Test
    void refuses_a_token_signed_with_another_secret() {
        String token = jwtUtilsAt(NOW, "another-secret-that-is-long-enough-123").generateAccessJwtToken("alice@nordia.com");

        assertThat(jwtUtils.validateJwtToken(token)).isFalse();
    }

    @Test
    void refuses_a_tampered_token() {
        String token = jwtUtils.generateAccessJwtToken("alice@nordia.com");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(jwtUtils.validateJwtToken(tampered)).isFalse();
    }

    @Test
    void refuses_an_unsigned_token() {
        String unsigned = Jwts.builder().subject("alice@nordia.com").compact();

        assertThat(jwtUtils.validateJwtToken(unsigned)).isFalse();
    }

    @Test
    void refuses_text_that_is_not_a_token() {
        assertThat(jwtUtils.validateJwtToken("not-a-token")).isFalse();
        assertThat(jwtUtils.validateJwtToken("")).isFalse();
    }

    @Test
    void uses_a_random_secret_when_none_is_configured() {
        JwtUtils first = jwtUtilsAt(NOW, "");
        JwtUtils second = jwtUtilsAt(NOW, "");

        String token = first.generateAccessJwtToken("alice@nordia.com");

        assertThat(first.validateJwtToken(token)).isTrue();
        assertThat(second.validateJwtToken(token)).isFalse();
    }

    @Test
    void refuses_to_start_with_a_secret_that_is_too_short() {
        assertThatThrownBy(() -> jwtUtilsAt(NOW, "too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("NORDIA_JWT_SECRET must be at least 32 characters long.");
    }

    @Test
    void reads_the_token_from_a_bearer_header() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(jwtUtils.extractJwtFromHeader(request)).isNull();

        request.addHeader("Authorization", "Basic abc");
        assertThat(jwtUtils.extractJwtFromHeader(request)).isNull();

        MockHttpServletRequest bearer = new MockHttpServletRequest();
        bearer.addHeader("Authorization", "Bearer abc.def.ghi");
        assertThat(jwtUtils.extractJwtFromHeader(bearer)).isEqualTo("abc.def.ghi");
    }

    private static JwtUtils jwtUtilsAt(Instant now, String secret) {
        return new JwtUtils(new JwtProperties(secret, Duration.ofHours(1)),
                Clock.fixed(now, ZoneId.of("Europe/Paris")));
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
