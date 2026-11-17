package com.nordia.rooms.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nordia")
public record DemoUsers(List<DemoUser> demoUsers) {

    public record DemoUser(String email, String password, String displayName) {
    }
}
