package com.nordia.rooms.jwt;

import java.time.Instant;

public interface JwtBlacklistService {

    void blacklistToken(String jti, Instant expiresAt);

    boolean isTokenBlacklisted(String jti);
}
