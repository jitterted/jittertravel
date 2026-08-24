package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCityResolver;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.domain.Place;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The places a ground transfer can start or end at, built straight from the events.
 * <p>
 * <strong>Why this exists rather than one more conversion.</strong> The form wants rows shaped like
 * <em>endpoints</em> — a place, the moment it happens, and which end of a hop it can serve. No other
 * read model has that shape, so {@link GroundTransferEndpointOptions} used to convert two list views
 * built for two other screens into a third. Read models are specific to the view or form they feed;
 * a conversion layer over other people's views is what that heuristic exists to avoid, and it is why
 * adding a third source meant teaching the conversion a third set of accessors.
 * <p>
 * <strong>Keyed by occurrence, not by token</strong> (D3). Two flights landing at DEN are two rows
 * that both submit {@code airport:DEN}, because a transfer is between places and not between
 * flights — so the key is (subject, {@link TransferEnd}) and the token rides along as data. Nothing
 * about the stored event changes.
 * <p>
 * <strong>A cancelled stay is absent, not flagged.</strong> {@code /booked-hotels} keeps a tombstone
 * row so the cancellation is visible; this is a list of places Ted can be dropped off, and a
 * cancelled booking is not one. That turns a {@code filter(hotel -> !hotel.cancelled())} in the
 * options class into no code at all, which is the difference between a read model and a view being
 * reused.
 * <p>
 * <strong>No clock here, deliberately.</strong> Which end an event can serve is a fact about the
 * event; whether it is still worth offering today is not. The day filter stays in
 * {@link GroundTransferEndpointOptions#choicesAt}, where {@code now} arrives from the boundary. The
 * row carries {@code offeredUntil} so that filter has a moment to read — and a <em>zone</em> to read
 * it in, since an endpoint is judged by its own local day.
 * <p>
 * Both {@code FlightBooked}/{@code FlightChanged} and {@code HotelBooked}/{@code HotelChanged} are
 * full snapshots, so the latest one simply overwrites both of its rows.
 */
public class TransferEndpointProjector implements EventStreamConsumer {

    private final Map<RowKey, TransferEndpointRow> rows = new ConcurrentHashMap<>();
    private final AirportCityResolver airportCities;

    public TransferEndpointProjector(AirportCityResolver airportCities) {
        this.airportCities = airportCities;
    }

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case FlightBooked e -> putFlight(e.flightId(), e.airline(), e.flightNumber(),
                        e.departureAirport(), e.departureDateTime(),
                        e.arrivalAirport(), e.arrivalDateTime());
                case FlightChanged e -> putFlight(e.flightId(), e.airline(), e.flightNumber(),
                        e.departureAirport(), e.departureDateTime(),
                        e.arrivalAirport(), e.arrivalDateTime());
                case HotelBooked e -> putStay(e.hotelBookingId(), e.hotelName(),
                        e.address().city(), Place.of(e.address()), e.checkIn(), e.checkOut());
                case HotelChanged e -> putStay(e.hotelBookingId(), e.hotelName(),
                        e.address().city(), Place.of(e.address()), e.checkIn(), e.checkOut());
                // Not a tombstone: see the class comment. Both ends go.
                case HotelBookingCancelled e -> {
                    rows.remove(new RowKey(e.hotelBookingId().id().toString(),
                            TransferEnd.HOTEL_CHECK_OUT));
                    rows.remove(new RowKey(e.hotelBookingId().id().toString(),
                            TransferEnd.HOTEL_CHECK_IN));
                }
                default -> { /* not an endpoint event */ }
            }
        });
    }

    /** The rows that can serve one end of a hop, in no particular order — ordering is the form's. */
    public List<TransferEndpointRow> rowsFor(TransferEnd end) {
        return rows.values().stream()
                .filter(row -> row.end() == end)
                .toList();
    }

    private void putFlight(FlightId flightId, String airline, String flightNumber,
                           AirportCode departure, ZonedTimestamp departsAt,
                           AirportCode arrival, ZonedTimestamp arrivesAt) {
        String detail = airline + " " + flightNumber;
        putAirport(flightId, TransferEnd.FLIGHT_ARRIVAL, arrival, arrivesAt, detail);
        putAirport(flightId, TransferEnd.FLIGHT_DEPARTURE, departure, departsAt, detail);
    }

    private void putAirport(FlightId flightId, TransferEnd end, AirportCode airport,
                            ZonedTimestamp moment, String detail) {
        Place place = Place.of(airport, airportCities);
        put(new RowKey(flightId.id().toString(), end), new TransferEndpointRow(
                end,
                GroundTransferEndpointResolver.AIRPORT_PREFIX + airport.code(),
                airport.code(),
                // An airport's label city and its matching place are the same value; a hotel's are
                // not. Both are read from the row, so the options class never has to know which.
                place.value(),
                place,
                moment,
                moment,
                detail));
    }

    private void putStay(HotelBookingId bookingId, String hotelName, String city, Place place,
                         ZonedTimestamp checkIn, ZonedTimestamp checkOut) {
        String token = GroundTransferEndpointResolver.HOTEL_PREFIX + bookingId.id();
        // Both ends are offered while the *check-out* day is today or later, whichever end this is:
        // filtering the "To" list on check-in would drop the hotel you are riding to at the moment
        // you had arrived, which is when the ride gets written down.
        put(new RowKey(bookingId.id().toString(), TransferEnd.HOTEL_CHECK_OUT),
                new TransferEndpointRow(TransferEnd.HOTEL_CHECK_OUT, token, hotelName, city, place,
                        checkOut, checkOut, ""));
        put(new RowKey(bookingId.id().toString(), TransferEnd.HOTEL_CHECK_IN),
                new TransferEndpointRow(TransferEnd.HOTEL_CHECK_IN, token, hotelName, city, place,
                        checkIn, checkOut, ""));
    }

    private void put(RowKey key, TransferEndpointRow row) {
        rows.put(key, row);
    }

    /**
     * (subject, end) — the occurrence, not the token. Two flights into DEN are two arrival rows
     * because their flight ids differ; a re-booked flight overwrites its own two rows because they
     * do not.
     */
    private record RowKey(String subjectId, TransferEnd end) {
    }
}
