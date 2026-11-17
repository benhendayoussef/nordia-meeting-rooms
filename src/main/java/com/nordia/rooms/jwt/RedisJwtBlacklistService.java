package com.nordia.rooms.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "nordia.security.token-blacklist", havingValue = "redis")
public class RedisJwtBlacklistService implements JwtBlacklistService {

    private static final String PREFIX = "nordia:revoked-jti:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public RedisJwtBlacklistService(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    @Override
    public void blacklistToken(String jti, Instant expiresAt) {
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        if (remaining.isPositive()) {
            redisTemplate.opsForValue().set(PREFIX + jti, "revoked", remaining);
        }
    }

    @Override
    public boolean isTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }
}
