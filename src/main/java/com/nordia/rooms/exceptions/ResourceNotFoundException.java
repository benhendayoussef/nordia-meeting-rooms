package com.nordia.rooms.exceptions;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BaseServiceException {
    public ResourceNotFoundException(String code, String message) {
        super(code, message, HttpStatus.NOT_FOUND);
    }

    public static ResourceNotFoundException roomNotFound(int roomId) {
        return new ResourceNotFoundException("ROOM_NOT_FOUND", "Room " + roomId + " does not exist.");
    }

    public static ResourceNotFoundException bookingNotFound(long bookingId) {
        return new ResourceNotFoundException("BOOKING_NOT_FOUND", "Booking " + bookingId + " does not exist.");
    }
}
