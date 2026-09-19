package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCityResolver;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.FlightId;
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
 *   <tr><td>{@code airport:DEN:<flightId>}<br>{@code airport:DEN}</td>
 *       <td>the code {@code DEN}, the city from {@link AirportCityResolver}, and the airport's zone.
 *           The leg is dropped: a transfer is between places, not between flights (D3)</td></tr>
 *   <tr><td>{@code hotel:<bookingId>}</td>
 *       <td>the hotel's name and its {@link Address} copied <em>verbatim</em> (including
 *           {@code locationForMatching}), and the zone its address resolves to</td></tr>
 *   <tr><td>{@code train:<tripId>:arrival}<br>{@code train:<tripId>:departure}</td>
 *       <td>that end's station name and city, and the zone carried on the trip's own
 *           {@code ZonedTimestamp} — resolved once at booking, never looked up again (D5)</td></tr>
 * </table>
 *
 * A trip has two stations, so the end is part of the token; a hotel names one place, so its is not
 * (D7). An airport names one place too, and its leg is in the token for a different reason
 * entirely — see {@link #airportToken}.
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

    private static final String AIRPORT_PREFIX = "airport:";
    static final String HOTEL_PREFIX = "hotel:";
    private static final String TRAIN_PREFIX = "train:";

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
     * The token a flight leg's airport submits — {@code airport:DEN:<flightId>}. The airport is the
     * place; the flight id says <em>which leg</em> offered it, and it exists for the form rather
     * than for the write path — {@link #resolve} reads the code and drops the rest.
     * <p>
     * <strong>Why a leg at all, when a transfer is between places (D3).</strong> An option's
     * {@code value} is what a {@code <select>} is selected <em>by</em>. Two legs out of DEN used to
     * be two options carrying one token, so {@code th:field} marked <em>both</em>
     * {@code selected="selected"} and the browser took the last — a form preselected from a Sep 28
     * gap displayed the Oct 15 flight, while the value it submitted and the times it filled in were
     * the right ones (2026-09-12). The place cannot identify the option, so the option carries the
     * occurrence too. Nothing about the stored event changes.
     * <p>
     * A bare {@code airport:DEN} still resolves and still means the same airport, so the leg is
     * never load-bearing. Where "is this the same place?" is asked, ask {@link #placeToken}.
     */
    public static String airportToken(AirportCode airport, FlightId flightId) {
        return AIRPORT_PREFIX + airport.code() + ":" + flightId.id();
    }

    /**
     * The part of a token that names the place, with an airport's leg dropped — what the
     * two-different-places rule compares, because landing at DEN on one flight and leaving from DEN
     * on another is still a transfer that goes nowhere. {@code null} comes back unchanged.
     * <p>
     * <strong>Normalized exactly as {@link #resolve} reads it</strong>, or two spellings of one place
     * compare as two places and the rule is walked round: an airport code is trimmed and upper-cased
     * as {@link #parseAirportCode} does, and every other token is lower-cased, because the ids in
     * {@code hotel:} and {@code train:} tokens are UUIDs and {@code UUID.fromString} reads either
     * case. The form never offers two spellings; a hand-made POST can.
     */
    public static String placeToken(String token) {
        if (token == null) {
            return null;
        }
        if (!token.startsWith(AIRPORT_PREFIX)) {
            return token.toLowerCase(Locale.ENGLISH);
        }
        return AIRPORT_PREFIX + codePartOf(token.substring(AIRPORT_PREFIX.length()))
                .trim()
                .toUpperCase(Locale.ENGLISH);
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

    private TransferEndpoint airportEndpoint(String rawEndpoint) {
        AirportCode airport = parseAirportCode(codePartOf(rawEndpoint));
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

    /**
     * The code out of an airport token's tail, dropping the leg {@link #airportToken} appends. A
     * tail with no leg is the whole of it, which is what keeps a bare {@code airport:DEN}
     * resolvable.
     */
    private static String codePartOf(String rawEndpoint) {
        int leg = rawEndpoint.indexOf(':');
        return leg < 0 ? rawEndpoint : rawEndpoint.substring(0, leg);
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
