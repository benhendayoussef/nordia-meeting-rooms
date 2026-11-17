package com.nordia.rooms.controller;

import java.net.URI;

import com.nordia.rooms.dtos.request.BookingRequest;
import com.nordia.rooms.dtos.response.BookingResponse;
import com.nordia.rooms.model.Booking;
import com.nordia.rooms.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        Booking booking = bookingService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(booking.id())
                .toUri();
        return ResponseEntity.created(location).body(BookingResponse.from(booking));
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable long id) {
        return BookingResponse.from(bookingService.getById(id));
    }
}
