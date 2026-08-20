package dev.ted.jittertravel.application;

import java.time.LocalDate;

/**
 * What the schedule <em>holds</em> on a run of days — the facts a {@link ScheduleProblem} was
 * derived from. A problem on its own says a bed is missing; the context says the conference runs
 * the 14th to the 18th, which is why.
 * <p>
 * It comes from {@link ScheduleGapProjector}, deliberately: the same state the detector read. A
 * second source could disagree with the first — a conference dropped in one model and not the
 * other paints a gap with no cause, or a cause with no gap — and the whole point of showing the
 * two together is that they line up.
 * <p>
 * Days are local dates at the location, like every other placement in this app: they say which
 * column an item sits in, and that column is always the local day where it happens.
 */
public sealed interface ScheduleContext {

    LocalDate firstDay();

    LocalDate lastDay();

    record Conference(String name, String city, LocalDate firstDay, LocalDate lastDay) implements ScheduleContext {
    }

    record Gathering(String name, String city, LocalDate firstDay, LocalDate lastDay) implements ScheduleContext {
    }

    /**
     * A private social event. It places Ted somewhere exactly as a gathering does, and gets its own
     * variant rather than being folded into {@link Gathering} for the same reason
     * {@code EntryKind.PRIVATE_EVENT} does on the public calendar: the two differ in who may see
     * them, and collapsing them here would make that distinction one refactor away from being lost.
     * This page is OWNER-only, so both render in full — but the type keeps the difference visible.
     */
    record PrivateEvent(String name, String city, LocalDate firstDay, LocalDate lastDay) implements ScheduleContext {
    }

    /** A booked leg, flight or train alike: the calendar cares that it moves you, not how. */
    record Travel(String fromCity, String toCity, LocalDate firstDay, LocalDate lastDay) implements ScheduleContext {
    }

    /** A hotel stay that <em>exists</em> — the counterpart to a {@link ScheduleProblem.MissingHotel}. */
    record Stay(String city, LocalDate firstDay, LocalDate lastDay) implements ScheduleContext {
    }
}
