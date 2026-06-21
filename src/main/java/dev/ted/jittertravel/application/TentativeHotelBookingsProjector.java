package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class TentativeHotelBookingsProjector implements EventStreamConsumer {

    private final Map<HotelBookingId, TentativeHotelBookingView> viewsById = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case HotelBooked e -> put(e.hotelBookingId(), e.hotelName(), e.address(),
                        e.checkIn(), e.checkOut());
                case HotelChanged e -> put(e.hotelBookingId(), e.hotelName(), e.address(),
                        e.checkIn(), e.checkOut());
                default -> { /* not a hotel event */ }
            }
        });
    }

    private void put(HotelBookingId hotelBookingId, String hotelName, Address address,
                     LocalDateTime checkIn, LocalDateTime checkOut) {
        viewsById.put(hotelBookingId, new TentativeHotelBookingView(
                hotelBookingId,
                hotelName,
                address.city(),
                address.country(),
                checkIn,
                checkOut,
                false
        ));
    }

    public List<TentativeHotelBookingView> views() {
        return viewsById.values().stream()
                .sorted(Comparator.comparing(TentativeHotelBookingView::checkIn))
                .toList();
    }
}
