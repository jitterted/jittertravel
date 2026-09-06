package dev.ted.jittertravel.domain;

/**
 * Which booked leg a scheduled journey is, for the two questions the write path asks about it:
 * <em>is this the one being changed</em>, and <em>where does the reader go to look at it</em>.
 * <p>
 * Sealed over the two kinds that carry a real timetable. A ground transfer is deliberately absent:
 * its times are approximate by design, and the production sweep on 2026-09-06 found one sitting 36
 * minutes from its train — close enough that refusing on it would block legitimate data. See
 * {@code docs/CancelTrainAndOverlappingLegsPlan.md} slice 3.
 * <p>
 * It carries no display string. {@code path()} is the leg's own address in the app, which is data
 * rather than formatting — the words wrapped around it are the boundary's business.
 */
public sealed interface ScheduledLegId {

    /** Where this leg can be looked at: its detail page, or its edit page while it has none. */
    String path();

    record Flight(FlightId id) implements ScheduledLegId {
        @Override
        public String path() {
            return "/booked-flights/" + id.id();
        }
    }

    record Train(TrainTripId id) implements ScheduledLegId {
        @Override
        public String path() {
            return "/booked-trains/" + id.id();
        }
    }
}
