package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Reads a pasted confirmation email into legs with instants: parse it, then settle every airport's
 * zone. Shared by booking an itinerary ({@link FlightItineraryBooking}) and changing one
 * ({@link FlightItineraryChange}), which read a paste the same way and then do different things
 * with it.
 * <p>
 * Nothing is judged until every time has a zone, because an instant is what the rules compare.
 */
final class ItineraryPasteReader {

    sealed interface Reading permits Unreadable, ZonesNeeded, Read {
    }

    /** The text could not be read as an itinerary at all. */
    record Unreadable(List<String> problems) implements Reading {
    }

    /** Read, but some airports are not in the curated table and have no zone picked yet. */
    record ZonesNeeded(PastedItinerary itinerary, List<AirportCode> unresolved) implements Reading {
    }

    /** Every leg has its instants; each carries a fresh id, minted before anything is matched. */
    record Read(PastedItinerary itinerary, List<ItineraryLeg> legs) implements Reading {
    }

    private final AirportZoneResolver airportZoneResolver;
    private final UnitedItineraryParser parser = new UnitedItineraryParser();

    ItineraryPasteReader(AirportZoneResolver airportZoneResolver) {
        this.airportZoneResolver = airportZoneResolver;
    }

    /**
     * @param airportZones zones Ted picked for airports the curated table does not know, keyed by
     *                     airport code; one pick covers every leg through that airport
     */
    Reading read(String pasted, Map<String, String> airportZones, Supplier<FlightId> newFlightId) {
        return switch (parser.parse(pasted)) {
            case UnitedItineraryParser.Unparseable unparseable -> new Unreadable(unparseable.problems());
            case UnitedItineraryParser.Parsed parsed -> withZones(parsed.itinerary(), airportZones, newFlightId);
        };
    }

    private Reading withZones(PastedItinerary itinerary, Map<String, String> airportZones,
                              Supplier<FlightId> newFlightId) {
        Map<AirportCode, ZoneId> zones = new LinkedHashMap<>();
        Set<AirportCode> unresolved = new LinkedHashSet<>();
        FlightEndpointZone endpointZone = new FlightEndpointZone(airportZoneResolver);
        for (PastedItinerary.Leg leg : itinerary.legs()) {
            for (AirportCode airport : List.of(leg.departureAirport(), leg.arrivalAirport())) {
                try {
                    zones.putIfAbsent(airport, endpointZone.resolve(airportZones.get(airport.code()), airport));
                } catch (ZoneResolutionException unknownAirport) {
                    unresolved.add(airport);
                }
            }
        }
        if (!unresolved.isEmpty()) {
            return new ZonesNeeded(itinerary, List.copyOf(unresolved));
        }
        return new Read(itinerary, itinerary.legs().stream()
                .map(leg -> resolved(leg, zones, newFlightId))
                .toList());
    }

    private ItineraryLeg resolved(PastedItinerary.Leg leg, Map<AirportCode, ZoneId> zones,
                                  Supplier<FlightId> newFlightId) {
        return new ItineraryLeg(newFlightId.get(), leg.airline(), leg.flightNumber(),
                leg.departureAirport(),
                ZonedTimestamp.fromLocal(leg.departureLocal(), zones.get(leg.departureAirport())),
                leg.arrivalAirport(),
                ZonedTimestamp.fromLocal(leg.arrivalLocal(), zones.get(leg.arrivalAirport())));
    }
}
