package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceNotFound;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.ConfirmConferenceAttendanceRequest;
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
 * decide whether a live conference exists. An organizer cancellation and a decline both clear it;
 * a prior confirmation deliberately does not, because re-confirming with a different basis is a
 * legitimate correction.
 */
class ConfirmConferenceAttendanceTest {

    private static final Instant CONFIRMED_ON = Instant.parse("2026-08-19T16:45:00Z");
    private static final ZoneId VENUE_ZONE = ZoneId.of("Africa/Casablanca");

    @Test
    void plannedConferenceConfirmsAndEmitsTheConfirmedEvent() {
        ConferenceId conferenceId = ConferenceId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, planned(conferenceId))));
        ConfirmConferenceAttendance service = new ConfirmConferenceAttendance(executor);

        service.confirmAttendance(UUID.randomUUID(),
                new ConfirmConferenceAttendanceRequest(conferenceId.id(),
                        AttendanceBasis.SPEAKING_ACCEPTED),
                CONFIRMED_ON);

        assertThat(executor.emittedEvents)
                .singleElement()
                .isEqualTo(new ConferenceAttendanceConfirmed(
                        conferenceId, AttendanceBasis.SPEAKING_ACCEPTED, CONFIRMED_ON));
    }

    @Test
    void unknownConferenceIsRefused() {
        ConferenceId conferenceId = ConferenceId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of());
        ConfirmConferenceAttendance service = new ConfirmConferenceAttendance(executor);

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> service.confirmAttendance(UUID.randomUUID(),
                        new ConfirmConferenceAttendanceRequest(conferenceId.id(),
                                AttendanceBasis.TICKET_PURCHASED),
                        CONFIRMED_ON));
    }

    @Test
    void conferenceAlreadyDeclinedCannotThenBeConfirmed() {
        // Declining removes the conference from every read model, so there is nothing left to
        // confirm — going after all means planning it again.
        ConferenceId conferenceId = ConferenceId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, planned(conferenceId)),
                storedEvent(2, new ConferenceAttendanceDeclined(conferenceId, "Clash", CONFIRMED_ON))));
        ConfirmConferenceAttendance service = new ConfirmConferenceAttendance(executor);

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> service.confirmAttendance(UUID.randomUUID(),
                        new ConfirmConferenceAttendanceRequest(conferenceId.id(),
                                AttendanceBasis.TICKET_PURCHASED),
                        CONFIRMED_ON));
    }

    @Test
    void conferenceCancelledByOrganizersCannotThenBeConfirmed() {
        ConferenceId conferenceId = ConferenceId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, planned(conferenceId)),
                storedEvent(2, new ConferenceCancelled(conferenceId, "Organizers pulled it"))));
        ConfirmConferenceAttendance service = new ConfirmConferenceAttendance(executor);

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> service.confirmAttendance(UUID.randomUUID(),
                        new ConfirmConferenceAttendanceRequest(conferenceId.id(),
                                AttendanceBasis.TICKET_PURCHASED),
                        CONFIRMED_ON));
    }

    @Test
    void alreadyConfirmedConferenceCanBeConfirmedAgainWithADifferentBasis() {
        ConferenceId conferenceId = ConferenceId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, planned(conferenceId)),
                storedEvent(2, new ConferenceAttendanceConfirmed(
                        conferenceId, AttendanceBasis.TICKET_PURCHASED, CONFIRMED_ON))));
        ConfirmConferenceAttendance service = new ConfirmConferenceAttendance(executor);

        service.confirmAttendance(UUID.randomUUID(),
                new ConfirmConferenceAttendanceRequest(conferenceId.id(),
                        AttendanceBasis.SPEAKING_ACCEPTED),
                CONFIRMED_ON);

        assertThat(executor.emittedEvents)
                .singleElement()
                .isEqualTo(new ConferenceAttendanceConfirmed(
                        conferenceId, AttendanceBasis.SPEAKING_ACCEPTED, CONFIRMED_ON));
    }

    @Test
    void anotherConferencesEventsDoNotMakeThisOneConfirmable() {
        ConferenceId wanted = ConferenceId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, planned(ConferenceId.random()))));
        ConfirmConferenceAttendance service = new ConfirmConferenceAttendance(executor);

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> service.confirmAttendance(UUID.randomUUID(),
                        new ConfirmConferenceAttendanceRequest(wanted.id(),
                                AttendanceBasis.TICKET_PURCHASED),
                        CONFIRMED_ON));
    }

    private static ConferencePlanned planned(ConferenceId conferenceId) {
        Address venue = new Address("Avenue de France", "Marrakesh", "", "40000", "Morocco", "Marrakesh");
        return new ConferencePlanned(
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
