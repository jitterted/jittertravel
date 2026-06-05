package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.infrastructure.AddressRenderer;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class HotelCalendarProjector implements EventStreamConsumer {

    private final Map<HotelBookingId, CalendarEntry> entriesById = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            if (storedEvent.payload() instanceof HotelBooked event) {
                String location = event.address().city() + ", " + event.address().country();
                String mapsUrl = event.mapsUrl().isBlank()
                        ? AddressRenderer.mapsUrl(event.hotelName(), event.address())
                        : event.mapsUrl();
                List<String> locationLines = List.of(location);
                entriesById.put(event.hotelBookingId(), new CalendarEntry(
                        EntryKind.LODGING,
                        event.checkIn(),
                        event.checkOut(),
                        event.hotelName(),
                        locationLines,
                        event.hotelName() + " cont'd",
                        locationLines,
                        mapsUrl
                ));
            }
        });
    }

    public List<CalendarEntry> entries() {
        return entriesById.values().stream()
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
