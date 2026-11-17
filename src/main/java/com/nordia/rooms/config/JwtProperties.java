package com.nordia.rooms.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nordia.jwt")
public record JwtProperties(String secret, Duration expiration) {
}
