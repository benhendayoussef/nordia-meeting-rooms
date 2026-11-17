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

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(header().string("Location", endsWith("/api/bookings/1")))
                .andExpect(jsonPath("$.id").value(1))
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

    private ResultActions createBooking(String start, String end) throws Exception {
        return postJson("""
                {"roomId":1,"start":"%s","end":"%s","organiser":"anna.martin@nordia.com","subject":"Planning"}
                """.formatted(start, end));
    }

    private ResultActions postJson(String json) throws Exception {
        return mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
