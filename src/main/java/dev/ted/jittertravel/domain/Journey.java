package dev.ted.jittertravel.domain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One direction of an itinerary: consecutive legs joined by connections. A connection is
 * <strong>24 hours or less</strong> on the ground; more than that ends a journey, whether or not a
 * hotel is booked (see {@code terms.md}, "Agreed: flight vocabulary"). Worked out from the legs,
 * never stored.
 * <p>
 * MD7LKB as booked is two journeys, SFO→ORD→YOW and YOW→ORD→SFO: thirteen days in Ottawa end the
 * first. Landing at ORD at 21:00 and leaving at 07:30 next day is still a connection.
 */
public record Journey(List<ItineraryLeg> legs) {

    static final Duration LONGEST_CONNECTION = Duration.ofHours(24);

    public Journey {
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("A journey has at least one leg");
        }
        legs = List.copyOf(legs);
    }

    /** The legs split into journeys, in departure order whatever order they arrive in. */
    public static List<Journey> of(List<ItineraryLeg> legs) {
        List<ItineraryLeg> inOrder = legs.stream()
                .sorted(Comparator.comparing(leg -> leg.departureDateTime().utc()))
                .toList();
        List<Journey> journeys = new ArrayList<>();
        List<ItineraryLeg> current = new ArrayList<>();
        for (ItineraryLeg leg : inOrder) {
            if (!current.isEmpty() && onTheGroundTooLong(current.getLast(), leg)) {
                journeys.add(new Journey(current));
                current = new ArrayList<>();
            }
            current.add(leg);
        }
        if (!current.isEmpty()) {
            journeys.add(new Journey(current));
        }
        return List.copyOf(journeys);
    }

    private static boolean onTheGroundTooLong(ItineraryLeg landed, ItineraryLeg next) {
        Duration onTheGround = Duration.between(landed.arrivalDateTime().utc(), next.departureDateTime().utc());
        return onTheGround.compareTo(LONGEST_CONNECTION) > 0;
    }

    public AirportCode from() {
        return legs.getFirst().departureAirport();
    }

    public AirportCode to() {
        return legs.getLast().arrivalAirport();
    }

    public ZonedTimestamp departs() {
        return legs.getFirst().departureDateTime();
    }

    public ZonedTimestamp arrives() {
        return legs.getLast().arrivalDateTime();
    }
}
