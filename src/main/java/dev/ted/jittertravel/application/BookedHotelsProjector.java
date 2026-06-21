package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelChanged;
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
            switch (storedEvent.payload()) {
                case HotelBooked e -> put(e.hotelBookingId(), e.hotelName(), e.address(),
                        e.checkIn(), e.checkOut(), e.mapsUrl());
                case HotelChanged e -> put(e.hotelBookingId(), e.hotelName(), e.address(),
                        e.checkIn(), e.checkOut(), e.mapsUrl());
                default -> { /* not a hotel event */ }
            }
        });
    }

    private void put(HotelBookingId hotelBookingId, String hotelName, Address address,
                     LocalDateTime checkIn, LocalDateTime checkOut, String rawMapsUrl) {
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
                mapsUrl
        ));
    }

    public List<BookedHotelView> views(TimeView timeView, LocalDateTime now) {
        return viewsById.values().stream()
                .filter(view -> timeView.includes(view, now))
                .sorted(Comparator.comparing(BookedHotelView::checkIn))
                .toList();
    }
}
