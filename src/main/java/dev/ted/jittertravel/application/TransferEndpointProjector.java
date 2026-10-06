package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCityResolver;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceDatesChanged;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.TransferEndpointWindow;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.domain.Place;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainCancelled;
import dev.ted.jittertravel.domain.TrainChanged;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
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
 * <strong>Keyed by occurrence, not by place</strong> (D3). Two flights landing at DEN are two rows,
 * because they are two moments Ted can be picked up — so the key is (subject,
 * {@link TransferEnd}). Each carries its own token, scoped to its leg, because the form selects an
 * option by its value and one token on two options selects both (2026-09-12); the <em>place</em> a
 * token resolves to is still just the airport, so nothing about the stored event changes. See
 * {@link GroundTransferEndpointResolver#airportToken}.
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
 * Every event here is a full snapshot, so the latest one simply overwrites both of its rows.
 * <p>
 * <strong>Trains take their zone from the event</strong> (D5), unlike an airport, whose zone the
 * write path still looks up. {@code TrainBooked}'s two {@code ZonedTimestamp}s were resolved at
 * booking time by {@code StationZone}, so reading them back cannot fail and cannot disagree with
 * the train leg the transfer is being recorded next to.
 */
public class TransferEndpointProjector implements EventStreamConsumer {

    private final Map<RowKey, TransferEndpointRow> rows = new ConcurrentHashMap<>();
    /** Held only so a conference's progress can say whether it has been dropped. */
    private final Map<ConferenceId, TrackedConference> conferences = new ConcurrentHashMap<>();
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
                // A cancelled flight lands at neither airport, so both ends go (as for trains).
                case FlightCancelled e -> {
                    rows.remove(new RowKey(e.flightId().id().toString(), TransferEnd.FLIGHT_DEPARTURE));
                    rows.remove(new RowKey(e.flightId().id().toString(), TransferEnd.FLIGHT_ARRIVAL));
                }
                case TrainBooked e -> putTrain(e.tripId(), e.serviceId(),
                        e.departureStation(), e.departureDateTime(),
                        e.arrivalStation(), e.arrivalDateTime());
                case TrainChanged e -> putTrain(e.tripId(), e.serviceId(),
                        e.departureStation(), e.departureDateTime(),
                        e.arrivalStation(), e.arrivalDateTime());
                // Not a tombstone either, and for the class comment's reason: this is a list of
                // places Ted can be picked up, and a cancelled trip stops at none of them.
                case TrainCancelled e -> {
                    rows.remove(new RowKey(e.tripId().id().toString(), TransferEnd.TRAIN_DEPARTURE));
                    rows.remove(new RowKey(e.tripId().id().toString(), TransferEnd.TRAIN_ARRIVAL));
                }
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
                // Gatherings can be edited but never cancelled, so there is no removal case.
                case GatheringPlanned e -> putGathering(e.gatheringId(), e.title(), e.venueName(),
                        e.location(), e.startsAt(), e.endsAt());
                case GatheringChanged e -> putGathering(e.gatheringId(), e.title(), e.venueName(),
                        e.location(), e.startsAt(), e.endsAt());
                case ConferencePlanned e -> trackConference(new TrackedConference(e.conferenceId(),
                        e.name(), e.venueName(), e.venueAddress(), e.startDate(), e.endDate(),
                        ConferenceProgress.planned(e.format())));
                case ConferenceDatesChanged e -> redateConference(e.conferenceId(),
                        e.startDate(), e.endDate());
                // The organizers called it off: there is no venue to ride to.
                case ConferenceCancelled e -> forgetConference(e.conferenceId());
                // Only these two can drop a conference, and ConferenceProgress alone says whether
                // they did — a conference Ted is no longer going to stops being offered exactly
                // when it leaves the schedule. The other talk and attendance events cannot change
                // that (a rejection drops by the format alone, a decline always does), so unlike
                // ScheduleGapProjector this does not fold them: here they would be dead weight.
                case ConferenceAttendanceDeclined e ->
                        moveConference(e.conferenceId(), ConferenceProgress::declined);
                case TalkRejected e ->
                        moveConference(e.conferenceId(), ConferenceProgress::rejected);
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

    /**
     * The window the form's row for this token covers on the given side — empty when no row
     * carries that token on that side, which is what a bare {@code airport:DEN} or a hand-made POST
     * looks like. The token is matched exactly as the form submitted it.
     */
    public Optional<TransferEndpointWindow> windowOf(String token, boolean asOrigin) {
        return rows.values().stream()
                .filter(row -> row.token().equals(token) && row.end().isOrigin() == asOrigin)
                .map(TransferEndpointRow::window)
                .findFirst();
    }

    /** Whether any row is offered under this token — false for a conference since dropped. */
    public boolean offers(String token) {
        return rows.values().stream().anyMatch(row -> row.token().equals(token));
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
                // The airport is the place this row offers; the leg is in the token because the
                // form selects an option by its value, and two legs through one airport are two
                // options. See GroundTransferEndpointResolver.airportToken.
                GroundTransferEndpointResolver.airportToken(airport, flightId),
                airport.code(),
                // An airport's label city and its matching place are the same value; a hotel's are
                // not. Both are read from the row, so the options class never has to know which.
                place.value(),
                place,
                moment,
                TransferEndpointWindow.at(moment),
                moment,
                detail));
    }

    private void putTrain(TrainTripId tripId, String serviceId,
                          TrainStationAddress departure, ZonedTimestamp departsAt,
                          TrainStationAddress arrival, ZonedTimestamp arrivesAt) {
        putStation(tripId, TransferEnd.TRAIN_ARRIVAL, arrival, arrivesAt, serviceId);
        putStation(tripId, TransferEnd.TRAIN_DEPARTURE, departure, departsAt, serviceId);
    }

    /**
     * A trip has two stations, so unlike an airport the end has to be in the token (D7):
     * {@code train:<tripId>:arrival}. The zone rides on the moment, resolved at booking time by
     * {@code StationZone} — never re-derived from a curated table, so it cannot fail and cannot
     * disagree with the train leg the transfer is being recorded next to (D5).
     */
    private void putStation(TrainTripId tripId, TransferEnd end, TrainStationAddress station,
                            ZonedTimestamp moment, String serviceId) {
        put(new RowKey(tripId.id().toString(), end), new TransferEndpointRow(
                end,
                GroundTransferEndpointResolver.trainToken(tripId, end),
                // The station's name is private exactly as a hotel's is; it reaches the label and
                // the event, never the public calendar. See TransferEndpointLabel.publicLabel.
                station.name(),
                station.city(),
                Place.of(station),
                moment,
                TransferEndpointWindow.at(moment),
                moment,
                serviceId));
    }

    private void putStay(HotelBookingId bookingId, String hotelName, String city, Place place,
                         ZonedTimestamp checkIn, ZonedTimestamp checkOut) {
        String token = GroundTransferEndpointResolver.HOTEL_PREFIX + bookingId.id();
        // Both ends are offered while the *check-out* day is today or later, whichever end this is:
        // filtering the "To" list on check-in would drop the hotel you are riding to at the moment
        // you had arrived, which is when the ride gets written down.
        // The window is the whole stay at both ends: a mid-stay ride to a gathering happens on
        // neither the check-in nor the check-out day.
        var stay = new TransferEndpointWindow(checkIn, checkOut);
        put(new RowKey(bookingId.id().toString(), TransferEnd.HOTEL_CHECK_OUT),
                new TransferEndpointRow(TransferEnd.HOTEL_CHECK_OUT, token, hotelName, city, place,
                        checkOut, stay, checkOut, ""));
        put(new RowKey(bookingId.id().toString(), TransferEnd.HOTEL_CHECK_IN),
                new TransferEndpointRow(TransferEnd.HOTEL_CHECK_IN, token, hotelName, city, place,
                        checkIn, stay, checkOut, ""));
    }

    /**
     * Both ends are offered until the gathering's end, for the hotel's reason. The label's name is
     * the gathering's title when it has one, else its venue: a title is what Ted recognises, and a
     * gathering's is public anyway (Ted, 2026-10-06).
     */
    private void putGathering(GatheringId gatheringId, String title, String venueName,
                              Address location, ZonedTimestamp startsAt, ZonedTimestamp endsAt) {
        String token = GroundTransferEndpointResolver.GATHERING_PREFIX + gatheringId.id();
        String name = title.isBlank() ? venueName : title;
        Place place = Place.of(location);
        var window = new TransferEndpointWindow(startsAt, endsAt);
        put(new RowKey(gatheringId.id().toString(), TransferEnd.GATHERING_END),
                new TransferEndpointRow(TransferEnd.GATHERING_END, token, name, location.city(),
                        place, endsAt, window, endsAt, ""));
        put(new RowKey(gatheringId.id().toString(), TransferEnd.GATHERING_START),
                new TransferEndpointRow(TransferEnd.GATHERING_START, token, name, location.city(),
                        place, startsAt, window, endsAt, ""));
    }

    /**
     * Both ends are the conference's whole span and are offered until its last day, as for a
     * gathering. The name is the conference's, which is public by decision; the venue is what the
     * transfer itself records (see {@link GroundTransferEndpointResolver}).
     */
    private void putConference(TrackedConference conference) {
        String id = conference.conferenceId().id().toString();
        String token = GroundTransferEndpointResolver.CONFERENCE_PREFIX + id;
        Address address = conference.venueAddress();
        Place place = Place.of(address);
        var window = new TransferEndpointWindow(conference.startDate(), conference.endDate());
        put(new RowKey(id, TransferEnd.CONFERENCE_END),
                new TransferEndpointRow(TransferEnd.CONFERENCE_END, token, conference.name(),
                        address.city(), place, conference.endDate(), window,
                        conference.endDate(), ""));
        put(new RowKey(id, TransferEnd.CONFERENCE_START),
                new TransferEndpointRow(TransferEnd.CONFERENCE_START, token, conference.name(),
                        address.city(), place, conference.startDate(), window,
                        conference.endDate(), ""));
    }

    private void trackConference(TrackedConference conference) {
        conferences.put(conference.conferenceId(), conference);
        putConference(conference);
    }

    private void forgetConference(ConferenceId conferenceId) {
        conferences.remove(conferenceId);
        rows.remove(new RowKey(conferenceId.id().toString(), TransferEnd.CONFERENCE_START));
        rows.remove(new RowKey(conferenceId.id().toString(), TransferEnd.CONFERENCE_END));
    }

    /** An event for a conference never seen is a no-op; one that drops it takes its rows with it. */
    private void moveConference(ConferenceId conferenceId,
                                UnaryOperator<ConferenceProgress> change) {
        TrackedConference tracked = conferences.get(conferenceId);
        if (tracked != null && change.apply(tracked.progress()).dropped()) {
            forgetConference(conferenceId);
        }
    }

    private void redateConference(ConferenceId conferenceId,
                                  ZonedTimestamp newStart, ZonedTimestamp newEnd) {
        TrackedConference tracked = conferences.get(conferenceId);
        if (tracked != null) {
            trackConference(tracked.during(newStart, newEnd));
        }
    }

    private record TrackedConference(ConferenceId conferenceId, String name, String venueName,
                                     Address venueAddress, ZonedTimestamp startDate,
                                     ZonedTimestamp endDate, ConferenceProgress progress) {
        TrackedConference during(ZonedTimestamp newStart, ZonedTimestamp newEnd) {
            return new TrackedConference(conferenceId, name, venueName, venueAddress,
                    newStart, newEnd, progress);
        }
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
