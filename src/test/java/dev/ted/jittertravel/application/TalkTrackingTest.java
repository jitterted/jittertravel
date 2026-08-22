package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceHasNoCfp;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceNotFound;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.InvitedToSpeak;
import dev.ted.jittertravel.domain.NoTalkToDecide;
import dev.ted.jittertravel.domain.NoTalkToWithdraw;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.TalkSubmitted;
import dev.ted.jittertravel.domain.TalkWithdrawn;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.RecordTalkRequest;
import dev.ted.jittertravel.web.TalkOutcome;
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
 * Covers the decision fold: the service reads the authoritative event stream — never a projector —
 * for the three facts the speaking-axis commands need, and turns the form's
 * {@link TalkOutcome} into the command that records it.
 * <p>
 * The transitions themselves are {@code TalkPipelineCommandTest}'s subject. What is asserted here
 * is that the fold hands each command the state it should see.
 */
class TalkTrackingTest {

    private static final Instant RECORDED_ON = Instant.parse("2026-08-22T10:15:00Z");
    private static final ZoneId VENUE_ZONE = ZoneId.of("Europe/Amsterdam");

    @Test
    void submittingToAPlannedConferenceEmitsTalkSubmitted() {
        ConferenceId conferenceId = ConferenceId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, planned(conferenceId, ConferenceFormat.CALL_FOR_PAPERS))));

        new TalkTracking(executor).record(UUID.randomUUID(),
                new RecordTalkRequest(conferenceId.id(), TalkOutcome.SUBMITTED), RECORDED_ON);

        assertThat(executor.emittedEvents)
                .singleElement()
                .isEqualTo(new TalkSubmitted(conferenceId, RECORDED_ON));
    }

    /** Each outcome reaches its own command; the exhaustive switch is what forces that. */
    @Test
    void eachOutcomeRecordsItsOwnEvent() {
        ConferenceId conferenceId = ConferenceId.random();

        assertThat(emit(conferenceId, TalkOutcome.ACCEPTED,
                        storedEvent(1, planned(conferenceId, ConferenceFormat.CALL_FOR_PAPERS)),
                        storedEvent(2, new TalkSubmitted(conferenceId, RECORDED_ON))))
                .singleElement()
                .isEqualTo(new TalkAccepted(conferenceId, RECORDED_ON));

        assertThat(emit(conferenceId, TalkOutcome.REJECTED,
                        storedEvent(1, planned(conferenceId, ConferenceFormat.CALL_FOR_PAPERS)),
                        storedEvent(2, new TalkSubmitted(conferenceId, RECORDED_ON))))
                .singleElement()
                .isEqualTo(new TalkRejected(conferenceId, RECORDED_ON));

        assertThat(emit(conferenceId, TalkOutcome.WITHDRAWN,
                        storedEvent(1, planned(conferenceId, ConferenceFormat.CALL_FOR_PAPERS)),
                        storedEvent(2, new TalkSubmitted(conferenceId, RECORDED_ON))))
                .singleElement()
                .isEqualTo(new TalkWithdrawn(conferenceId, RECORDED_ON));

        assertThat(emit(conferenceId, TalkOutcome.INVITED,
                        storedEvent(1, planned(conferenceId, ConferenceFormat.CALL_FOR_PAPERS))))
                .singleElement()
                .isEqualTo(new InvitedToSpeak(conferenceId, RECORDED_ON));
    }

    /**
     * The fold takes the <em>last</em> submission event, not the best one: a rejection after a
     * submission leaves nothing outstanding to withdraw. Under the plan's original
     * "best-outcome-wins" this would still read as SUBMITTED and the withdrawal would be allowed.
     */
    @Test
    void theLastSubmissionEventDecidesTheState() {
        ConferenceId conferenceId = ConferenceId.random();

        assertThatExceptionOfType(NoTalkToWithdraw.class)
                .isThrownBy(() -> emit(conferenceId, TalkOutcome.WITHDRAWN,
                        storedEvent(1, planned(conferenceId, ConferenceFormat.CALL_FOR_PAPERS)),
                        storedEvent(2, new TalkSubmitted(conferenceId, RECORDED_ON)),
                        storedEvent(3, new TalkRejected(conferenceId, RECORDED_ON))));
    }

    @Test
    void anAcceptanceWithNoSubmissionInTheStreamIsRefused() {
        ConferenceId conferenceId = ConferenceId.random();

        assertThatExceptionOfType(NoTalkToDecide.class)
                .isThrownBy(() -> emit(conferenceId, TalkOutcome.ACCEPTED,
                        storedEvent(1, planned(conferenceId, ConferenceFormat.CALL_FOR_PAPERS))));
    }

    /** The format reaches the context off the conference's own plan, not from a projector. */
    @Test
    void theFormatIsFoldedFromTheConferencesOwnPlan() {
        ConferenceId conferenceId = ConferenceId.random();

        assertThatExceptionOfType(ConferenceHasNoCfp.class)
                .isThrownBy(() -> emit(conferenceId, TalkOutcome.SUBMITTED,
                        storedEvent(1, planned(conferenceId, ConferenceFormat.OPEN_SPACE))));
    }

    @Test
    void unknownConferenceIsRefused() {
        ConferenceId conferenceId = ConferenceId.random();

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> emit(conferenceId, TalkOutcome.SUBMITTED));
    }

    @Test
    void conferenceCancelledByOrganizersTakesItsTalkWithIt() {
        ConferenceId conferenceId = ConferenceId.random();

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> emit(conferenceId, TalkOutcome.ACCEPTED,
                        storedEvent(1, planned(conferenceId, ConferenceFormat.CALL_FOR_PAPERS)),
                        storedEvent(2, new TalkSubmitted(conferenceId, RECORDED_ON)),
                        storedEvent(3, new ConferenceCancelled(conferenceId, "Organizers pulled it"))));
    }

    @Test
    void declinedConferenceTakesItsTalkWithIt() {
        ConferenceId conferenceId = ConferenceId.random();

        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> emit(conferenceId, TalkOutcome.ACCEPTED,
                        storedEvent(1, planned(conferenceId, ConferenceFormat.CALL_FOR_PAPERS)),
                        storedEvent(2, new TalkSubmitted(conferenceId, RECORDED_ON)),
                        storedEvent(3, new ConferenceAttendanceDeclined(
                                conferenceId, "Clash", RECORDED_ON))));
    }

    /** Another conference's submission must not make this one's talk decidable. */
    @Test
    void anotherConferencesSubmissionIsNotThisOnes() {
        ConferenceId wanted = ConferenceId.random();
        ConferenceId other = ConferenceId.random();

        assertThatExceptionOfType(NoTalkToDecide.class)
                .isThrownBy(() -> emit(wanted, TalkOutcome.ACCEPTED,
                        storedEvent(1, planned(wanted, ConferenceFormat.CALL_FOR_PAPERS)),
                        storedEvent(2, planned(other, ConferenceFormat.CALL_FOR_PAPERS)),
                        storedEvent(3, new TalkSubmitted(other, RECORDED_ON))));
    }

    private static List<? extends Event> emit(ConferenceId conferenceId, TalkOutcome outcome,
                                              StoredEvent... stream) {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(stream));
        new TalkTracking(executor).record(UUID.randomUUID(),
                new RecordTalkRequest(conferenceId.id(), outcome), RECORDED_ON);
        return executor.emittedEvents;
    }

    private static ConferencePlanned planned(ConferenceId conferenceId, ConferenceFormat format) {
        Address venue = new Address("Reehorstweg 1", "Ede", "", "6717 LA", "Netherlands", "Ede");
        return new ConferencePlanned(
                conferenceId, "J-Fall",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 5, 9, 0), VENUE_ZONE),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 5, 18, 0), VENUE_ZONE),
                "De Reehorst", venue, format);
    }

    private static StoredEvent storedEvent(long sequence, Event payload) {
        return new StoredEvent(sequence, payload.getClass(), UUID.randomUUID(),
                Instant.now(), payload, UUID.randomUUID());
    }

    /** Mirrors {@code ConfirmConferenceAttendanceTest}'s: runs the command against the folded context. */
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
