package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
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
                        e.checkIn().localDateTime(), e.checkOut().localDateTime(), e.mapsUrl());
                case HotelChanged e -> put(e.hotelBookingId(), e.hotelName(), e.address(),
                        e.checkIn().localDateTime(), e.checkOut().localDateTime(), e.mapsUrl());
                case HotelBookingCancelled e -> entriesById.remove(e.hotelBookingId());
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
        List<SubtitleLine> locationLines = List.of(new SubtitleLine.Text(location));
        // OWNER-only deep link to the booking's edit page; the renderer gates it on isOwner and
        // the redactor drops it, so it never reaches an anonymous viewer.
        String editPath = "/booked-hotels/" + hotelBookingId.id();
        entriesById.put(hotelBookingId, new CalendarEntry(
                EntryKind.LODGING,
                checkIn,
                checkOut,
                hotelName,
                locationLines,
                hotelName + " cont'd",
                locationLines,
                mapsUrl,
                editPath
        ));
    }

    public List<CalendarEntry> entries() {
        return entriesById.values().stream()
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
