package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ItineraryProjector implements EventStreamConsumer {

    private final Map<FlightId, FlightItineraryEntry> flightEntries = new ConcurrentHashMap<>();
    private final Map<TrainTripId, TrainItineraryEntry> trainEntries = new ConcurrentHashMap<>();
    private final Map<HotelBookingId, List<HotelItineraryEntry>> hotelEntries = new ConcurrentHashMap<>();
    private final Map<ConferenceId, List<ConferenceItineraryEntry>> conferenceEntries = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                case FlightBooked e -> flightEntries.put(e.flightId(), toEntry(e));
                case FlightChanged e -> flightEntries.put(e.flightId(), toEntry(e));
                case TrainBooked e -> trainEntries.put(e.tripId(), toEntry(e));
                case HotelBooked e -> hotelEntries.put(e.hotelBookingId(), toHotelEntries(e));
                case ConferenceTentativelyPlanned e -> conferenceEntries.put(e.conferenceId(), toConferenceEntries(e));
                default -> {}
            }
        });
    }

    public List<ItineraryEntry> entriesForDate(LocalDate date) {
        List<ItineraryEntry> result = new ArrayList<>();
        flightEntries.values().stream()
                .filter(e -> e.departureDateTime().toLocalDate().equals(date))
                .forEach(result::add);
        trainEntries.values().stream()
                .filter(e -> e.departureDateTime().toLocalDate().equals(date))
                .forEach(result::add);
        hotelEntries.values().stream()
                .flatMap(List::stream)
                .filter(e -> e.anchorDateTime().toLocalDate().equals(date))
                .forEach(result::add);
        conferenceEntries.values().stream()
                .flatMap(List::stream)
                .filter(e -> e.anchorDateTime().toLocalDate().equals(date))
                .forEach(result::add);
        result.sort(Comparator.comparing(ItineraryEntry::anchorTime));
        return Collections.unmodifiableList(result);
    }

    private static FlightItineraryEntry toEntry(FlightBooked e) {
        return new FlightItineraryEntry(
                e.airline(), e.flightNumber(),
                e.departureAirport().code(), e.departureDateTime(),
                e.arrivalAirport().code(), e.arrivalDateTime());
    }

    private static FlightItineraryEntry toEntry(FlightChanged e) {
        return new FlightItineraryEntry(
                e.airline(), e.flightNumber(),
                e.departureAirport().code(), e.departureDateTime(),
                e.arrivalAirport().code(), e.arrivalDateTime());
    }

    private static TrainItineraryEntry toEntry(TrainBooked e) {
        return new TrainItineraryEntry(
                e.serviceId(),
                e.departureStation().name(), e.departureStation().city(), e.departureStation().mapsUrl(),
                e.departureDateTime(),
                e.arrivalStation().name(), e.arrivalStation().city(), e.arrivalStation().mapsUrl(),
                e.arrivalDateTime());
    }

    private static List<HotelItineraryEntry> toHotelEntries(HotelBooked e) {
        return List.of(
                new HotelItineraryEntry(e.hotelName(), e.address(), e.bookingIntent(),
                        HotelDayRole.CHECK_IN, e.checkIn()),
                new HotelItineraryEntry(e.hotelName(), e.address(), e.bookingIntent(),
                        HotelDayRole.CHECK_OUT, e.checkOut()));
    }

    private static List<ConferenceItineraryEntry> toConferenceEntries(ConferenceTentativelyPlanned e) {
        LocalDateTime startDateTime = e.startDate();
        LocalDate start = startDateTime.toLocalDate();
        int totalDays = (int) ChronoUnit.DAYS.between(start, e.endDate().toLocalDate()) + 1;
        List<ConferenceItineraryEntry> entries = new ArrayList<>();
        for (int i = 0; i < totalDays; i++) {
            entries.add(new ConferenceItineraryEntry(
                    e.name(), e.venueName(), e.venueAddress(),
                    i + 1, totalDays, start.plusDays(i).atTime(startDateTime.toLocalTime())));
        }
        return entries;
    }
}
