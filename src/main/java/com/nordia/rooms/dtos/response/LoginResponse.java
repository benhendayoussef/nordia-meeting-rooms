package com.nordia.rooms.dtos.response;

import java.time.OffsetDateTime;

public record LoginResponse(String accessToken, OffsetDateTime expiresAt) {
}
