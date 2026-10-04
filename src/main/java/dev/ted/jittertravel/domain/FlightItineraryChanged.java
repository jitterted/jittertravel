package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.util.List;

/**
 * An airline's schedule change was applied to an itinerary: some legs moved, some were added, some
 * dropped. Written in the same append as the {@link FlightChanged}, {@link FlightBooked} and
 * {@link FlightCancelled} that carry the legs' own changes (see {@code docs/FlightItineraryPlan.md}).
 * <p>
 * <strong>A full snapshot of membership</strong>, like {@link FlightChanged}: every flight that
 * belongs to the trip after the change, in departure order, <em>including</em> legs the change
 * cancelled. A cancelled leg still belongs to the trip it was part of, and the flights list shows it
 * so, which is also how a cancelled-on-its-own leg has always stayed in {@link FlightItineraryBooked}.
 * <p>
 * {@code changedOn} is when Ted recorded it, captured at the boundary and a payload field because a
 * displayed time never comes from the store's envelope (R11). {@code reason} is for recall only.
 */
public record FlightItineraryChanged(
        FlightItineraryId itineraryId,
        List<FlightId> flightIds,
        String reason,
        Instant changedOn
) implements Event {
    public FlightItineraryChanged {
        flightIds = flightIds == null ? List.of() : List.copyOf(flightIds);
        if (reason == null) {
            reason = "";
        }
    }
}
