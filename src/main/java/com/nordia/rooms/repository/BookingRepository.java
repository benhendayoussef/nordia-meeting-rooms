package com.nordia.rooms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
