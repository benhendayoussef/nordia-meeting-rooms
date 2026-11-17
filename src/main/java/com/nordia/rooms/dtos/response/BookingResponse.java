package com.nordia.rooms.dtos.response;

import java.time.OffsetDateTime;

import com.nordia.rooms.model.Booking;
import com.nordia.rooms.model.BookingStatus;

public record BookingResponse(
        long id,
        int roomId,
        OffsetDateTime start,
        OffsetDateTime end,
        String organiser,
        String subject,
        BookingStatus status) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.id(), booking.roomId(), booking.start(), booking.end(),
                booking.organiser(), booking.subject(), booking.status());
    }
}
