package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ChangePrivateEventMatchingLocationCommandTest {

    @Test
    void emitsMatchingLocationChangedForThePrivateEventBeingRematched() {
        PrivateEventId privateEventId = PrivateEventId.random();

        List<PrivateEventMatchingLocationChanged> events =
                new ChangePrivateEventMatchingLocationCommand(privateEventId, "Lone Tree")
                        .execute(new ChangePrivateEventMatchingLocationContext(true))
                        .toList();

        assertThat(events)
                .containsExactly(
                        new PrivateEventMatchingLocationChanged(privateEventId, "Lone Tree"));
    }

    @Test
    void unknownPrivateEventIsRejected() {
        // A stale link, or an evening cancelled in another tab: an override for an event no read
        // model holds would sit in the log applying to nothing.
        assertThatExceptionOfType(PrivateEventNotFound.class)
                .isThrownBy(() -> new ChangePrivateEventMatchingLocationCommand(
                        PrivateEventId.random(), "Lone Tree")
                        .execute(new ChangePrivateEventMatchingLocationContext(false))
                        .toList());
    }

    @Test
    void blankLocationIsRejectedRatherThanClearingTheOverride() {
        // ScheduleGapProjector holds a resolved occupancy carrying one city string, not the
        // original Address, so "" has nothing to revert to. Refusing is honest; the undo is typing
        // the original city back in. See PrivateEventMatchingLocationPlan.md D4.
        assertThatExceptionOfType(InvalidMatchingLocation.class)
                .isThrownBy(() -> new ChangePrivateEventMatchingLocationCommand(
                        PrivateEventId.random(), "")
                        .execute(new ChangePrivateEventMatchingLocationContext(true))
                        .toList())
                .withMessage("Location is required");
    }

    @Test
    void whitespaceOnlyLocationIsRejectedAsBlank() {
        // TrimTypedTextAdvice already trims every bound String, so " " should never arrive from a
        // form — but a place name made of whitespace is the one value that would match nothing
        // while looking fine in the markup (event 92's failure), so the command refuses it itself.
        assertThatExceptionOfType(InvalidMatchingLocation.class)
                .isThrownBy(() -> new ChangePrivateEventMatchingLocationCommand(
                        PrivateEventId.random(), "   ")
                        .execute(new ChangePrivateEventMatchingLocationContext(true))
                        .toList());
    }

    @Test
    void nullLocationIsRejectedRatherThanReachingTheEvent() {
        assertThatExceptionOfType(InvalidMatchingLocation.class)
                .isThrownBy(() -> new ChangePrivateEventMatchingLocationCommand(
                        PrivateEventId.random(), null)
                        .execute(new ChangePrivateEventMatchingLocationContext(true))
                        .toList());
    }

    @Test
    void existenceIsCheckedBeforeTheLocation() {
        // Order matters for what the form says: a cancelled evening must report "gone", not "type
        // a city", because typing one would not help.
        assertThatExceptionOfType(PrivateEventNotFound.class)
                .isThrownBy(() -> new ChangePrivateEventMatchingLocationCommand(
                        PrivateEventId.random(), "")
                        .execute(new ChangePrivateEventMatchingLocationContext(false))
                        .toList());
    }

    @Test
    void surroundingWhitespaceIsTrimmedOnTheWayOntoTheEvent() {
        // The city is compared, not merely displayed: "Lone Tree " is a different city to
        // Place.matches while rendering identically. Address normalizes for the same reason.
        List<PrivateEventMatchingLocationChanged> events =
                new ChangePrivateEventMatchingLocationCommand(
                        PrivateEventId.random(), "  Lone Tree  ")
                        .execute(new ChangePrivateEventMatchingLocationContext(true))
                        .toList();

        assertThat(events.getFirst().locationForMatching())
                .isEqualTo("Lone Tree");
    }

    // There is deliberately no "a past evening is still rematchable" case here. It was written and
    // removed the same day: neither the command nor its context carries a clock, so the setup was
    // byte-identical to the first case above and no mutation of production code could tell the two
    // apart — it asserted a decision rather than a behaviour. The decision is recorded where it can
    // be acted on, in ChangePrivateEventMatchingLocationContext's javadoc, and the guarantee is
    // structural: give the context a clock and this file will need the case back.
}
