package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryId;

import java.util.List;

/**
 * One flight itinerary: the legs an airline confirmation booked together.
 * <p>
 * OWNER-only. {@code confirmationCode} is a booking reference, which the redaction rules keep off
 * every anonymous and family surface. It names its legs by id only — the flights themselves are
 * composed in by whoever shows them, because a read model is built from events alone (R12).
 * {@code cancelled} is set once the whole itinerary has been cancelled.
 */
public record BookedItineraryView(
        FlightItineraryId itineraryId,
        String airline,
        String confirmationCode,
        List<FlightId> flightIds,
        boolean cancelled
) {
    public BookedItineraryView {
        flightIds = List.copyOf(flightIds);
    }

    BookedItineraryView cancelledNow() {
        return new BookedItineraryView(itineraryId, airline, confirmationCode, flightIds, true);
    }
}
