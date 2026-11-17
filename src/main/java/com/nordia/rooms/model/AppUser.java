package com.nordia.rooms.model;

public record AppUser(String email, String passwordHash, String displayName) {
}
