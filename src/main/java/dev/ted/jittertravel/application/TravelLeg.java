package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.TrainTripId;

/**
 * Which booked thing a leg on the schedule came from: its typed id, and what to call it on screen.
 * <p>
 * <strong>Sealed, with typed ids, rather than an {@code (enum kind, String id)} pair.</strong> The
 * point is {@code ProblemFix}: a switch over this is exhaustive, so the day Cancel Flight ships the
 * compiler asks what a flight's fix link should be, instead of a default arm quietly going on
 * offering nothing.
 * <p>
 * <strong>Named a leg, not a {@code Ref}</strong> (Ted, 2026-09-06). It is a typed in-memory
 * identity, and {@link dev.ted.jittertravel.web.ProblemKey} is a derived string that survives in a
 * URL — one suffix over both is what made the old name say nothing about either.
 * <p>
 * It lives in {@code application} and not {@code domain} because {@link #label()} is a display
 * string, and display strings are presentation (CLAUDE.md). {@code ScheduleProblem} keeps its
 * view-shaped records here for the same reason.
 */
public sealed interface TravelLeg {

    /**
     * What to call this leg where it is named beside another one — a flight's
     * {@code "LH 402"}, a train's service id, a transfer's route.
     * <p>
     * Never blank: a train's service id is optional on the form, so the route stands in when there
     * is none. That fallback is <em>not</em> a disambiguator — two duplicate legs share a route —
     * which is why {@code ProblemFix} leads its labels with the departure time instead.
     */
    String label();

    /** Where this leg can be corrected or removed — its edit page, or a detail page if one exists. */
    String detailsPath();

    record Flight(FlightId id, String label) implements TravelLeg {
        @Override
        public String detailsPath() {
            return "/booked-flights/" + id.id();
        }
    }

    record Train(TrainTripId id, String label) implements TravelLeg {
        @Override
        public String detailsPath() {
            return "/booked-trains/" + id.id();
        }
    }

    record Transfer(GroundTransferId id, String label) implements TravelLeg {
        /**
         * A transfer has no page of its own — there is no {@code /ground-transfers/{id}} — so it
         * points at the itinerary, which is where its card and its cancel bin are.
         */
        @Override
        public String detailsPath() {
            return "/itinerary";
        }
    }
}
