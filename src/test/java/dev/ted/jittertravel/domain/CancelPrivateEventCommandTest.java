package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CancelPrivateEventCommandTest {

    @Test
    void emitsCancelledEventForThePrivateEventBeingCancelled() {
        PrivateEventId privateEventId = PrivateEventId.random();

        List<PrivateEventCancelled> events =
                new CancelPrivateEventCommand(privateEventId, "Rescheduled to Friday")
                        .execute(new CancelPrivateEventContext(true))
                        .toList();

        assertThat(events)
                .containsExactly(new PrivateEventCancelled(privateEventId, "Rescheduled to Friday"));
    }

    @Test
    void unknownPrivateEventIsRejected() {
        assertThatExceptionOfType(PrivateEventNotFound.class)
                .isThrownBy(() -> new CancelPrivateEventCommand(PrivateEventId.random(), "")
                        .execute(new CancelPrivateEventContext(false))
                        .toList());
    }

    @Test
    void aPrivateEventThatHasAlreadyHappenedIsStillCancellable() {
        // No time gate, deliberately unlike PlanPrivateEventCommand's future-date rule: a past
        // event is the entry still telling ScheduleGapProjector that Ted was in a city he was not.
        // This test pins the decision; the guarantee is structural, since the context has no clock.
        List<PrivateEventCancelled> events =
                new CancelPrivateEventCommand(PrivateEventId.random(), "Never happened")
                        .execute(new CancelPrivateEventContext(true))
                        .toList();

        assertThat(events).hasSize(1);
    }

    @Test
    void reasonIsCarriedOntoTheEventUntouched() {
        List<PrivateEventCancelled> events =
                new CancelPrivateEventCommand(PrivateEventId.random(), "Susan is ill")
                        .execute(new CancelPrivateEventContext(true))
                        .toList();

        assertThat(events.getFirst().reason())
                .isEqualTo("Susan is ill");
    }

    @Test
    void missingReasonBecomesTheEmptyStringRatherThanNull() {
        // "" is the absent sentinel in this domain; nothing downstream should have to null-check.
        List<PrivateEventCancelled> events =
                new CancelPrivateEventCommand(PrivateEventId.random(), null)
                        .execute(new CancelPrivateEventContext(true))
                        .toList();

        assertThat(events.getFirst().reason())
                .isEmpty();
    }
}
