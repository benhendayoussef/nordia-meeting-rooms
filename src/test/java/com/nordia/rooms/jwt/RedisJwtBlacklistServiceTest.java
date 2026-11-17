package com.nordia.rooms.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisJwtBlacklistServiceTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redisTemplate;

    RedisJwtBlacklistService blacklist;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void createBlacklist() {
        blacklist = new RedisJwtBlacklistService(redisTemplate, Clock.systemUTC());
    }

    @Test
    void remembers_a_revoked_token() {
        blacklist.blacklistToken("jti-remembered", Instant.now().plus(Duration.ofHours(1)));

        assertThat(blacklist.isTokenBlacklisted("jti-remembered")).isTrue();
        assertThat(blacklist.isTokenBlacklisted("jti-unknown")).isFalse();
    }

    @Test
    void keeps_the_entry_for_the_remaining_lifetime_of_the_token() {
        blacklist.blacklistToken("jti-ttl", Instant.now().plus(Duration.ofMinutes(30)));

        Long secondsLeft = redisTemplate.getExpire("nordia:revoked-jti:jti-ttl", TimeUnit.SECONDS);

        assertThat(secondsLeft).isBetween(29L * 60, 30L * 60);
    }

    @Test
    void does_not_store_a_token_that_has_already_expired() {
        blacklist.blacklistToken("jti-expired", Instant.now().minusSeconds(5));

        assertThat(redisTemplate.hasKey("nordia:revoked-jti:jti-expired")).isFalse();
        assertThat(blacklist.isTokenBlacklisted("jti-expired")).isFalse();
    }
}
