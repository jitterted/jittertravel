package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.InvalidAirportCode;

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
 * </table>
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

    private final HotelDetailsViewProjector hotelDetails;
    private final AirportCityResolver airportCities;
    private final AirportZoneResolver airportZones;
    private final LocationZoneResolver locationZones;

    public GroundTransferEndpointResolver(HotelDetailsViewProjector hotelDetails,
                                          AirportCityResolver airportCities,
                                          AirportZoneResolver airportZones,
                                          LocationZoneResolver locationZones) {
        this.hotelDetails = hotelDetails;
        this.airportCities = airportCities;
        this.airportZones = airportZones;
        this.locationZones = locationZones;
    }

    /**
     * @throws UnknownTransferEndpoint when the token has no recognized prefix, or names a hotel
     *         booking that no longer exists (cancelled between GET and POST).
     * @throws ZoneResolutionException when the airport code or the hotel's address is one the
     *         curated tables do not know.
     */
    public TransferEndpoint resolve(String token) {
        if (token == null || token.isBlank()) {
            throw new UnknownTransferEndpoint("Pick an airport or a booked hotel for each end");
        }
        if (token.startsWith(AIRPORT_PREFIX)) {
            return airportEndpoint(token.substring(AIRPORT_PREFIX.length()));
        }
        if (token.startsWith(HOTEL_PREFIX)) {
            return hotelEndpoint(token.substring(HOTEL_PREFIX.length()));
        }
        throw new UnknownTransferEndpoint("Not an airport or a booked hotel: " + token);
    }

    private TransferEndpoint airportEndpoint(String rawCode) {
        AirportCode airport = parseAirportCode(rawCode);
        String city = airportCities.cityFor(airport.code());
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

    private HotelBookingId parseBookingId(String rawBookingId) {
        try {
            return HotelBookingId.of(UUID.fromString(rawBookingId));
        } catch (IllegalArgumentException e) {
            throw new UnknownTransferEndpoint("Not a booked hotel: " + HOTEL_PREFIX + rawBookingId);
        }
    }
}
