package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
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

public class HotelCalendarProjector implements EventStreamConsumer {

    private final Map<HotelBookingId, CalendarEntry> entriesById = new ConcurrentHashMap<>();

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
        String location = address.city() + ", " + address.country();
        String mapsUrl = rawMapsUrl.isBlank()
                ? AddressRenderer.mapsUrl(hotelName, address)
                : rawMapsUrl;
        List<String> locationLines = List.of(location);
        entriesById.put(hotelBookingId, new CalendarEntry(
                EntryKind.LODGING,
                checkIn,
                checkOut,
                hotelName,
                locationLines,
                hotelName + " cont'd",
                locationLines,
                mapsUrl
        ));
    }

    public List<CalendarEntry> entries() {
        return entriesById.values().stream()
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
