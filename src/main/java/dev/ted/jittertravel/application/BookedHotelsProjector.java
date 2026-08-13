package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.AddressRenderer;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class BookedHotelsProjector implements EventStreamConsumer {

    private final Map<HotelBookingId, BookedHotelView> viewsById = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case HotelBooked e -> put(e.hotelBookingId(), e.hotelName(), e.address(),
                        e.checkIn(), e.checkOut(), e.mapsUrl(), e.cancelBy());
                case HotelChanged e -> put(e.hotelBookingId(), e.hotelName(), e.address(),
                        e.checkIn(), e.checkOut(), e.mapsUrl(), e.cancelBy());
                // Alone among the hotel read models, this one keeps a tombstone instead of
                // removing: /booked-hotels is where you go to see that the cancellation landed.
                case HotelBookingCancelled e -> viewsById.computeIfPresent(e.hotelBookingId(),
                        (id, view) -> view.cancelledWith(e.reason()));
                default -> { /* not a hotel event */ }
            }
        });
    }

    private void put(HotelBookingId hotelBookingId, String hotelName, Address address,
                     ZonedTimestamp checkIn, ZonedTimestamp checkOut, String rawMapsUrl,
                     ZonedTimestamp cancelBy) {
        String mapsUrl = rawMapsUrl.isBlank()
                ? AddressRenderer.mapsUrl(hotelName, address)
                : rawMapsUrl;
        viewsById.put(hotelBookingId, new BookedHotelView(
                hotelBookingId,
                hotelName,
                address.city(),
                address.country(),
                checkIn,
                checkOut,
                BookingIntent.TENTATIVE,
                mapsUrl,
                cancelBy,
                false,  // resolved against `now` in views(...), which is where the clock arrives
                false,
                ""
        ));
    }

    public List<BookedHotelView> views(TimeView timeView, Instant now) {
        return viewsById.values().stream()
                .filter(view -> timeView.includes(view, now))
                .map(view -> view.withDeadlineEvaluatedAt(now))
                .sorted(Comparator.comparing((BookedHotelView view) -> view.checkIn().utc()))
                .toList();
    }
}
