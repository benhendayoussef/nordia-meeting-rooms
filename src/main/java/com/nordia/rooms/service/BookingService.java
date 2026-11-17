package com.nordia.rooms.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import com.nordia.rooms.config.BookingPolicy;
import com.nordia.rooms.dtos.request.BookingRequest;
import com.nordia.rooms.exceptions.BadRequestException;
import com.nordia.rooms.exceptions.ConflictException;
import com.nordia.rooms.exceptions.ForbiddenException;
import com.nordia.rooms.exceptions.ResourceNotFoundException;
import com.nordia.rooms.model.Booking;
import com.nordia.rooms.model.BookingStatus;
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

    // The connection starts IMMEDIATE transactions, so the checks and the insert
    // run under SQLite's write lock and two requests cannot book the same slot.
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
        requireNoOverlap(booking);
        requireDailyLimitNotReached(booking);

        return bookings.insert(booking, clock.instant());
    }

    public Booking getById(long id) {
        return bookings.findById(id).orElseThrow(() -> ResourceNotFoundException.bookingNotFound(id));
    }

    public List<Booking> findActiveForRoomOn(int roomId, LocalDate date) {
        roomService.getById(roomId);
        return bookings.findActiveByRoomAndDate(roomId, date);
    }

    // A single conditional UPDATE decides; the booking is only read afterwards to explain why nothing changed.
    @Transactional
    public void cancel(long id, String organiserEmail) {
        String organiser = organiserEmail.trim().toLowerCase(Locale.ROOT);
        if (bookings.cancel(id, organiser, clock.instant())) {
            return;
        }

        Booking booking = getById(id);
        if (!booking.organiser().equals(organiser)) {
            throw new ForbiddenException("NOT_ORGANISER", "Only the organiser can cancel this booking.");
        }
        if (booking.status() == BookingStatus.CANCELLED) {
            return;
        }
        throw new ConflictException("BOOKING_ALREADY_STARTED",
                "Booking %d has already started and can no longer be cancelled.".formatted(id));
    }

    private void requireWholeMinute(String field, OffsetDateTime time) {
        if (time.getSecond() != 0 || time.getNano() != 0) {
            throw new BadRequestException("INVALID_TIME",
                    "The " + field + " time must be on a whole minute, for example 2026-11-17T09:00:00+01:00.");
        }
    }

    private void requireNoOverlap(NewBooking booking) {
        bookings.findOverlapping(booking).stream().findFirst().ifPresent(existing -> {
            throw new ConflictException("BOOKING_OVERLAP",
                    "Room %d is already booked from %s to %s on %s.".formatted(
                            existing.roomId(),
                            existing.start().toLocalTime(),
                            existing.end().toLocalTime(),
                            existing.start().toLocalDate()));
        });
    }

    private void requireDailyLimitNotReached(NewBooking booking) {
        int limit = policy.maxActivePerOrganiserPerDay();
        int active = bookings.countActiveForOrganiserOn(booking.organiser(), booking.start().toLocalDate());
        if (active >= limit) {
            throw new ConflictException("DAILY_LIMIT_REACHED",
                    "%s already has %d bookings on %s, which is the daily maximum."
                            .formatted(booking.organiser(), active, booking.start().toLocalDate()));
        }
    }
}
