package com.nordia.rooms.service.rules;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.nordia.rooms.exceptions.UnprocessableEntityException;
import com.nordia.rooms.model.NewBooking;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotInPastRuleTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 17, 9, 0, 0, 0, PARIS);

    private final NotInPastRule rule = new NotInPastRule(Clock.fixed(NOW.toInstant(), PARIS));

    @Test
    void accepts_a_start_one_minute_from_now() {
        assertThatCode(() -> rule.check(startingAt(NOW.plusMinutes(1)))).doesNotThrowAnyException();
    }

    @Test
    void refuses_a_start_at_the_current_minute() {
        assertThatThrownBy(() -> rule.check(startingAt(NOW)))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessage("A booking cannot start in the past.");
    }

    @Test
    void refuses_a_start_one_minute_ago() {
        assertThatThrownBy(() -> rule.check(startingAt(NOW.minusMinutes(1))))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    private NewBooking startingAt(ZonedDateTime start) {
        return new NewBooking(1, start, start.plusHours(1), "anna.martin@nordia.com", "Planning");
    }
}
