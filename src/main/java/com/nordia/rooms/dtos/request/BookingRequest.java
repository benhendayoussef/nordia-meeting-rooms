package com.nordia.rooms.dtos.request;

import java.time.OffsetDateTime;
import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookingRequest(
        @NotNull Integer roomId,
        @NotNull OffsetDateTime start,
        @NotNull OffsetDateTime end,
        @NotBlank @Email String organiser,
        @NotBlank @Size(max = 200) String subject) {

    public BookingRequest {
        if (organiser != null) {
            organiser = organiser.trim().toLowerCase(Locale.ROOT);
        }
    }
}
