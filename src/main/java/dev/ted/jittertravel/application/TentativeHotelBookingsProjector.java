package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

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
            if (storedEvent.payload() instanceof HotelBooked event) {
                viewsById.put(event.hotelBookingId(), new TentativeHotelBookingView(
                        event.hotelBookingId(),
                        event.hotelName(),
                        event.address().city(),
                        event.address().country(),
                        event.checkIn(),
                        event.checkOut(),
                        false
                ));
            }
        });
    }

    public List<TentativeHotelBookingView> views() {
        return viewsById.values().stream()
                .sorted(Comparator.comparing(TentativeHotelBookingView::checkIn))
                .toList();
    }
}
