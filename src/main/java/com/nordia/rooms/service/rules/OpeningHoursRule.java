package com.nordia.rooms.service.rules;

import java.time.DayOfWeek;

import com.nordia.rooms.config.BookingPolicy;
import com.nordia.rooms.exceptions.UnprocessableEntityException;
import com.nordia.rooms.model.NewBooking;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class OpeningHoursRule implements BookingRule {

    private final BookingPolicy policy;

    public OpeningHoursRule(BookingPolicy policy) {
        this.policy = policy;
    }

    @Override
    public void check(NewBooking booking) {
        boolean weekday = booking.start().getDayOfWeek() != DayOfWeek.SATURDAY
                && booking.start().getDayOfWeek() != DayOfWeek.SUNDAY;
        boolean sameDay = booking.start().toLocalDate().equals(booking.end().toLocalDate());
        boolean withinHours = !booking.start().toLocalTime().isBefore(policy.openingStart())
                && !booking.end().toLocalTime().isAfter(policy.openingEnd());

        if (!weekday || !sameDay || !withinHours) {
            throw new UnprocessableEntityException("OUTSIDE_OPENING_HOURS",
                    "Bookings are only possible Monday to Friday between %s and %s, and must end on the day they start."
                            .formatted(policy.openingStart(), policy.openingEnd()));
        }
    }
}
