package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.AddressRenderer;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ItineraryProjector implements EventStreamConsumer {

    private final Map<FlightId, List<FlightItineraryEntry>> flightEntries = new ConcurrentHashMap<>();
    private final Map<TrainTripId, List<TrainItineraryEntry>> trainEntries = new ConcurrentHashMap<>();
    private final Map<HotelBookingId, List<HotelItineraryEntry>> hotelEntries = new ConcurrentHashMap<>();
    private final Map<ConferenceId, List<ConferenceItineraryEntry>> conferenceEntries = new ConcurrentHashMap<>();
    private final Map<GatheringId, GatheringItineraryEntry> gatheringEntries = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                case FlightBooked e -> flightEntries.put(e.flightId(), toFlightEntries(e));
                case FlightChanged e -> flightEntries.put(e.flightId(), toFlightEntries(e));
                case TrainBooked e -> trainEntries.put(e.tripId(), toTrainEntries(e));
                case TrainChanged e -> trainEntries.put(e.tripId(), toTrainEntries(e));
                case HotelBooked e -> hotelEntries.put(e.hotelBookingId(), toHotelEntries(e));
                case HotelChanged e -> hotelEntries.put(e.hotelBookingId(), toHotelEntries(e));
                case ConferenceTentativelyPlanned e -> conferenceEntries.put(e.conferenceId(), toConferenceEntries(e));
                case ConferenceCancelled(ConferenceId conferenceId, String _) -> conferenceEntries.remove(conferenceId);
                case GatheringPlanned e -> gatheringEntries.put(e.gatheringId(), toGatheringEntry(e));
                default -> {}
            }
        });
    }

    public LocalDate firstDateOnOrAfter(LocalDate date) {
        return Stream.of(
                        flightEntries.values().stream().flatMap(List::stream),
                        trainEntries.values().stream().flatMap(List::stream),
                        hotelEntries.values().stream().flatMap(List::stream),
                        conferenceEntries.values().stream().flatMap(List::stream),
                        gatheringEntries.values().stream()
                )
                .flatMap(s -> s)
                .map(e -> e.anchorTime().toLocalDate())
                .filter(d -> !d.isBefore(date))
                .min(Comparator.naturalOrder())
                .orElse(date);
    }

    public List<ItineraryEntry> entriesForDate(LocalDate date) {
        List<ItineraryEntry> result = new ArrayList<>();
        flightEntries.values().stream()
                .flatMap(List::stream)
                .filter(e -> e.anchorTime().toLocalDate().equals(date))
                .forEach(result::add);
        trainEntries.values().stream()
                .flatMap(List::stream)
                .filter(e -> e.anchorTime().toLocalDate().equals(date))
                .forEach(result::add);
        hotelEntries.values().stream()
                .flatMap(List::stream)
                .filter(e -> e.anchorDateTime().toLocalDate().equals(date))
                .forEach(result::add);
        conferenceEntries.values().stream()
                .flatMap(List::stream)
                .filter(e -> e.anchorDateTime().toLocalDate().equals(date))
                .forEach(result::add);
        gatheringEntries.values().stream()
                .filter(e -> e.anchorDateTime().toLocalDate().equals(date))
                .forEach(result::add);
        result.sort(Comparator.comparing(ItineraryEntry::anchorTime));
        return Collections.unmodifiableList(result);
    }

    private static List<FlightItineraryEntry> toFlightEntries(FlightBooked e) {
        return toFlightEntries(e.flightId(), e.airline(), e.flightNumber(),
                e.departureAirport().code(), e.departureDateTime(),
                e.arrivalAirport().code(), e.arrivalDateTime());
    }

    private static List<FlightItineraryEntry> toFlightEntries(FlightChanged e) {
        return toFlightEntries(e.flightId(), e.airline(), e.flightNumber(),
                e.departureAirport().code(), e.departureDateTime(),
                e.arrivalAirport().code(), e.arrivalDateTime());
    }

    private static List<FlightItineraryEntry> toFlightEntries(
            FlightId flightId,
            String airline, String flightNumber,
            String depCode, LocalDateTime depDt,
            String arrCode, LocalDateTime arrDt) {
        FlightItineraryEntry departure = new FlightItineraryEntry(
                flightId, FlightDayRole.DEPARTURE, airline, flightNumber, depCode, depDt, arrCode, arrDt);
        if (depDt.toLocalDate().equals(arrDt.toLocalDate())) {
            return List.of(departure);
        }
        return List.of(departure, new FlightItineraryEntry(
                flightId, FlightDayRole.ARRIVAL, airline, flightNumber, depCode, depDt, arrCode, arrDt));
    }

    private static List<TrainItineraryEntry> toTrainEntries(TrainBooked e) {
        return toTrainEntries(e.tripId(), e.serviceId(), e.departureStation(), e.departureDateTime(),
                e.arrivalStation(), e.arrivalDateTime());
    }

    private static List<TrainItineraryEntry> toTrainEntries(TrainChanged e) {
        return toTrainEntries(e.tripId(), e.serviceId(), e.departureStation(), e.departureDateTime(),
                e.arrivalStation(), e.arrivalDateTime());
    }

    private static List<TrainItineraryEntry> toTrainEntries(
            TrainTripId tripId,
            String serviceId,
            TrainStationAddress departureStation, LocalDateTime departureDateTime,
            TrainStationAddress arrivalStation, LocalDateTime arrivalDateTime) {
        TrainItineraryEntry departure = new TrainItineraryEntry(
                tripId, TrainDayRole.DEPARTURE, serviceId,
                departureStation.name(), departureStation.city(), departureStation.mapsUrl(),
                departureDateTime,
                arrivalStation.name(), arrivalStation.city(), arrivalStation.mapsUrl(),
                arrivalDateTime);
        if (departureDateTime.toLocalDate().equals(arrivalDateTime.toLocalDate())) {
            return List.of(departure);
        }
        return List.of(departure, new TrainItineraryEntry(
                tripId, TrainDayRole.ARRIVAL, serviceId,
                departureStation.name(), departureStation.city(), departureStation.mapsUrl(),
                departureDateTime,
                arrivalStation.name(), arrivalStation.city(), arrivalStation.mapsUrl(),
                arrivalDateTime));
    }

    private static List<HotelItineraryEntry> toHotelEntries(HotelBooked e) {
        return toHotelEntries(e.hotelBookingId(), e.hotelName(), e.address(), e.bookingIntent(),
                e.checkIn(), e.checkOut(), e.mapsUrl());
    }

    private static List<HotelItineraryEntry> toHotelEntries(HotelChanged e) {
        return toHotelEntries(e.hotelBookingId(), e.hotelName(), e.address(), e.bookingIntent(),
                e.checkIn(), e.checkOut(), e.mapsUrl());
    }

    private static List<HotelItineraryEntry> toHotelEntries(
            HotelBookingId hotelBookingId, String hotelName, Address address, BookingIntent bookingIntent,
            LocalDateTime checkIn, LocalDateTime checkOut, String rawMapsUrl) {
        String mapsUrl = rawMapsUrl.isBlank()
                ? AddressRenderer.mapsUrl(hotelName, address)
                : rawMapsUrl;
        return List.of(
                new HotelItineraryEntry(hotelBookingId, hotelName, address, bookingIntent,
                        HotelDayRole.CHECK_IN, checkIn, mapsUrl),
                new HotelItineraryEntry(hotelBookingId, hotelName, address, bookingIntent,
                        HotelDayRole.CHECK_OUT, checkOut, mapsUrl));
    }

    private static GatheringItineraryEntry toGatheringEntry(GatheringPlanned e) {
        return new GatheringItineraryEntry(
                e.title(), e.venueName(),
                e.location().city(), e.location().country(),
                e.speaking(), e.infoUrl(),
                e.date().atTime(e.startTime()),
                e.date().atTime(e.endTime()));
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
