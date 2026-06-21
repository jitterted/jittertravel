package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class HotelDetailsViewProjectorTest {

    private static final Address BERLIN =
            new Address("123 Unter den Linden", "Berlin", "", "10117", "Germany", "Berlin");
    private static final Address MUNICH =
            new Address("1 Marienplatz", "Munich", "", "80331", "Germany", "Munich");

    @Test
    void hotelBookedProducesDetailViewKeyedByBookingId() {
        HotelDetailsViewProjector projector = new HotelDetailsViewProjector();
        HotelBookingId id = HotelBookingId.random();
        HotelBooked event = new HotelBooked(
                id, "Grand Hotel", BERLIN,
                LocalDateTime.of(2026, 7, 1, 15, 0),
                LocalDateTime.of(2026, 7, 5, 11, 0),
                BookingIntent.TENTATIVE, "https://maps.example/grand");

        projector.handle(Stream.of(stored(event)));

        Optional<HotelDetailsView> view = projector.findById(id);
        assertThat(view)
                .isPresent();
        assertThat(view.get().hotelName())
                .isEqualTo("Grand Hotel");
        assertThat(view.get().address())
                .isEqualTo(BERLIN);
        assertThat(view.get().checkIn())
                .isEqualTo(LocalDateTime.of(2026, 7, 1, 15, 0));
        assertThat(view.get().checkOut())
                .isEqualTo(LocalDateTime.of(2026, 7, 5, 11, 0));
        assertThat(view.get().bookingIntent())
                .isEqualTo(BookingIntent.TENTATIVE);
        assertThat(view.get().mapsUrl())
                .isEqualTo("https://maps.example/grand");
    }

    @Test
    void hotelChangedOverwritesPreviousDetails() {
        HotelDetailsViewProjector projector = new HotelDetailsViewProjector();
        HotelBookingId id = HotelBookingId.random();
        HotelBooked booked = new HotelBooked(
                id, "Grand Hotel", BERLIN,
                LocalDateTime.of(2026, 7, 1, 15, 0),
                LocalDateTime.of(2026, 7, 5, 11, 0),
                BookingIntent.TENTATIVE, "");
        HotelChanged changed = new HotelChanged(
                id, "Bavaria Inn", MUNICH,
                LocalDateTime.of(2026, 8, 2, 16, 0),
                LocalDateTime.of(2026, 8, 6, 10, 0),
                BookingIntent.FINAL, "https://maps.example/bavaria");

        projector.handle(Stream.of(stored(booked), stored(changed)));

        HotelDetailsView view = projector.findById(id).orElseThrow();
        assertThat(view.hotelName())
                .isEqualTo("Bavaria Inn");
        assertThat(view.address())
                .isEqualTo(MUNICH);
        assertThat(view.checkIn())
                .isEqualTo(LocalDateTime.of(2026, 8, 2, 16, 0));
        assertThat(view.checkOut())
                .isEqualTo(LocalDateTime.of(2026, 8, 6, 10, 0));
        assertThat(view.bookingIntent())
                .isEqualTo(BookingIntent.FINAL);
        assertThat(view.mapsUrl())
                .isEqualTo("https://maps.example/bavaria");
    }

    @Test
    void findByIdReturnsEmptyWhenNoBooking() {
        HotelDetailsViewProjector projector = new HotelDetailsViewProjector();
        assertThat(projector.findById(HotelBookingId.random()))
                .isEmpty();
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}