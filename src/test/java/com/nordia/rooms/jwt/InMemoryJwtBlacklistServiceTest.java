package com.nordia.rooms.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryJwtBlacklistServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T08:00:00Z");

    private final Clock clock = mock(Clock.class);
    private final InMemoryJwtBlacklistService blacklist = new InMemoryJwtBlacklistService(clock);

    @Test
    void remembers_a_revoked_token_until_it_expires() {
        when(clock.instant()).thenReturn(NOW);
        blacklist.blacklistToken("jti-1", NOW.plus(Duration.ofHours(1)));

        assertThat(blacklist.isTokenBlacklisted("jti-1")).isTrue();
        assertThat(blacklist.isTokenBlacklisted("jti-2")).isFalse();

        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(1)));
        assertThat(blacklist.isTokenBlacklisted("jti-1")).isFalse();
    }

    @Test
    void removes_expired_entries_when_another_token_is_revoked() {
        when(clock.instant()).thenReturn(NOW);
        blacklist.blacklistToken("old", NOW.plus(Duration.ofHours(1)));

        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(2)));
        blacklist.blacklistToken("new", NOW.plus(Duration.ofHours(3)));

        @SuppressWarnings("unchecked")
        Map<String, Instant> stored = (Map<String, Instant>) ReflectionTestUtils.getField(blacklist, "revoked");
        assertThat(stored).containsOnlyKeys("new");
    }
}
