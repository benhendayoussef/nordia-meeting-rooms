package com.nordia.rooms.model;

import java.time.ZonedDateTime;

public record NewBooking(
        int roomId,
        ZonedDateTime start,
        ZonedDateTime end,
        String organiser,
        String subject) {
}
