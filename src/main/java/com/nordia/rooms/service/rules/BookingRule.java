package com.nordia.rooms.service.rules;

import com.nordia.rooms.model.NewBooking;

public interface BookingRule {

    void check(NewBooking booking);
}
