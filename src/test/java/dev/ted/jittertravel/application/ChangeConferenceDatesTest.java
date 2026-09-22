package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceDatesChanged;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceNotFound;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.ChangeConferenceDatesRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Covers the decision fold: liveness and the zone both come off the conference's own
 * {@link ConferencePlanned} in the authoritative stream, and a cancellation or a decline clears it.
 */
class ChangeConferenceDatesTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalDateTime MOVED_START = LocalDateTime.of(2027, 3, 29, 9, 0);
    private static final LocalDateTime MOVED_END = LocalDateTime.of(2027, 3, 31, 17, 0);
    private static final ChangeConferenceDatesRequest MOVE =
            new ChangeConferenceDatesRequest(MOVED_START, MOVED_END);

    private final ConferenceId conferenceId = ConferenceId.random();

    @Test
    void theMovedDatesAreStampedInTheZoneTheConferenceWasPlannedIn() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(planned());

        new ChangeConferenceDates(executor).changeDates(UUID.randomUUID(), conferenceId.id(), MOVE);

        assertThat(executor.emittedEvents)
                .singleElement()
                .isEqualTo(new ConferenceDatesChanged(conferenceId,
                        ZonedTimestamp.fromLocal(MOVED_START, NEW_YORK),
                        ZonedTimestamp.fromLocal(MOVED_END, NEW_YORK)));
    }

    @Test
    void anUnknownConferenceIsRefused() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor();

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> new ChangeConferenceDates(executor)
                        .changeDates(UUID.randomUUID(), conferenceId.id(), MOVE));
    }

    @Test
    void aDeclinedConferenceIsRefused() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(planned(),
                new ConferenceAttendanceDeclined(conferenceId, "Too far", Instant.parse("2026-09-01T00:00:00Z")));

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> new ChangeConferenceDates(executor)
                        .changeDates(UUID.randomUUID(), conferenceId.id(), MOVE));
    }

    @Test
    void aCancelledConferenceIsRefused() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(planned(),
                new ConferenceCancelled(conferenceId, "Organizers called it off"));

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> new ChangeConferenceDates(executor)
                        .changeDates(UUID.randomUUID(), conferenceId.id(), MOVE));
    }

    @Test
    void anotherConferencesPlanDoesNotCount() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(
                new ConferencePlanned(ConferenceId.random(), "Other",
                        ZonedTimestamp.fromLocal(MOVED_START, NEW_YORK),
                        ZonedTimestamp.fromLocal(MOVED_END, NEW_YORK), "", atlanta()));

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> new ChangeConferenceDates(executor)
                        .changeDates(UUID.randomUUID(), conferenceId.id(), MOVE));
    }

    private ConferencePlanned planned() {
        return new ConferencePlanned(conferenceId, "DevNexus",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2027, 4, 5, 9, 0), NEW_YORK),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2027, 4, 7, 17, 0), NEW_YORK),
                "Georgia World Congress Center", atlanta());
    }

    private static Address atlanta() {
        return new Address("285 Andrew Young International Blvd NW", "Atlanta", "GA", "30313", "US", "Atlanta");
    }

    /**
     * Runs the command against the folded context, so a not-found decision propagates the domain
     * exception exactly as production would — the same double {@code DeclineConferenceTest} uses.
     */
    private static final class RecordingCommandExecutor extends CommandExecutor {
        private final List<StoredEvent> events = new ArrayList<>();
        private List<? extends Event> emittedEvents = new ArrayList<>();

        RecordingCommandExecutor(Event... history) {
            super(null, null);
            long sequence = 0;
            for (Event event : history) {
                events.add(new StoredEvent(++sequence, event.getClass(), UUID.randomUUID(),
                        Instant.parse("2026-09-01T00:00:00Z"), event, UUID.randomUUID()));
            }
        }

        @Override
        public Stream<StoredEvent> eventsForDecision() {
            return events.stream();
        }

        @Override
        public <C extends DecisionContext> void execute(UUID commandId, Object request, C context,
                                                        DomainCommand<C> command) {
            this.emittedEvents = command.execute(context).toList();
        }
    }
}
