package com.nordia.rooms.service;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.nordia.rooms.dtos.request.BookingRequest;
import com.nordia.rooms.exceptions.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingServiceTest {

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
    BookingService bookingService;

    @Autowired
    JdbcClient jdbc;

    @Test
    void books_a_slot_only_once_when_ten_requests_arrive_at_the_same_moment() throws Exception {
        int requests = 10;
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests);

        List<Future<Throwable>> outcomes = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            BookingRequest request = new BookingRequest(1,
                    OffsetDateTime.parse("2026-11-17T09:00:00+01:00"),
                    OffsetDateTime.parse("2026-11-17T10:00:00+01:00"),
                    "Planning");
            String organiser = "person" + i + "@nordia.com";
            outcomes.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    bookingService.create(request, organiser);
                    return null;
                } catch (Throwable e) {
                    return e;
                }
            }));
        }
        ready.await();
        go.countDown();

        List<Throwable> failures = new ArrayList<>();
        int succeeded = 0;
        for (Future<Throwable> outcome : outcomes) {
            Throwable failure = outcome.get(30, TimeUnit.SECONDS);
            if (failure == null) {
                succeeded++;
            } else {
                failures.add(failure);
            }
        }
        executor.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(failures).hasSize(requests - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(ConflictException.class,
                                conflict -> assertThat(conflict.getCode()).isEqualTo("BOOKING_OVERLAP")));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM booking WHERE status = 'ACTIVE'").query(Integer.class).single())
                .isEqualTo(1);
    }
}
