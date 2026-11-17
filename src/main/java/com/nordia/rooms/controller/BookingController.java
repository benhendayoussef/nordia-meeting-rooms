package com.nordia.rooms.controller;

import java.net.URI;

import com.nordia.rooms.dtos.request.BookingRequest;
import com.nordia.rooms.dtos.response.BookingResponse;
import com.nordia.rooms.model.Booking;
import com.nordia.rooms.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @Operation(summary = "Book a room")
    @ApiResponse(responseCode = "201", description = "Booking created; its URL is in the Location header")
    @ApiResponse(responseCode = "400",
            description = "Malformed or invalid body: MALFORMED_REQUEST, VALIDATION_ERROR or INVALID_TIME")
    @ApiResponse(responseCode = "404", description = "The room does not exist: ROOM_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "BOOKING_OVERLAP or DAILY_LIMIT_REACHED")
    @ApiResponse(responseCode = "422",
            description = "INVALID_TIME_RANGE, INVALID_DURATION, OUTSIDE_OPENING_HOURS or START_IN_PAST")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        Booking booking = bookingService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(booking.id())
                .toUri();
        return ResponseEntity.created(location).body(BookingResponse.from(booking));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a booking")
    @ApiResponse(responseCode = "200", description = "The booking, with times in office time")
    @ApiResponse(responseCode = "404", description = "BOOKING_NOT_FOUND")
    public BookingResponse get(@PathVariable long id) {
        return BookingResponse.from(bookingService.getById(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel a booking",
            description = "Only the organiser can cancel, and only before the booking starts. "
                    + "Cancelling a booking that is already cancelled also answers 204.")
    @ApiResponse(responseCode = "204", description = "The booking is cancelled")
    @ApiResponse(responseCode = "400", description = "The X-Organiser-Email header is missing or blank")
    @ApiResponse(responseCode = "403", description = "The email is not the organiser's: NOT_ORGANISER")
    @ApiResponse(responseCode = "404", description = "BOOKING_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "The booking has already started: BOOKING_ALREADY_STARTED")
    public void cancel(@PathVariable long id, @RequestHeader("X-Organiser-Email") @NotBlank String organiser) {
        bookingService.cancel(id, organiser);
    }
}
