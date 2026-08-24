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
    private final Map<PrivateEventId, PrivateEventItineraryEntry> privateEventEntries = new ConcurrentHashMap<>();
    private final Map<GroundTransferId, GroundTransferItineraryEntry> groundTransferEntries = new ConcurrentHashMap<>();

    private final TransferEndpointLabel transferLabel = new TransferEndpointLabel();

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
                case HotelBookingCancelled(HotelBookingId hotelBookingId, String _) -> hotelEntries.remove(hotelBookingId);
                case ConferencePlanned e -> conferenceEntries.put(e.conferenceId(), toConferenceEntries(e));
                case ConferenceCancelled(ConferenceId conferenceId, String _) -> conferenceEntries.remove(conferenceId);
                case ConferenceAttendanceDeclined e -> conferenceEntries.remove(e.conferenceId());
                case GatheringPlanned e -> gatheringEntries.put(e.gatheringId(), toGatheringEntry(
                        e.gatheringId(), e.title(), e.venueName(), e.location(),
                        e.speaking(), e.infoUrl(), e.startsAt(), e.endsAt()));
                case GatheringChanged e -> gatheringEntries.put(e.gatheringId(), toGatheringEntry(
                        e.gatheringId(), e.title(), e.venueName(), e.location(),
                        e.speaking(), e.infoUrl(), e.startsAt(), e.endsAt()));
                case PrivateEventPlanned e -> privateEventEntries.put(e.privateEventId(), toPrivateEventEntry(e));
                case GroundTransferPlanned e -> groundTransferEntries.put(e.groundTransferId(), toGroundTransferEntry(e));
                case GroundTransferCancelled e -> groundTransferEntries.remove(e.groundTransferId());
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
                        gatheringEntries.values().stream(),
                        privateEventEntries.values().stream(),
                        groundTransferEntries.values().stream()
                )
                .flatMap(s -> s)
                .map(e -> e.anchorTime().toLocalDate())
                .filter(d -> !d.isBefore(date))
                .min(Comparator.naturalOrder())
                .orElse(date);
    }

    /**
     * The stay covering {@code date} in full, for a day the itinerary would otherwise leave blank.
     * Check-in and check-out days are deliberately <em>not</em> covered: each already has its own
     * entry, which says where the traveler is more precisely than this line could.
     * <p>
     * Both booking intents count. The itinerary draws a tentative stay exactly as it draws a final
     * one, and a bed booked on spec still answers "where am I sleeping".
     */
    public Optional<OngoingStay> ongoingStayOn(LocalDate date) {
        return hotelEntries.values().stream()
                .filter(stay -> spansAllOf(stay, date))
                .flatMap(List::stream)
                .filter(entry -> entry.dayRole() == HotelDayRole.CHECK_IN)
                // Overlapping stays are a data problem /schedule-problems reports; pick the one
                // that started first so the answer is at least the same on every render.
                .min(Comparator.comparing(HotelItineraryEntry::anchorTime))
                .map(entry -> new OngoingStay(entry.hotelName(),
                        entry.address().city(), entry.address().country()));
    }

    private static boolean spansAllOf(List<HotelItineraryEntry> stay, LocalDate date) {
        return stay.stream().anyMatch(entry -> entry.dayRole() == HotelDayRole.CHECK_IN
                                               && entry.anchorTime().toLocalDate().isBefore(date))
               && stay.stream().anyMatch(entry -> entry.dayRole() == HotelDayRole.CHECK_OUT
                                                  && entry.anchorTime().toLocalDate().isAfter(date));
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
                .filter(e -> e.anchorTime().toLocalDate().equals(date))
                .forEach(result::add);
        conferenceEntries.values().stream()
                .flatMap(List::stream)
                .filter(e -> e.anchorDateTime().toLocalDate().equals(date))
                .forEach(result::add);
        gatheringEntries.values().stream()
                .filter(e -> e.anchorTime().toLocalDate().equals(date))
                .forEach(result::add);
        privateEventEntries.values().stream()
                .filter(e -> e.anchorTime().toLocalDate().equals(date))
                .forEach(result::add);
        groundTransferEntries.values().stream()
                .filter(e -> e.anchorTime().toLocalDate().equals(date))
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

    /**
     * Each endpoint keeps its own {@link ZonedTimestamp} so the renderer can emit the UTC instant
     * alongside the airport-local wall-clock. Day bucketing stays local (decision 7): a flight
     * gets a second, ARRIVAL entry only when it lands on a different <em>local</em> day than it
     * left — which is exactly the case a traveler needs to see twice.
     */
    private static List<FlightItineraryEntry> toFlightEntries(
            FlightId flightId,
            String airline, String flightNumber,
            String depCode, ZonedTimestamp depDt,
            String arrCode, ZonedTimestamp arrDt) {
        FlightItineraryEntry departure = new FlightItineraryEntry(
                flightId, FlightDayRole.DEPARTURE, airline, flightNumber, depCode, depDt, arrCode, arrDt);
        LocalDateTime depLocal = depDt.localDateTime();
        LocalDateTime arrLocal = arrDt.localDateTime();
        if (depLocal.toLocalDate().equals(arrLocal.toLocalDate()) && !arrLocal.isBefore(depLocal)) {
            return List.of(departure);
        }
        return List.of(departure, new FlightItineraryEntry(
                flightId, FlightDayRole.ARRIVAL, airline, flightNumber, depCode, depDt, arrCode, arrDt));
    }

    private static List<TrainItineraryEntry> toTrainEntries(TrainBooked e) {
        // Bucket each endpoint on its own entry-zone local day (decision 7).
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
            TrainStationAddress departureStation, ZonedTimestamp departureDateTime,
            TrainStationAddress arrivalStation, ZonedTimestamp arrivalDateTime) {
        TrainItineraryEntry departure = new TrainItineraryEntry(
                tripId, TrainDayRole.DEPARTURE, serviceId,
                departureStation.name(), departureStation.city(), departureStation.mapsUrl(),
                departureDateTime,
                arrivalStation.name(), arrivalStation.city(), arrivalStation.mapsUrl(),
                arrivalDateTime);
        if (departureDateTime.localDateTime().toLocalDate()
                .equals(arrivalDateTime.localDateTime().toLocalDate())) {
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
            ZonedTimestamp checkIn, ZonedTimestamp checkOut, String rawMapsUrl) {
        String mapsUrl = rawMapsUrl.isBlank()
                ? AddressRenderer.mapsUrl(hotelName, address)
                : rawMapsUrl;
        return List.of(
                new HotelItineraryEntry(hotelBookingId, hotelName, address, bookingIntent,
                        HotelDayRole.CHECK_IN, checkIn, mapsUrl),
                new HotelItineraryEntry(hotelBookingId, hotelName, address, bookingIntent,
                        HotelDayRole.CHECK_OUT, checkOut, mapsUrl));
    }

    private static GatheringItineraryEntry toGatheringEntry(GatheringId gatheringId,
                                                            String title,
                                                            String venueName,
                                                            Address location,
                                                            boolean speaking,
                                                            String infoUrl,
                                                            ZonedTimestamp startsAt,
                                                            ZonedTimestamp endsAt) {
        return new GatheringItineraryEntry(
                gatheringId,
                title, venueName,
                location.city(), location.country(),
                speaking, infoUrl,
                startsAt, endsAt);
    }

    private static PrivateEventItineraryEntry toPrivateEventEntry(PrivateEventPlanned e) {
        return new PrivateEventItineraryEntry(
                e.title(), e.venueName(),
                e.location().city(), e.location().country(),
                e.startsAt(), e.endsAt());
    }

    /**
     * Anchored on the departure alone: a transfer takes both ends' times from one zone and is a
     * short hop, so unlike a flight or a train it never needs a second, arrival-day entry.
     */
    private GroundTransferItineraryEntry toGroundTransferEntry(GroundTransferPlanned e) {
        return new GroundTransferItineraryEntry(
                e.groundTransferId(),
                transferLabel.ownerLabel(e.originAirportCode(), e.originName(), e.origin()),
                transferLabel.ownerLabel(e.destinationAirportCode(), e.destinationName(), e.destination()),
                e.departsAt(), e.arrivesAt(), e.mode());
    }

    private static List<ConferenceItineraryEntry> toConferenceEntries(ConferencePlanned e) {
        // Itinerary days are venue-local days (see CalendarEntry), so the entry keeps the
        // wall-clock the traveler will actually read off a clock when they get there.
        LocalDateTime startDateTime = e.startDate().localDateTime();
        LocalDate start = startDateTime.toLocalDate();
        int totalDays = (int) ChronoUnit.DAYS.between(start, e.endDate().localDateTime().toLocalDate()) + 1;
        List<ConferenceItineraryEntry> entries = new ArrayList<>();
        for (int i = 0; i < totalDays; i++) {
            entries.add(new ConferenceItineraryEntry(
                    e.name(), e.venueName(), e.venueAddress(),
                    i + 1, totalDays, start.plusDays(i).atTime(startDateTime.toLocalTime()),
                    e.infoUrl()));
        }
        return entries;
    }
}
