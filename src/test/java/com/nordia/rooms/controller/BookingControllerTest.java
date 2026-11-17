package com.nordia.rooms.controller;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingControllerTest {

    @TestConfiguration
    static class FixedClockConfig {

        // Monday 2026-11-16, 07:00 in Paris
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-16T06:00:00Z"), ZoneId.of("Europe/Paris"));
        }
    }

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("test.db")
                + "?foreign_keys=on&transaction_mode=IMMEDIATE&busy_timeout=5000");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void startWithoutBookings() {
        jdbc.sql("DELETE FROM booking").update();
    }

    @Test
    void creates_a_booking_that_can_be_read_back() throws Exception {
        String location = createBooking("2026-11-17T09:00:00+01:00", "2026-11-17T10:00:00+01:00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/bookings/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.roomId").value(1))
                .andExpect(jsonPath("$.start").value("2026-11-17T09:00:00+01:00"))
                .andExpect(jsonPath("$.end").value("2026-11-17T10:00:00+01:00"))
                .andExpect(jsonPath("$.organiser").value("anna.martin@nordia.com"))
                .andExpect(jsonPath("$.subject").value("Planning"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value("2026-11-17T09:00:00+01:00"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void trims_and_lower_cases_the_organiser() throws Exception {
        postJson("""
                {"roomId":1,"start":"2026-11-17T09:00:00+01:00","end":"2026-11-17T10:00:00+01:00",
                 "organiser":"  Anna.Martin@Nordia.com ","subject":"Planning"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organiser").value("anna.martin@nordia.com"));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-17T08:00:00+01:00, 2026-11-17T08:15:00+01:00",
            "2026-11-17T18:45:00+01:00, 2026-11-17T19:00:00+01:00"
    })
    void accepts_bookings_at_the_edges_of_the_opening_hours(String start, String end) throws Exception {
        createBooking(start, end).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-17T07:45:00+01:00, 2026-11-17T08:15:00+01:00",
            "2026-11-17T18:30:00+01:00, 2026-11-17T19:30:00+01:00",
            "2026-11-21T10:00:00+01:00, 2026-11-21T11:00:00+01:00",
            "2026-11-22T10:00:00+01:00, 2026-11-22T11:00:00+01:00",
            "2026-11-17T23:00:00+01:00, 2026-11-18T00:30:00+01:00"
    })
    void refuses_bookings_outside_the_opening_hours(String start, String end) throws Exception {
        createBooking(start, end)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OUTSIDE_OPENING_HOURS"))
                .andExpect(jsonPath("$.detail").value(
                        "Bookings are only possible Monday to Friday between 08:00 and 19:00, "
                                + "and must end on the day they start."));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-17T09:00:00+01:00, 2026-11-17T09:15:00+01:00",
            "2026-11-17T09:00:00+01:00, 2026-11-17T13:00:00+01:00"
    })
    void accepts_the_shortest_and_the_longest_duration(String start, String end) throws Exception {
        createBooking(start, end).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-17T09:00:00+01:00, 2026-11-17T09:14:00+01:00",
            "2026-11-17T09:00:00+01:00, 2026-11-17T13:01:00+01:00"
    })
    void refuses_a_duration_outside_15_minutes_to_4_hours(String start, String end) throws Exception {
        createBooking(start, end)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_DURATION"));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-17T09:00:00+01:00, 2026-11-17T09:00:00+01:00",
            "2026-11-17T10:00:00+01:00, 2026-11-17T09:00:00+01:00"
    })
    void refuses_a_booking_that_does_not_end_after_it_starts(String start, String end) throws Exception {
        createBooking(start, end)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
    }

    @Test
    void converts_a_utc_time_to_office_time_in_winter() throws Exception {
        createBooking("2026-11-17T07:00:00Z", "2026-11-17T08:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.start").value("2026-11-17T08:00:00+01:00"))
                .andExpect(jsonPath("$.end").value("2026-11-17T09:00:00+01:00"));
    }

    @Test
    void refuses_a_utc_time_that_is_before_opening_in_office_time() throws Exception {
        createBooking("2026-11-17T06:30:00Z", "2026-11-17T07:30:00Z")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OUTSIDE_OPENING_HOURS"));
    }

    @Test
    void converts_a_utc_time_to_office_time_in_summer() throws Exception {
        createBooking("2027-06-15T06:00:00Z", "2027-06-15T07:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.start").value("2027-06-15T08:00:00+02:00"))
                .andExpect(jsonPath("$.end").value("2027-06-15T09:00:00+02:00"));
    }

    @Test
    void refuses_a_time_without_an_offset() throws Exception {
        createBooking("2026-11-17T09:00:00", "2026-11-17T10:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value(
                        "The value of 'start' is not valid. "
                                + "Use an ISO 8601 date-time with an offset, for example 2026-11-17T09:00:00+01:00."));
    }

    @Test
    void refuses_a_time_that_is_not_on_a_whole_minute() throws Exception {
        createBooking("2026-11-17T09:00:30+01:00", "2026-11-17T10:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME"));
    }

    @Test
    void refuses_a_start_in_the_past() throws Exception {
        createBooking("2026-11-13T09:00:00+01:00", "2026-11-13T10:00:00+01:00")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("START_IN_PAST"));
    }

    @Test
    void refuses_an_unknown_property_in_the_body() throws Exception {
        postJson("""
                {"roomId":1,"start":"2026-11-17T09:00:00+01:00","end":"2026-11-17T10:00:00+01:00",
                 "organiser":"anna.martin@nordia.com","subject":"Planning","floor":2}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Unknown property 'floor'."));
    }

    @Test
    void refuses_a_body_that_is_not_json() throws Exception {
        postJson("start=tomorrow")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void refuses_a_missing_subject_with_a_field_error() throws Exception {
        postJson("""
                {"roomId":1,"start":"2026-11-17T09:00:00+01:00","end":"2026-11-17T10:00:00+01:00",
                 "organiser":"anna.martin@nordia.com"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("subject")));
    }

    @Test
    void refuses_a_subject_longer_than_200_characters() throws Exception {
        postJson("""
                {"roomId":1,"start":"2026-11-17T09:00:00+01:00","end":"2026-11-17T10:00:00+01:00",
                 "organiser":"anna.martin@nordia.com","subject":"%s"}
                """.formatted("x".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("subject")));
    }

    @Test
    void refuses_an_invalid_email_with_a_field_error() throws Exception {
        postJson("""
                {"roomId":1,"start":"2026-11-17T09:00:00+01:00","end":"2026-11-17T10:00:00+01:00",
                 "organiser":"not-an-email","subject":"Planning"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("organiser")));
    }

    @Test
    void answers_404_when_the_room_does_not_exist() throws Exception {
        postJson("""
                {"roomId":99,"start":"2026-11-17T09:00:00+01:00","end":"2026-11-17T10:00:00+01:00",
                 "organiser":"anna.martin@nordia.com","subject":"Planning"}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Room 99 does not exist."));
    }

    @Test
    void answers_404_when_the_booking_does_not_exist() throws Exception {
        mockMvc.perform(get("/api/bookings/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Booking 42 does not exist."));
    }

    @ParameterizedTest
    @CsvSource({
            "partial left, 2026-11-17T08:30:00+01:00, 2026-11-17T09:30:00+01:00",
            "partial right, 2026-11-17T09:30:00+01:00, 2026-11-17T10:30:00+01:00",
            "inside, 2026-11-17T09:15:00+01:00, 2026-11-17T09:45:00+01:00",
            "around, 2026-11-17T08:00:00+01:00, 2026-11-17T11:00:00+01:00",
            "identical, 2026-11-17T09:00:00+01:00, 2026-11-17T10:00:00+01:00"
    })
    void refuses_a_booking_that_overlaps_an_existing_one(String shape, String start, String end) throws Exception {
        book(1, "bob.durand@nordia.com", "2026-11-17T09:00:00+01:00", "2026-11-17T10:00:00+01:00")
                .andExpect(status().isCreated());

        book(1, "anna.martin@nordia.com", start, end)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_OVERLAP"))
                .andExpect(jsonPath("$.detail").value("Room 1 is already booked from 09:00 to 10:00 on 2026-11-17."))
                .andExpect(content().string(not(containsString("bob.durand"))));
    }

    @Test
    void accepts_bookings_that_touch_in_the_same_room() throws Exception {
        book(1, "anna.martin@nordia.com", "2026-11-17T09:00:00+01:00", "2026-11-17T10:00:00+01:00")
                .andExpect(status().isCreated());
        book(1, "bob.durand@nordia.com", "2026-11-17T10:00:00+01:00", "2026-11-17T11:00:00+01:00")
                .andExpect(status().isCreated());
        book(1, "carla.petit@nordia.com", "2026-11-17T08:00:00+01:00", "2026-11-17T09:00:00+01:00")
                .andExpect(status().isCreated());
    }

    @Test
    void accepts_the_same_time_in_another_room() throws Exception {
        book(1, "anna.martin@nordia.com", "2026-11-17T09:00:00+01:00", "2026-11-17T10:00:00+01:00")
                .andExpect(status().isCreated());
        book(2, "bob.durand@nordia.com", "2026-11-17T09:00:00+01:00", "2026-11-17T10:00:00+01:00")
                .andExpect(status().isCreated());
    }

    @Test
    void ignores_cancelled_bookings_when_checking_for_overlap() throws Exception {
        insertCancelledBooking(1, "bob.durand@nordia.com", "2026-11-17T08:00:00Z", "2026-11-17T09:00:00Z");

        book(1, "anna.martin@nordia.com", "2026-11-17T09:00:00+01:00", "2026-11-17T10:00:00+01:00")
                .andExpect(status().isCreated());
    }

    @Test
    void refuses_a_fourth_booking_on_the_same_day() throws Exception {
        bookThreeTimesOn17th("anna.martin@nordia.com");

        book(1, "anna.martin@nordia.com", "2026-11-17T12:00:00+01:00", "2026-11-17T13:00:00+01:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_REACHED"))
                .andExpect(jsonPath("$.detail").value(
                        "anna.martin@nordia.com already has 3 bookings on 2026-11-17, which is the daily maximum."));
    }

    @Test
    void accepts_a_booking_for_the_same_organiser_on_another_day() throws Exception {
        bookThreeTimesOn17th("anna.martin@nordia.com");

        book(1, "anna.martin@nordia.com", "2026-11-18T09:00:00+01:00", "2026-11-18T10:00:00+01:00")
                .andExpect(status().isCreated());
    }

    @Test
    void counts_the_organiser_email_without_regard_to_case() throws Exception {
        bookThreeTimesOn17th("alice@nordia.com");

        book(1, "Alice@Nordia.com", "2026-11-17T12:00:00+01:00", "2026-11-17T13:00:00+01:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_REACHED"));
    }

    @Test
    void does_not_count_cancelled_bookings_towards_the_daily_limit() throws Exception {
        insertCancelledBooking(1, "anna.martin@nordia.com", "2026-11-17T12:00:00Z", "2026-11-17T13:00:00Z");
        bookThreeTimesOn17th("anna.martin@nordia.com");
    }

    @Test
    void reports_an_overlap_before_the_daily_limit() throws Exception {
        bookThreeTimesOn17th("anna.martin@nordia.com");

        book(1, "anna.martin@nordia.com", "2026-11-17T09:30:00+01:00", "2026-11-17T10:30:00+01:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_OVERLAP"));
    }

    private void bookThreeTimesOn17th(String organiser) throws Exception {
        for (int hour = 9; hour <= 11; hour++) {
            book(1, organiser, "2026-11-17T%02d:00:00+01:00".formatted(hour),
                    "2026-11-17T%02d:00:00+01:00".formatted(hour + 1))
                    .andExpect(status().isCreated());
        }
    }

    private void insertCancelledBooking(int roomId, String organiser, String startUtc, String endUtc) {
        jdbc.sql("""
                        INSERT INTO booking (room_id, start_at, end_at, booking_date, organiser, subject, status, created_at)
                        VALUES (:roomId, :startAt, :endAt, '2026-11-17', :organiser, 'Cancelled', 'CANCELLED',
                                '2026-11-16T06:00:00Z')
                        """)
                .param("roomId", roomId)
                .param("startAt", startUtc)
                .param("endAt", endUtc)
                .param("organiser", organiser)
                .update();
    }

    private ResultActions createBooking(String start, String end) throws Exception {
        return book(1, "anna.martin@nordia.com", start, end);
    }

    private ResultActions book(int roomId, String organiser, String start, String end) throws Exception {
        return postJson("""
                {"roomId":%d,"start":"%s","end":"%s","organiser":"%s","subject":"Planning"}
                """.formatted(roomId, start, end, organiser));
    }

    private ResultActions postJson(String json) throws Exception {
        return mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
