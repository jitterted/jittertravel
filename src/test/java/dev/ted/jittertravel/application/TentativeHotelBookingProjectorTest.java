package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingId;
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
                BookingIntent.TENTATIVE
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

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
