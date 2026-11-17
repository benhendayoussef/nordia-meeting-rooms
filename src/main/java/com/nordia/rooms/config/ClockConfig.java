package com.nordia.rooms.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    Clock clock(BookingPolicy policy) {
        return Clock.system(policy.zone());
    }
}
