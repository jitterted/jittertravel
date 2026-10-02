package dev.ted.jittertravel.domain;

/**
 * One flight of an itinerary, fully resolved: ids minted and zones settled at the boundary. The same
 * fields a {@link FlightBooked} carries, because that is what each leg becomes.
 */
public record ItineraryLeg(
        FlightId flightId,
        String airline,
        String flightNumber,
        AirportCode departureAirport,
        ZonedTimestamp departureDateTime,
        AirportCode arrivalAirport,
        ZonedTimestamp arrivalDateTime
) {

    FlightBooked booked() {
        return new FlightBooked(flightId, airline, flightNumber,
                departureAirport, departureDateTime, arrivalAirport, arrivalDateTime);
    }
}
