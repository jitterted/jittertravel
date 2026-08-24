package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCityResolver;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.InvalidAirportCode;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.Place;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.util.Locale;
import java.util.UUID;

/**
 * Turns a ground-transfer form token into a resolved {@link TransferEndpoint}. Ted never types an
 * address (D3): each end is picked from something the app already knows.
 *
 * <table>
 *   <tr><th>Token</th><th>Resolves to</th></tr>
 *   <tr><td>{@code airport:DEN}</td>
 *       <td>the code {@code DEN}, the city from {@link AirportCityResolver}, and the airport's zone</td></tr>
 *   <tr><td>{@code hotel:<bookingId>}</td>
 *       <td>the hotel's name and its {@link Address} copied <em>verbatim</em> (including
 *           {@code locationForMatching}), and the zone its address resolves to</td></tr>
 *   <tr><td>{@code train:<tripId>:arrival}<br>{@code train:<tripId>:departure}</td>
 *       <td>that end's station name and city, and the zone carried on the trip's own
 *           {@code ZonedTimestamp} — resolved once at booking, never looked up again (D5)</td></tr>
 * </table>
 *
 * A trip has two stations, so the end is part of the token; an airport and a hotel each name one
 * place, so theirs are not (D7).
 *
 * There is deliberately <strong>no free-text fallback</strong> (D12): a transfer whose end is a bare
 * venue cannot be recorded yet, rather than being recorded as an unmatched string that the schedule
 * timeline could never line up with anything.
 * <p>
 * Resolution happens server-side at submit time, and the address is <strong>snapshotted into the
 * command</strong>, never referenced live — changing the hotel later must not silently rewrite a
 * transfer already recorded.
 */
public class GroundTransferEndpointResolver {

    static final String AIRPORT_PREFIX = "airport:";
    static final String HOTEL_PREFIX = "hotel:";
    static final String TRAIN_PREFIX = "train:";

    private static final String ARRIVAL_SUFFIX = ":arrival";
    private static final String DEPARTURE_SUFFIX = ":departure";

    private final HotelDetailsViewProjector hotelDetails;
    private final TrainDetailsViewProjector trainDetails;
    private final AirportCityResolver airportCities;
    private final AirportZoneResolver airportZones;
    private final LocationZoneResolver locationZones;

    public GroundTransferEndpointResolver(HotelDetailsViewProjector hotelDetails,
                                          TrainDetailsViewProjector trainDetails,
                                          AirportCityResolver airportCities,
                                          AirportZoneResolver airportZones,
                                          LocationZoneResolver locationZones) {
        this.hotelDetails = hotelDetails;
        this.trainDetails = trainDetails;
        this.airportCities = airportCities;
        this.airportZones = airportZones;
        this.locationZones = locationZones;
    }

    /**
     * The token a station endpoint submits — {@code train:<tripId>:arrival} (D7). A trip has two
     * stations, so unlike an airport the end has to be part of the token; it is built here, beside
     * the code that takes it apart again, so the two cannot drift.
     */
    public static String trainToken(TrainTripId tripId, TransferEnd end) {
        return TRAIN_PREFIX + tripId.id()
               + (end == TransferEnd.TRAIN_ARRIVAL ? ARRIVAL_SUFFIX : DEPARTURE_SUFFIX);
    }

    /**
     * @throws UnknownTransferEndpoint when the token has no recognized prefix, or names a hotel
     *         booking that no longer exists (cancelled between GET and POST).
     * @throws ZoneResolutionException when the airport code or the hotel's address is one the
     *         curated tables do not know.
     */
    public TransferEndpoint resolve(String token) {
        if (token == null || token.isBlank()) {
            throw new UnknownTransferEndpoint("Pick a place for each end");
        }
        if (token.startsWith(AIRPORT_PREFIX)) {
            return airportEndpoint(token.substring(AIRPORT_PREFIX.length()));
        }
        if (token.startsWith(HOTEL_PREFIX)) {
            return hotelEndpoint(token.substring(HOTEL_PREFIX.length()));
        }
        if (token.startsWith(TRAIN_PREFIX)) {
            return stationEndpoint(token.substring(TRAIN_PREFIX.length()), token);
        }
        throw new UnknownTransferEndpoint(
                "Not an airport, a booked hotel or a train station: " + token);
    }

    private TransferEndpoint airportEndpoint(String rawCode) {
        AirportCode airport = parseAirportCode(rawCode);
        // The same derivation the gap report and the options list use, so what gets frozen into the
        // event is the place the schedule will look for when it decides the gap is closed.
        String city = Place.of(airport, airportCities).value();
        // Throws ZoneResolutionException for a well-formed code the curated table does not know,
        // which is the only way an airport token can be stale — the options only ever offer
        // airports that appear on a booked flight.
        return new TransferEndpoint(airport.code(), "",
                new Address("", city, "", "", "", city),
                airportZones.resolve(airport));
    }

    private AirportCode parseAirportCode(String rawCode) {
        try {
            return AirportCode.of(rawCode.trim().toUpperCase(Locale.ENGLISH));
        } catch (InvalidAirportCode e) {
            throw new UnknownTransferEndpoint("Not an airport: " + AIRPORT_PREFIX + rawCode);
        }
    }

    private TransferEndpoint hotelEndpoint(String rawBookingId) {
        HotelBookingId bookingId = parseBookingId(rawBookingId);
        HotelDetailsView hotel = hotelDetails.findById(bookingId)
                .orElseThrow(() -> new UnknownTransferEndpoint(
                        "That hotel booking is no longer available — it may have been cancelled"));
        // The address is copied verbatim, locationForMatching included: that field is what the
        // schedule timeline matches the transfer against, so re-deriving it here could silently
        // disagree with the stay it is meant to connect to.
        return new TransferEndpoint("", hotel.hotelName(), hotel.address(),
                locationZones.resolve(hotel.address()));
    }

    /**
     * A station endpoint, from {@code <tripId>:arrival} or {@code <tripId>:departure}.
     * <p>
     * The zone comes from the trip's own {@code ZonedTimestamp}, resolved at booking time by
     * {@code StationZone} (D5) — never from the curated table. So unlike the airport and hotel
     * branches this one cannot raise {@link ZoneResolutionException}, and it cannot disagree with
     * the train leg the transfer is being recorded next to.
     * <p>
     * The station's {@code name} is private exactly as a hotel's is, and the {@code Address} is
     * built with the same {@link Place} the gap report uses, so the write path and the report cannot
     * disagree about where the hop ended.
     */
    private TransferEndpoint stationEndpoint(String rawEndpoint, String wholeToken) {
        boolean arrival = rawEndpoint.endsWith(ARRIVAL_SUFFIX);
        boolean departure = rawEndpoint.endsWith(DEPARTURE_SUFFIX);
        if (!arrival && !departure) {
            throw new UnknownTransferEndpoint("Not a train station: " + wholeToken);
        }
        String rawTripId = rawEndpoint.substring(0, rawEndpoint.lastIndexOf(':'));
        TrainDetailsView trip = trainDetails.findById(parseTripId(rawTripId, wholeToken))
                .orElseThrow(() -> new UnknownTransferEndpoint(
                        "That train trip is no longer available"));
        TrainStationAddress station = arrival ? trip.arrivalStation() : trip.departureStation();
        ZonedTimestamp moment = arrival ? trip.arrivalDateTime() : trip.departureDateTime();
        return new TransferEndpoint("", station.name(),
                new Address("", station.city(), "", "", station.country(),
                        Place.of(station).value()),
                moment.zone());
    }

    private TrainTripId parseTripId(String rawTripId, String wholeToken) {
        try {
            return TrainTripId.of(UUID.fromString(rawTripId));
        } catch (IllegalArgumentException e) {
            throw new UnknownTransferEndpoint("Not a train station: " + wholeToken);
        }
    }

    private HotelBookingId parseBookingId(String rawBookingId) {
        try {
            return HotelBookingId.of(UUID.fromString(rawBookingId));
        } catch (IllegalArgumentException e) {
            throw new UnknownTransferEndpoint("Not a booked hotel: " + HOTEL_PREFIX + rawBookingId);
        }
    }
}
