package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.infrastructure.AddressRenderer;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.LocalDateTime;
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
            if (storedEvent.payload() instanceof HotelBooked event) {
                String mapsUrl = event.mapsUrl().isBlank()
                        ? AddressRenderer.mapsUrl(event.hotelName(), event.address())
                        : event.mapsUrl();
                viewsById.put(event.hotelBookingId(), new BookedHotelView(
                        event.hotelBookingId(),
                        event.hotelName(),
                        event.address().city(),
                        event.address().country(),
                        event.checkIn(),
                        event.checkOut(),
                        BookingIntent.TENTATIVE,
                        mapsUrl
                ));
            }
        });
    }

    public List<BookedHotelView> views(TimeView timeView, LocalDateTime now) {
        return viewsById.values().stream()
                .filter(view -> timeView.includes(view, now))
                .sorted(Comparator.comparing(BookedHotelView::checkIn))
                .toList();
    }
}
