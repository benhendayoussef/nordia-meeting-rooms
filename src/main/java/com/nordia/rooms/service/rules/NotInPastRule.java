package com.nordia.rooms.service.rules;

import java.time.Clock;

import com.nordia.rooms.exceptions.UnprocessableEntityException;
import com.nordia.rooms.model.NewBooking;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class NotInPastRule implements BookingRule {

    private final Clock clock;

    public NotInPastRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void check(NewBooking booking) {
        if (!booking.start().toInstant().isAfter(clock.instant())) {
            throw new UnprocessableEntityException("START_IN_PAST", "A booking cannot start in the past.");
        }
    }
}
