package com.nordia.rooms.service.rules;

import com.nordia.rooms.exceptions.UnprocessableEntityException;
import com.nordia.rooms.model.NewBooking;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class TimeRangeRule implements BookingRule {

    @Override
    public void check(NewBooking booking) {
        if (!booking.end().isAfter(booking.start())) {
            throw new UnprocessableEntityException("INVALID_TIME_RANGE", "A booking must end after it starts.");
        }
    }
}
