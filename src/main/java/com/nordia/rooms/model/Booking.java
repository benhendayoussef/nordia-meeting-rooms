package com.nordia.rooms.model;

import java.time.OffsetDateTime;

public record Booking(
        long id,
        int roomId,
        OffsetDateTime start,
        OffsetDateTime end,
        String organiser,
        String subject,
        BookingStatus status) {
}
