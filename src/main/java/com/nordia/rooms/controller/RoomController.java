package com.nordia.rooms.controller;

import java.time.LocalDate;
import java.util.List;

import com.nordia.rooms.dtos.response.BookingResponse;
import com.nordia.rooms.dtos.response.RoomResponse;
import com.nordia.rooms.service.BookingService;
import com.nordia.rooms.service.RoomService;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final BookingService bookingService;

    public RoomController(RoomService roomService, BookingService bookingService) {
        this.roomService = roomService;
        this.bookingService = bookingService;
    }

    @GetMapping
    public List<RoomResponse> list(@RequestParam(defaultValue = "1") @Positive int minCapacity) {
        return roomService.findByMinCapacity(minCapacity).stream()
                .map(RoomResponse::from)
                .toList();
    }

    @GetMapping("/{roomId}/bookings")
    public List<BookingResponse> bookingsOn(@PathVariable int roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookingService.findActiveForRoomOn(roomId, date).stream()
                .map(BookingResponse::from)
                .toList();
    }
}
