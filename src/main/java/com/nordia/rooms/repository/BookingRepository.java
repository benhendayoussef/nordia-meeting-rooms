package com.nordia.rooms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import com.nordia.rooms.config.BookingPolicy;
import com.nordia.rooms.model.Booking;
import com.nordia.rooms.model.BookingStatus;
import com.nordia.rooms.model.NewBooking;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {

    // Fixed width so that comparing the stored text compares the instants.
    private static final DateTimeFormatter UTC_TEXT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final JdbcClient jdbc;
    private final BookingPolicy policy;

    public BookingRepository(JdbcClient jdbc, BookingPolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    public Booking insert(NewBooking booking, Instant createdAt) {
        long id = jdbc.sql("""
                        INSERT INTO booking (room_id, start_at, end_at, booking_date, organiser, subject, status, created_at)
                        VALUES (:roomId, :startAt, :endAt, :bookingDate, :organiser, :subject, :status, :createdAt)
                        RETURNING id
                        """)
                .param("roomId", booking.roomId())
                .param("startAt", UTC_TEXT.format(booking.start()))
                .param("endAt", UTC_TEXT.format(booking.end()))
                .param("bookingDate", booking.start().toLocalDate().toString())
                .param("organiser", booking.organiser())
                .param("subject", booking.subject())
                .param("status", BookingStatus.ACTIVE.name())
                .param("createdAt", UTC_TEXT.format(createdAt))
                .query(Long.class)
                .single();
        return findById(id).orElseThrow();
    }

    // Returns false when nothing matched: unknown id, other organiser, already cancelled or already started.
    public boolean cancel(long id, String organiser, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE booking
                        SET status = :cancelled, cancelled_at = :now
                        WHERE id = :id
                          AND organiser = :organiser
                          AND status = :active
                          AND start_at > :now
                        """)
                .param("cancelled", BookingStatus.CANCELLED.name())
                .param("active", BookingStatus.ACTIVE.name())
                .param("now", UTC_TEXT.format(now))
                .param("id", id)
                .param("organiser", organiser)
                .update();
        return updated == 1;
    }

    public Optional<Booking> findById(long id) {
        return jdbc.sql("""
                        SELECT id, room_id, start_at, end_at, organiser, subject, status
                        FROM booking
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(this::mapBooking)
                .optional();
    }

    // Strict comparisons: a booking that ends exactly when another starts does not overlap it.
    // A booking never spans two days, so the date narrows the search to the index.
    public List<Booking> findOverlapping(NewBooking booking) {
        return jdbc.sql("""
                        SELECT id, room_id, start_at, end_at, organiser, subject, status
                        FROM booking
                        WHERE room_id = :roomId
                          AND booking_date = :bookingDate
                          AND status = :status
                          AND start_at < :endAt
                          AND end_at > :startAt
                        ORDER BY start_at
                        """)
                .param("roomId", booking.roomId())
                .param("bookingDate", booking.start().toLocalDate().toString())
                .param("status", BookingStatus.ACTIVE.name())
                .param("startAt", UTC_TEXT.format(booking.start()))
                .param("endAt", UTC_TEXT.format(booking.end()))
                .query(this::mapBooking)
                .list();
    }

    public List<Booking> findActiveByRoomAndDate(int roomId, LocalDate date) {
        return jdbc.sql("""
                        SELECT id, room_id, start_at, end_at, organiser, subject, status
                        FROM booking
                        WHERE room_id = :roomId
                          AND booking_date = :bookingDate
                          AND status = :status
                        ORDER BY start_at
                        """)
                .param("roomId", roomId)
                .param("bookingDate", date.toString())
                .param("status", BookingStatus.ACTIVE.name())
                .query(this::mapBooking)
                .list();
    }

    public int countActiveForOrganiserOn(String organiser, LocalDate date) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                        FROM booking
                        WHERE organiser = :organiser
                          AND booking_date = :bookingDate
                          AND status = :status
                        """)
                .param("organiser", organiser)
                .param("bookingDate", date.toString())
                .param("status", BookingStatus.ACTIVE.name())
                .query(Integer.class)
                .single();
    }

    private Booking mapBooking(ResultSet rs, int rowNum) throws SQLException {
        return new Booking(
                rs.getLong("id"),
                rs.getInt("room_id"),
                Instant.parse(rs.getString("start_at")).atZone(policy.zone()).toOffsetDateTime(),
                Instant.parse(rs.getString("end_at")).atZone(policy.zone()).toOffsetDateTime(),
                rs.getString("organiser"),
                rs.getString("subject"),
                BookingStatus.valueOf(rs.getString("status")));
    }
}
