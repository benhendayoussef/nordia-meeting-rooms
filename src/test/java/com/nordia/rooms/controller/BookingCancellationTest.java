package com.nordia.rooms.controller;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingCancellationTest {

    private static final String ORGANISER = "anna.martin@nordia.com";
    private static final Instant MONDAY_7AM_PARIS = Instant.parse("2026-11-16T06:00:00Z");

    static class SettableClock extends Clock {

        private final ZoneId zone = ZoneId.of("Europe/Paris");
        private Instant instant = MONDAY_7AM_PARIS;

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class SettableClockConfig {

        @Bean
        @Primary
        SettableClock settableClock() {
            return new SettableClock();
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

    @Autowired
    SettableClock clock;

    @BeforeEach
    void startMondayMorningWithoutBookings() {
        clock.set(MONDAY_7AM_PARIS);
        jdbc.sql("DELETE FROM booking").update();
    }

    @Test
    void lets_the_organiser_cancel_a_future_booking_and_frees_the_slot() throws Exception {
        long id = bookOn17th(ORGANISER, 9, 10);

        cancel(id, ORGANISER).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/bookings/" + id))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        book(ORGANISER, 9, 10).andExpect(status().isCreated());
    }

    @Test
    void treats_the_organiser_email_without_regard_to_case_and_spaces() throws Exception {
        long id = bookOn17th(ORGANISER, 9, 10);

        cancel(id, " Anna.Martin@Nordia.com ").andExpect(status().isNoContent());
    }

    @Test
    void answers_204_when_the_booking_is_already_cancelled() throws Exception {
        long id = bookOn17th(ORGANISER, 9, 10);
        cancel(id, ORGANISER).andExpect(status().isNoContent());

        cancel(id, ORGANISER).andExpect(status().isNoContent());
    }

    @Test
    void cancels_a_booking_one_minute_before_it_starts() throws Exception {
        long id = bookOn17th(ORGANISER, 9, 10);
        clock.set(Instant.parse("2026-11-17T07:59:00Z"));

        cancel(id, ORGANISER).andExpect(status().isNoContent());
    }

    @Test
    void refuses_to_cancel_at_the_exact_start_minute_and_keeps_the_booking_active() throws Exception {
        long id = bookOn17th(ORGANISER, 9, 10);
        clock.set(Instant.parse("2026-11-17T08:00:00Z"));

        cancel(id, ORGANISER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_ALREADY_STARTED"));

        mockMvc.perform(get("/api/bookings/" + id))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void refuses_to_cancel_a_booking_that_is_already_over() throws Exception {
        long id = bookOn17th(ORGANISER, 9, 10);
        clock.set(Instant.parse("2026-11-17T12:00:00Z"));

        cancel(id, ORGANISER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_ALREADY_STARTED"));
    }

    @Test
    void refuses_another_email_with_403_and_keeps_the_booking_active() throws Exception {
        long id = bookOn17th(ORGANISER, 9, 10);

        cancel(id, "bob.durand@nordia.com")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ORGANISER"));

        mockMvc.perform(get("/api/bookings/" + id))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void refuses_a_request_without_the_organiser_header() throws Exception {
        long id = bookOn17th(ORGANISER, 9, 10);

        mockMvc.perform(delete("/api/bookings/" + id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void refuses_a_blank_organiser_header() throws Exception {
        long id = bookOn17th(ORGANISER, 9, 10);

        cancel(id, "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void answers_404_when_the_booking_does_not_exist() throws Exception {
        cancel(42, ORGANISER)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void lets_the_organiser_book_again_after_cancelling_one_of_three_bookings() throws Exception {
        long first = bookOn17th(ORGANISER, 9, 10);
        bookOn17th(ORGANISER, 10, 11);
        bookOn17th(ORGANISER, 11, 12);
        book(ORGANISER, 12, 13)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_REACHED"));

        cancel(first, ORGANISER).andExpect(status().isNoContent());

        book(ORGANISER, 12, 13).andExpect(status().isCreated());
    }

    private long bookOn17th(String organiser, int startHour, int endHour) throws Exception {
        String body = book(organiser, startHour, endHour)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.id", Long.class);
    }

    private ResultActions book(String organiser, int startHour, int endHour) throws Exception {
        return mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("""
                {"roomId":1,"start":"2026-11-17T%02d:00:00+01:00","end":"2026-11-17T%02d:00:00+01:00",
                 "organiser":"%s","subject":"Planning"}
                """.formatted(startHour, endHour, organiser)));
    }

    private ResultActions cancel(long id, String organiser) throws Exception {
        return mockMvc.perform(delete("/api/bookings/" + id).header("X-Organiser-Email", organiser));
    }
}
