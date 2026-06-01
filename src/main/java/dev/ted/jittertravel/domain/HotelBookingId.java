package dev.ted.jittertravel.domain;

import java.util.UUID;

public record HotelBookingId(UUID id) {
    public HotelBookingId {
        if (id == null) {
            throw new IllegalArgumentException("Hotel booking id is required");
        }
    }

    public static HotelBookingId random() {
        return new HotelBookingId(UUID.randomUUID());
    }

    public static HotelBookingId of(UUID id) {
        return new HotelBookingId(id);
    }
}
