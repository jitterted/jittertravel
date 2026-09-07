package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.TrainTripId;

/**
 * Which booked thing a leg on the schedule came from: its typed id, and what to call it on screen.
 * <p>
 * Sealed with typed ids rather than an {@code (enum kind, String id)} pair, so a switch over it is
 * exhaustive: the day Cancel Flight ships, {@code ProblemFix} stops compiling until someone writes
 * down a flight's fix link.
 * <p>
 * It lives in {@code application} and not {@code domain} because {@link #label()} is a display
 * string, and display strings are presentation (CLAUDE.md).
 */
public sealed interface TravelLeg {

    /**
     * What to call this leg beside another one — a flight's {@code "LH 402"}, a train's service id,
     * a transfer's route.
     * <p>
     * Never blank: a train's service id is optional, so the route stands in when there is none.
     * That fallback is not a disambiguator — two duplicate legs share a route — which is why
     * {@code ProblemFix} leads its labels with the departure time instead.
     */
    String label();

    /**
     * This leg's own id, as text. What tells two legs apart, for sorting and for keying a problem.
     * <p>
     * Not {@link #detailsPath()}, which is <strong>not</strong> unique: every transfer's page is
     * {@code /itinerary}, so keying on it made every transfer-vs-train overlap share one
     * {@code ProblemKey} and every transfer sort equal to every other.
     */
    String identity();

    /** Where this leg can be corrected or removed — its detail page, or its edit page. */
    String detailsPath();

    record Flight(FlightId id, String label) implements TravelLeg {
        @Override
        public String identity() {
            return "flight:" + id.id();
        }

        @Override
        public String detailsPath() {
            return "/booked-flights/" + id.id();
        }
    }

    record Train(TrainTripId id, String label) implements TravelLeg {
        @Override
        public String identity() {
            return "train:" + id.id();
        }

        @Override
        public String detailsPath() {
            return "/booked-trains/" + id.id();
        }
    }

    record Transfer(GroundTransferId id, String label) implements TravelLeg {
        @Override
        public String identity() {
            return "transfer:" + id.id();
        }

        /** A transfer has no page of its own, so it points at the itinerary — its card and its bin. */
        @Override
        public String detailsPath() {
            return "/itinerary";
        }
    }
}
