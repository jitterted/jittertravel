package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceNotFound;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.DeclineConferenceRequest;
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
 * Covers the decision fold: the service reads the authoritative event stream (not a projector) to
 * decide whether a live conference exists, treating <em>both</em> an organizer cancellation and a
 * prior decline as clearing it — so a second decline, or a decline of an already-cancelled
 * conference, is refused as not-found rather than emitting a duplicate event.
 */
class DeclineConferenceTest {

    private static final Instant DECLINED_ON = Instant.parse("2026-08-16T18:30:00Z");
    private static final ZoneId VENUE_ZONE = ZoneId.of("Africa/Casablanca");

    @Test
    void plannedConferenceDeclinesAndEmitsTheDeclinedEvent() {
        ConferenceId conferenceId = ConferenceId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, planned(conferenceId))));
        DeclineConference service = new DeclineConference(executor);

        service.declineConference(UUID.randomUUID(),
                new DeclineConferenceRequest(conferenceId.id(), "Schedule clash"), DECLINED_ON);

        assertThat(executor.emittedEvents)
                .singleElement()
                .isEqualTo(new ConferenceAttendanceDeclined(conferenceId, "Schedule clash", DECLINED_ON));
    }

    @Test
    void unknownConferenceIsRefused() {
        ConferenceId conferenceId = ConferenceId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of());
        DeclineConference service = new DeclineConference(executor);

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> service.declineConference(UUID.randomUUID(),
                        new DeclineConferenceRequest(conferenceId.id(), ""), DECLINED_ON));
    }

    @Test
    void conferenceAlreadyDeclinedIsRefused() {
        ConferenceId conferenceId = ConferenceId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, planned(conferenceId)),
                storedEvent(2, new ConferenceAttendanceDeclined(conferenceId, "First time", DECLINED_ON))));
        DeclineConference service = new DeclineConference(executor);

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> service.declineConference(UUID.randomUUID(),
                        new DeclineConferenceRequest(conferenceId.id(), "again"), DECLINED_ON));
    }

    @Test
    void conferenceCancelledByOrganizersCannotThenBeDeclined() {
        ConferenceId conferenceId = ConferenceId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, planned(conferenceId)),
                storedEvent(2, new ConferenceCancelled(conferenceId, "Organizers pulled it"))));
        DeclineConference service = new DeclineConference(executor);

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> service.declineConference(UUID.randomUUID(),
                        new DeclineConferenceRequest(conferenceId.id(), ""), DECLINED_ON));
    }

    private static ConferenceTentativelyPlanned planned(ConferenceId conferenceId) {
        Address venue = new Address("Avenue de France", "Marrakesh", "", "40000", "Morocco", "Marrakesh");
        return new ConferenceTentativelyPlanned(
                conferenceId, "Devoxx Morocco",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 7, 9, 0), VENUE_ZONE),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 9, 17, 0), VENUE_ZONE),
                "Palais des Congrès", venue);
    }

    private static StoredEvent storedEvent(long sequence, Event payload) {
        return new StoredEvent(sequence, payload.getClass(), UUID.randomUUID(),
                Instant.now(), payload, UUID.randomUUID());
    }

    /**
     * Records what the service asked the executor to do, and mimics the real executor's contract
     * closely enough for this test: it runs the command against the folded context, so a not-found
     * decision propagates the domain exception exactly as production would.
     */
    private static final class RecordingCommandExecutor extends CommandExecutor {
        private final List<StoredEvent> events;
        private List<? extends Event> emittedEvents = new ArrayList<>();

        RecordingCommandExecutor(Stream<StoredEvent> events) {
            super(null, null);
            this.events = events.toList();
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
