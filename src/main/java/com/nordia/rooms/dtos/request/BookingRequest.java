package com.nordia.rooms.dtos.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookingRequest(
        @NotNull Integer roomId,
        @NotNull OffsetDateTime start,
        @NotNull OffsetDateTime end,
        @NotBlank @Size(max = 200) String subject) {
}
