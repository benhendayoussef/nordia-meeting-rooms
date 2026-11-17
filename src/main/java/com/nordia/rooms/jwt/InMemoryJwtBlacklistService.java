package com.nordia.rooms.jwt;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "nordia.security.token-blacklist", havingValue = "memory", matchIfMissing = true)
public class InMemoryJwtBlacklistService implements JwtBlacklistService {

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryJwtBlacklistService(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void blacklistToken(String jti, Instant expiresAt) {
        removeExpired();
        revoked.put(jti, expiresAt);
    }

    @Override
    public boolean isTokenBlacklisted(String jti) {
        Instant expiresAt = revoked.get(jti);
        return expiresAt != null && expiresAt.isAfter(clock.instant());
    }

    // A token that has expired is refused anyway, so its entry is no longer needed.
    private void removeExpired() {
        Instant now = clock.instant();
        revoked.values().removeIf(expiresAt -> !expiresAt.isAfter(now));
    }
}
