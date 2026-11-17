package com.nordia.rooms.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import com.nordia.rooms.config.BookingPolicy;
import com.nordia.rooms.dtos.request.BookingRequest;
import com.nordia.rooms.exceptions.BadRequestException;
import com.nordia.rooms.exceptions.ResourceNotFoundException;
import com.nordia.rooms.model.Booking;
import com.nordia.rooms.model.NewBooking;
import com.nordia.rooms.repository.BookingRepository;
import com.nordia.rooms.service.rules.BookingRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final RoomService roomService;
    private final BookingRepository bookings;
    private final List<BookingRule> rules;
    private final BookingPolicy policy;
    private final Clock clock;

    public BookingService(RoomService roomService, BookingRepository bookings, List<BookingRule> rules,
                          BookingPolicy policy, Clock clock) {
        this.roomService = roomService;
        this.bookings = bookings;
        this.rules = rules;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public Booking create(BookingRequest request) {
        requireWholeMinute("start", request.start());
        requireWholeMinute("end", request.end());
        roomService.getById(request.roomId());

        NewBooking booking = new NewBooking(
                request.roomId(),
                request.start().atZoneSameInstant(policy.zone()),
                request.end().atZoneSameInstant(policy.zone()),
                request.organiser(),
                request.subject());
        rules.forEach(rule -> rule.check(booking));

        return bookings.insert(booking, clock.instant());
    }

    public Booking getById(long id) {
        return bookings.findById(id).orElseThrow(() -> ResourceNotFoundException.bookingNotFound(id));
    }

    private void requireWholeMinute(String field, OffsetDateTime time) {
        if (time.getSecond() != 0 || time.getNano() != 0) {
            throw new BadRequestException("INVALID_TIME",
                    "The " + field + " time must be on a whole minute, for example 2026-11-17T09:00:00+01:00.");
        }
    }
}
