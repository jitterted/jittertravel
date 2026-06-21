package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects hotel events into the {@link HotelDetailsView} used by the edit screen. Single-purpose:
 * serves one view (the change-hotel form). Both {@link HotelBooked} and {@link HotelChanged} are
 * full snapshots, so each new event simply overwrites the entry keyed by {@link HotelBookingId}.
 * Mirrors {@link TrainDetailsViewProjector}.
 */
public class HotelDetailsViewProjector implements EventStreamConsumer {

    private final Map<HotelBookingId, HotelDetailsView> viewsById = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                case HotelBooked e -> viewsById.put(e.hotelBookingId(), toView(
                        e.hotelBookingId(), e.hotelName(), e.address(),
                        e.checkIn(), e.checkOut(), e.bookingIntent(), e.mapsUrl()));
                case HotelChanged e -> viewsById.put(e.hotelBookingId(), toView(
                        e.hotelBookingId(), e.hotelName(), e.address(),
                        e.checkIn(), e.checkOut(), e.bookingIntent(), e.mapsUrl()));
                default -> { /* not a hotel event */ }
            }
        });
    }

    private static HotelDetailsView toView(HotelBookingId hotelBookingId,
                                           String hotelName,
                                           Address address,
                                           LocalDateTime checkIn,
                                           LocalDateTime checkOut,
                                           BookingIntent bookingIntent,
                                           String mapsUrl) {
        return new HotelDetailsView(hotelBookingId, hotelName, address,
                checkIn, checkOut, bookingIntent, mapsUrl);
    }

    public Optional<HotelDetailsView> findById(HotelBookingId id) {
        return Optional.ofNullable(viewsById.get(id));
    }
}