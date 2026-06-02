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
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TentativeHotelBookingsProjectorTest {

    private static final LocalDateTime CHECK_IN = LocalDateTime.of(2026, 6, 14, 15, 0);
    private static final LocalDateTime CHECK_OUT = LocalDateTime.of(2026, 6, 15, 11, 0);

    @Test
    void hotelBookedAddsEntryWithAllFieldsAndNoOverlap() {
        TentativeHotelBookingsProjector projector = new TentativeHotelBookingsProjector();
        HotelBooked event = sampleHotelBooked();

        projector.handle(Stream.of(stored(event)));

        List<TentativeHotelBookingView> views = projector.views();
        assertThat(views)
                .hasSize(1);
        TentativeHotelBookingView view = views.getFirst();
        assertThat(view.hotelBookingId())
                .isEqualTo(event.hotelBookingId());
        assertThat(view.hotelName())
                .isEqualTo("Grand Hotel");
        assertThat(view.city())
                .isEqualTo("Springfield");
        assertThat(view.country())
                .isEqualTo("US");
        assertThat(view.checkIn())
                .isEqualTo(CHECK_IN);
        assertThat(view.checkOut())
                .isEqualTo(CHECK_OUT);
        assertThat(view.hasOverlap())
                .as("Newly booked hotel must not show an overlap")
                .isFalse();
    }

    private static HotelBooked sampleHotelBooked() {
        return new HotelBooked(
                HotelBookingId.random(),
                "Grand Hotel",
                new Address("123 Main St", "Springfield", "IL", "62701", "US"),
                CHECK_IN,
                CHECK_OUT,
                BookingIntent.TENTATIVE
        );
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
