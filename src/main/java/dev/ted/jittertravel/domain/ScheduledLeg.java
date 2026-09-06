package dev.ted.jittertravel.domain;

/**
 * One booked journey with a real timetable: which leg it is, and the window it occupies.
 * <p>
 * Deliberately thinner than the timeline's own leg type — no cities, no name. The write path asks
 * one question of it ("does the proposed journey collide with this one?"), and carrying anything
 * the answer does not need would invite a rule that quietly depends on it.
 */
public record ScheduledLeg(
        ScheduledLegId id,
        ZonedTimestamp departure,
        ZonedTimestamp arrival
) {

    /**
     * A real overlap in time — the same half-open predicate the schedule-problems detector uses,
     * and half-open for the same load-bearing reason: <strong>a connection is not a conflict</strong>.
     * Landing at 11:00 and departing at 11:00 is the ordinary shape of a journey, and a closed
     * comparison would refuse every changeover Ted tried to record.
     */
    public boolean overlaps(ZonedTimestamp otherDeparture, ZonedTimestamp otherArrival) {
        return departure.utc().isBefore(otherArrival.utc())
               && otherDeparture.utc().isBefore(arrival.utc());
    }
}
