package dev.ted.jittertravel.application;

/**
 * Which end of a hop an endpoint can serve, and what to call the moment it carries.
 * <p>
 * <strong>This is a fact about the event, not about the form</strong> (D4). You only travel away
 * from an airport you have <em>landed</em> at and toward one you <em>fly out</em> of (Ted,
 * 2026-08-20); you leave a hotel at <em>check-out</em> and reach one at <em>check-in</em> (Ted,
 * 2026-08-21). That is why the end is decided in {@link TransferEndpointProjector}, where the event
 * is read, rather than by whoever is rendering a select — the same reasoning that keeps {@code now}
 * <em>out</em> of the projector, since what is still offerable today is not a fact about the event
 * at all.
 * <p>
 * The values are the lists in {@link GroundTransferEndpointChoices}, one apiece, which is what lets
 * the options class group rows without knowing what kind of thing produced them.
 * <p>
 * A train's two ends follow the flight rule exactly — you leave from where you pulled in and travel
 * to where you depart — which is why they share those verbs and not the hotel's.
 * <p>
 * The verb lives here because it is a function of the end and nothing else: every flight arrival
 * says "arrive". Putting it on each row would be the same four strings copied once per booking.
 */
public enum TransferEnd {

    /** The airport a flight landed at — an origin, on the "From" select. */
    FLIGHT_ARRIVAL("arrive"),
    /** The airport a flight takes off from — a destination, on the "To" select. */
    FLIGHT_DEPARTURE("depart"),
    /** The station a train pulled into — an origin, on the same rule as a flight arrival. */
    TRAIN_ARRIVAL("arrive"),
    /** The station a train leaves from — a destination, on the same rule as a flight departure. */
    TRAIN_DEPARTURE("depart"),
    /** A stay being left — an origin, on the "From" select. */
    HOTEL_CHECK_OUT("check out"),
    /** A stay being reached — a destination, on the "To" select. */
    HOTEL_CHECK_IN("check in");

    private final String verb;

    TransferEnd(String verb) {
        this.verb = verb;
    }

    /** What the label calls this end's moment: {@code … · check out Fri Sep 18, 11:00 AM}. */
    public String verb() {
        return verb;
    }
}
