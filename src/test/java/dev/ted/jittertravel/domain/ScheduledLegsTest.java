package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The collision rule itself. Its two load-bearing properties are the half-open comparison (a
 * connection is not a conflict) and the self-exclusion (or no booked leg could ever be corrected).
 */
class ScheduledLegsTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private final TrainTripId existingTrip = TrainTripId.random();
    private final ScheduledLegId existing = new ScheduledLegId.Train(existingTrip);

    @Test
    void aJourneyOverlappingABookedLegIsReported() {
        ScheduledLegs legs = legsWith(leg(existing, 9, 11));

        assertThat(legs.overlapping(null, at(10), at(12)))
                .isPresent();
    }

    @Test
    void aConnectionIsNotACollision() {
        // Arrive 11:00, depart 11:00. Refusing this would refuse every changeover Ted records.
        ScheduledLegs legs = legsWith(leg(existing, 9, 11));

        assertThat(legs.overlapping(null, at(11), at(14)))
                .isEmpty();
    }

    @Test
    void aJourneyEntirelyBeforeOrAfterIsNotACollision() {
        ScheduledLegs legs = legsWith(leg(existing, 9, 11));

        assertThat(legs.overlapping(null, at(6), at(8)))
                .isEmpty();
        assertThat(legs.overlapping(null, at(13), at(15)))
                .isEmpty();
    }

    @Test
    void aLegDoesNotCollideWithItself() {
        // The whole of why a change is possible: it re-states the leg being changed, so without
        // this every edit would collide with the very thing it replaces.
        ScheduledLegs legs = legsWith(leg(existing, 9, 11));

        assertThat(legs.overlapping(existing, at(9), at(11)))
                .isEmpty();
    }

    @Test
    void changingALegStillCollidesWithADifferentOne() {
        ScheduledLegId other = new ScheduledLegId.Flight(FlightId.random());
        ScheduledLegs legs = legsWith(leg(existing, 9, 11), leg(other, 10, 12));

        assertThat(legs.overlapping(existing, at(9), at(11)))
                .get()
                .extracting(ScheduledLeg::id)
                .isEqualTo(other);
    }

    @Test
    void aFlightCollidesWithATrainBecauseTheKindIsNotTheQuestion() {
        ScheduledLegs legs = legsWith(leg(new ScheduledLegId.Flight(FlightId.random()), 9, 11));

        assertThat(legs.overlapping(new ScheduledLegId.Train(TrainTripId.random()), at(10), at(12)))
                .isPresent();
    }

    @Test
    void theFirstCollidingLegIsReported() {
        // One at a time: the form names the leg to deal with, and a second would be met on the
        // next submit if it is still there.
        ScheduledLegId first = new ScheduledLegId.Train(TrainTripId.random());
        ScheduledLegId second = new ScheduledLegId.Train(TrainTripId.random());
        ScheduledLegs legs = legsWith(leg(first, 9, 13), leg(second, 10, 14));

        assertThat(legs.overlapping(null, at(11), at(12)))
                .get()
                .extracting(ScheduledLeg::id)
                .isEqualTo(first);
    }

    @Test
    void anEmptyScheduleCollidesWithNothing() {
        assertThat(ScheduledLegs.none().overlapping(null, at(9), at(11)))
                .isEmpty();
    }

    private static ScheduledLegs legsWith(ScheduledLeg... legs) {
        return new ScheduledLegs(List.of(legs));
    }

    private static ScheduledLeg leg(ScheduledLegId id, int departHour, int arriveHour) {
        return new ScheduledLeg(id, at(departHour), at(arriveHour));
    }

    private static ZonedTimestamp at(int hour) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, hour, 0), BERLIN);
    }
}
