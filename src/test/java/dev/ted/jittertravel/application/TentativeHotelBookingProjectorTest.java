package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TentativeHotelBookingProjectorTest {

    private static final LocalDateTime CHECK_IN = LocalDateTime.of(2026, 6, 14, 15, 0);
    private static final LocalDateTime CHECK_OUT = LocalDateTime.of(2026, 6, 15, 11, 0);

    @Test
    void hotelBookedCreatesEntryForThatBookingId() {
        TentativeHotelBookingProjector projector = new TentativeHotelBookingProjector();
        HotelBookingId bookingId = HotelBookingId.random();
        HotelBooked event = new HotelBooked(
                bookingId,
                "Grand Hotel",
                new Address("123 Main St", "Springfield", "IL", "62701", "US", null),
                CHECK_IN,
                CHECK_OUT,
                BookingIntent.TENTATIVE,
                null
        );

        projector.handle(Stream.of(stored(event)));

        TentativeHotelBookingView view = projector.findById(bookingId);
        assertThat(view).isNotNull();
        assertThat(view.hotelBookingId()).isEqualTo(bookingId);
        assertThat(view.hotelName()).isEqualTo("Grand Hotel");
        assertThat(view.city()).isEqualTo("Springfield");
        assertThat(view.country()).isEqualTo("US");
        assertThat(view.checkIn()).isEqualTo(CHECK_IN);
        assertThat(view.checkOut()).isEqualTo(CHECK_OUT);
    }

    @Test
    void hotelChangedOverwritesEntryUnderSameId() {
        TentativeHotelBookingProjector projector = new TentativeHotelBookingProjector();
        HotelBookingId bookingId = HotelBookingId.random();
        HotelBooked booked = new HotelBooked(bookingId, "Grand Hotel",
                new Address("123 Main St", "Springfield", "IL", "62701", "US", null),
                CHECK_IN, CHECK_OUT, BookingIntent.TENTATIVE, null);
        HotelChanged changed = new HotelChanged(bookingId, "Seaside Resort",
                new Address("1 Ocean Dr", "Miami", "FL", "33139", "US", null),
                CHECK_IN.plusDays(10), CHECK_OUT.plusDays(11), BookingIntent.FINAL, null);

        projector.handle(Stream.of(stored(booked), stored(changed)));

        TentativeHotelBookingView view = projector.findById(bookingId);
        assertThat(view)
                .isNotNull();
        assertThat(view.hotelName())
                .isEqualTo("Seaside Resort");
        assertThat(view.city())
                .isEqualTo("Miami");
        assertThat(view.checkIn())
                .isEqualTo(CHECK_IN.plusDays(10));
        assertThat(view.checkOut())
                .isEqualTo(CHECK_OUT.plusDays(11));
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
