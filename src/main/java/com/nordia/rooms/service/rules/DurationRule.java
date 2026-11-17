package com.nordia.rooms.service.rules;

import java.time.Duration;

import com.nordia.rooms.config.BookingPolicy;
import com.nordia.rooms.exceptions.UnprocessableEntityException;
import com.nordia.rooms.model.NewBooking;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class DurationRule implements BookingRule {

    private final BookingPolicy policy;

    public DurationRule(BookingPolicy policy) {
        this.policy = policy;
    }

    @Override
    public void check(NewBooking booking) {
        Duration duration = Duration.between(booking.start(), booking.end());
        if (duration.compareTo(policy.minDuration()) < 0 || duration.compareTo(policy.maxDuration()) > 0) {
            throw new UnprocessableEntityException("INVALID_DURATION",
                    "A booking must last between %d and %d minutes, but this one lasts %d minutes."
                            .formatted(policy.minDuration().toMinutes(), policy.maxDuration().toMinutes(),
                                    duration.toMinutes()));
        }
    }
}
