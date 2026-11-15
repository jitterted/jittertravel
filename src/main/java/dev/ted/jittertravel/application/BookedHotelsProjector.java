package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.domain.Place;
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
    private final CityLabel cityLabel = new CityLabel();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case HotelBooked e -> put(e.hotelBookingId(), e.hotelName(), e.address(),
                        e.checkIn(), e.checkOut(), e.bookingIntent(), e.mapsUrl(), e.cancelBy());
                case HotelChanged e -> put(e.hotelBookingId(), e.hotelName(), e.address(),
                        e.checkIn(), e.checkOut(), e.bookingIntent(), e.mapsUrl(), e.cancelBy());
                // Alone among the hotel read models, this one keeps a tombstone instead of
                // removing: /booked-hotels is where you go to see that the cancellation landed.
                case HotelBookingCancelled e -> viewsById.computeIfPresent(e.hotelBookingId(),
                        (id, view) -> view.cancelledWith(e.reason()));
                default -> { /* not a hotel event */ }
            }
        });
    }

    private void put(HotelBookingId hotelBookingId, String hotelName, Address address,
                     ZonedTimestamp checkIn, ZonedTimestamp checkOut, BookingIntent bookingIntent,
                     String rawMapsUrl, ZonedTimestamp cancelBy) {
        String mapsUrl = rawMapsUrl.isBlank()
                ? AddressRenderer.mapsUrl(hotelName, address)
                : rawMapsUrl;
        viewsById.put(hotelBookingId, new BookedHotelView(
                hotelBookingId,
                hotelName,
                address.city(),
                // The schedule's place for this stay, which the ground-transfer form matches a gap
                // against — derived by the same rule ScheduleGapProjector uses, not alongside it.
                Place.of(address).value(),
                cityLabel.qualifier(address),
                checkIn,
                checkOut,
                bookingIntent,
                mapsUrl,
                cancelBy,
                false,  // resolved against `now` in views(...), which is where the clock arrives
                false,
                ""
        ));
    }

    /** Every stay the time filter admits, canceled ones included and marked. */
    public List<BookedHotelView> views(TimeView timeView, Instant now) {
        return views(timeView, CancelledView.SHOW, now);
    }

    public List<BookedHotelView> views(TimeView timeView, CancelledView cancelledView, Instant now) {
        return viewsById.values().stream()
                .filter(view -> timeView.includes(view, now))
                .filter(cancelledView::includes)
                .map(view -> view.withDeadlineEvaluatedAt(now))
                .sorted(Comparator.comparing((BookedHotelView view) -> view.checkIn().utc()))
                .toList();
    }

    /**
     * How many canceled stays the time filter admits — what the list's switch counts, needed even
     * while they are hidden, since the switch reports what the page is leaving out.
     */
    public int cancelledCount(TimeView timeView, Instant now) {
        return (int) viewsById.values().stream()
                .filter(view -> timeView.includes(view, now))
                .filter(BookedHotelView::cancelled)
                .count();
    }
}
