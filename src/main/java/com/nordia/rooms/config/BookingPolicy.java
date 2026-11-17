package com.nordia.rooms.config;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nordia.booking")
public record BookingPolicy(
        ZoneId zone,
        LocalTime openingStart,
        LocalTime openingEnd,
        Duration minDuration,
        Duration maxDuration,
        int maxActivePerOrganiserPerDay) {
}
