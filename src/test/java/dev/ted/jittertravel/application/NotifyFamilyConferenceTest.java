package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceDatesChanged;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FamilyNotified;
import dev.ted.jittertravel.domain.InvitedToSpeak;
import dev.ted.jittertravel.domain.NotifiedFact;
import dev.ted.jittertravel.domain.NotifiedSubject;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.TalkSubmitted;
import dev.ted.jittertravel.domain.TalkWithdrawn;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.FamilyMessage;
import dev.ted.jittertravel.infrastructure.FamilyNotificationMessages;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Whether family are told about a conference is folded from that conference's own events and from
 * what they were last told. The executor is the real one over a mocked persister and store, as in
 * {@link NotifyFamilyTest}; the Brevo client is a spy for what was sent. Each case pins one rule:
 * the fact comparison, positive-first, the fold, or what the words may and may not carry.
 */
@ExtendWith(MockitoExtension.class)
class NotifyFamilyConferenceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T17:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Mock PostgresPersister persister;
    @Mock EventStore eventStore;
    @Mock BrevoEmailClient brevo;

    private final ConferenceId socrates = ConferenceId.random();
    private final NotifiedSubject subject = NotifiedSubject.conference(socrates);

    private NotifyFamily service() {
        return new NotifyFamily(new CommandExecutor(persister, eventStore), brevo,
                new FamilyNotificationMessages(new StaticAirportCityResolver(), ""), true);
    }

    private void configuredWithHistory(Event... events) {
        given(brevo.configured()).willReturn(true);
        history(events);
    }

    private void history(Event... events) {
        long[] sequence = {0};
        given(eventStore.findAll()).willAnswer(_ -> Stream.of(events)
                .map(event -> new StoredEvent(++sequence[0], event.getClass(), UUID.randomUUID(), NOW, event,
                        UUID.randomUUID())));
    }

    private ConferencePlanned planned(ConferenceFormat format) {
        return plannedAs(socrates, "SoCraTes 2026", format);
    }

    private ConferencePlanned plannedAs(ConferenceId id, String name, ConferenceFormat format) {
        return new ConferencePlanned(id, name, at(2026, 8, 24), at(2026, 8, 27),
                "Seminarzentrum Rückersbach",
                new Address("Rückersbach 1", "Johannesberg", "BY", "63867", "DE", ""),
                format, "https://socrates-conference.de");
    }

    private ZonedTimestamp at(int year, int month, int day) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(year, month, day, 9, 0), BERLIN);
    }

    private FamilyNotified told(NotifiedFact fact) {
        return new FamilyNotified(subject, fact, NOW.minusSeconds(60));
    }

    private ConferenceAttendanceConfirmed confirmed(AttendanceBasis basis) {
        return new ConferenceAttendanceConfirmed(socrates, basis, NOW.minusSeconds(120));
    }

    private ConferenceAttendanceDeclined declined(String reason) {
        return new ConferenceAttendanceDeclined(socrates, reason, NOW.minusSeconds(120));
    }

    private NotifyFamily.Outcome notifyOfConference() {
        return service().notifyOfConference(UUID.randomUUID(), socrates, NOW);
    }

    private FamilyMessage sent() {
        ArgumentCaptor<FamilyMessage> sent = ArgumentCaptor.forClass(FamilyMessage.class);
        verify(brevo).send(sent.capture());
        return sent.getValue();
    }

    // ---- going ---------------------------------------------------------------------------------

    @Test
    void aConfirmedConferenceTellsFamilyHeIsGoingAndRecordsIt() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), confirmed(AttendanceBasis.TICKET_PURCHASED));
        UUID commandId = UUID.randomUUID();

        NotifyFamily.Outcome outcome = service().notifyOfConference(commandId, socrates, NOW);

        assertThat(outcome)
                .isEqualTo(NotifyFamily.Outcome.SENT);
        verify(persister).saveCommand(commandId,
                new NotifyFamilyCommand(subject, NotifiedFact.CONFERENCE_GOING, NOW));
        assertThat(sent().subject())
                .isEqualTo("(JitterTravel) Ted is going to a conference: SoCraTes 2026");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Stream<? extends Event>> appended = ArgumentCaptor.forClass(Stream.class);
        verify(eventStore).append(appended.capture(), eq(commandId));
        assertThat(appended.getValue().map(Event.class::cast).toList())
                .containsExactly(new FamilyNotified(subject, NotifiedFact.CONFERENCE_GOING, NOW));
    }

    @Test
    void aConferenceOnlyBeingWatchedIsNeverNews() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS),
                new TalkSubmitted(socrates, NOW.minusSeconds(120)));

        assertThat(notifyOfConference())
                .isEqualTo(NotifyFamily.Outcome.NOTHING_TO_SAY);
        verify(persister, never()).saveCommand(any(), any());
        verify(brevo, never()).send(any());
    }

    @Test
    void aConferenceTheStreamDoesNotKnowIsNothingToSay() {
        configuredWithHistory();

        assertThat(notifyOfConference())
                .isEqualTo(NotifyFamily.Outcome.NOTHING_TO_SAY);
        verify(brevo, never()).send(any());
    }

    @Test
    void confirmingWhatFamilyAlreadyKnowIsNotSaidAgain() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), confirmed(AttendanceBasis.TICKET_PURCHASED),
                told(NotifiedFact.CONFERENCE_GOING), confirmed(AttendanceBasis.TICKET_PURCHASED));

        assertThat(notifyOfConference())
                .isEqualTo(NotifyFamily.Outcome.ALREADY_TOLD);
        verify(persister, never()).saveCommand(any(), any());
        verify(brevo, never()).send(any());
    }

    @Test
    void aTalkAcceptedAfterAConfirmationIsTheSameGoingAndSaysNothingNew() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), confirmed(AttendanceBasis.TICKET_PURCHASED),
                told(NotifiedFact.CONFERENCE_GOING), new TalkAccepted(socrates, NOW.minusSeconds(30)));

        assertThat(notifyOfConference())
                .as("a speaking-axis change while still attending is silent (a stale line is accepted)")
                .isEqualTo(NotifyFamily.Outcome.ALREADY_TOLD);
        verify(brevo, never()).send(any());
    }

    @Test
    void aConferenceFamilyWereToldAboutDoesNotSuppressAnotherOne() {
        ConferenceId other = ConferenceId.random();
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), confirmed(AttendanceBasis.TICKET_PURCHASED),
                new FamilyNotified(NotifiedSubject.conference(other), NotifiedFact.CONFERENCE_GOING,
                        NOW.minusSeconds(60)));

        assertThat(notifyOfConference())
                .isEqualTo(NotifyFamily.Outcome.SENT);
    }

    @Test
    void anotherConferencesEventsNeverMoveThisOne() {
        ConferenceId other = ConferenceId.random();
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS),
                plannedAs(other, "Devoxx", ConferenceFormat.CALL_FOR_PAPERS),
                new ConferenceAttendanceConfirmed(other, AttendanceBasis.TICKET_PURCHASED, NOW));

        assertThat(notifyOfConference())
                .isEqualTo(NotifyFamily.Outcome.NOTHING_TO_SAY);
    }

    // ---- what the going email says -------------------------------------------------------------

    @Test
    void anAcceptedTalkIsSaidInTheGoingEmail() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), new TalkAccepted(socrates, NOW));

        notifyOfConference();

        assertThat(sent().textContent())
                .contains("SoCraTes 2026\nSeminarzentrum Rückersbach, Johannesberg, Germany\nMon 24 Aug – Thu 27 Aug 2026\n"
                          + "His talk was accepted.\nhttps://socrates-conference.de");
    }

    @Test
    void anInvitationTakenUpIsSaidAsAnInvitation() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), new InvitedToSpeak(socrates, NOW),
                confirmed(AttendanceBasis.SPEAKING_INVITED));

        notifyOfConference();

        assertThat(sent().textContent())
                .contains("He was invited to speak.")
                .doesNotContain("accepted");
    }

    @Test
    void anInvitationNotYetTakenUpIsNotGoingAndNotNews() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), new InvitedToSpeak(socrates, NOW));

        assertThat(notifyOfConference())
                .as("an unanswered invitation would tell family a stranger-visible secret one bit at a time")
                .isEqualTo(NotifyFamily.Outcome.NOTHING_TO_SAY);
    }

    @Test
    void aTicketBoughtAfterAnInvitationSaysNothingAboutATalk() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), new InvitedToSpeak(socrates, NOW),
                confirmed(AttendanceBasis.TICKET_PURCHASED));

        notifyOfConference();

        assertThat(sent().textContent())
                .doesNotContain("talk")
                .doesNotContain("invited")
                .doesNotContain("speak");
    }

    @Test
    void aConferenceRecordedBeforeTalkEventsExistedSpeaksThroughItsConfirmationBasis() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), confirmed(AttendanceBasis.SPEAKING_ACCEPTED));

        notifyOfConference();

        assertThat(sent().textContent())
                .contains("His talk was accepted.");
    }

    @Test
    void aTalkSubmittedOrAnInvitationElsewhereDoesNotUnsayThisConferencesAcceptedTalk() {
        ConferenceId elsewhere = ConferenceId.random();
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), new TalkAccepted(socrates, NOW),
                new TalkSubmitted(elsewhere, NOW), new InvitedToSpeak(elsewhere, NOW));

        notifyOfConference();

        assertThat(sent().textContent())
                .contains("His talk was accepted.");
    }

    @Test
    void aWithdrawnTalkLeavesHimGoingButTheEmailNoLongerSaysTheTalkWasAccepted() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), new TalkAccepted(socrates, NOW),
                new TalkWithdrawn(socrates, NOW));

        NotifyFamily.Outcome outcome = notifyOfConference();

        assertThat(outcome)
                .as("withdrawing a talk moves the speaking axis only, so he is still going")
                .isEqualTo(NotifyFamily.Outcome.SENT);
        assertThat(sent().textContent())
                .doesNotContain("talk");
    }

    @Test
    void aMovedConferenceIsDescribedOnItsCurrentDates() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), confirmed(AttendanceBasis.TICKET_PURCHASED),
                new ConferenceDatesChanged(socrates, at(2026, 8, 17), at(2026, 8, 20)));

        notifyOfConference();

        assertThat(sent().textContent())
                .contains("Mon 17 Aug – Thu 20 Aug 2026")
                .doesNotContain("24 Aug");
    }

    // ---- not going: told only where going went first -------------------------------------------

    @Test
    void decliningAConferenceFamilyWereToldHeWasGoingToTellsThemAndRecordsIt() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), confirmed(AttendanceBasis.TICKET_PURCHASED),
                told(NotifiedFact.CONFERENCE_GOING), declined("venue flooded"));
        UUID commandId = UUID.randomUUID();

        NotifyFamily.Outcome outcome = service().notifyOfConference(commandId, socrates, NOW);

        assertThat(outcome)
                .isEqualTo(NotifyFamily.Outcome.SENT);
        verify(persister).saveCommand(commandId,
                new NotifyFamilyCommand(subject, NotifiedFact.CONFERENCE_NOT_GOING, NOW));
        FamilyMessage message = sent();
        assertThat(message.subject())
                .isEqualTo("(JitterTravel) Ted is no longer going to SoCraTes 2026");
        assertThat(message.textContent())
                .contains("Hi, JitterTravel here. Ted is no longer going to the conference below, and he wanted to let you know.")
                .contains("SoCraTes 2026\nSeminarzentrum Rückersbach, Johannesberg, Germany\nMon 24 Aug – Thu 27 Aug 2026")
                .doesNotContain("rejected")
                .doesNotContain("organizers");
    }

    @Test
    void aReasonTedTypedForHimselfNeverReachesFamily() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), confirmed(AttendanceBasis.TICKET_PURCHASED),
                told(NotifiedFact.CONFERENCE_GOING), declined("venue flooded"));

        notifyOfConference();

        FamilyMessage message = sent();
        assertThat(message.textContent())
                .doesNotContain("flooded");
        assertThat(message.subject())
                .doesNotContain("flooded");
    }

    @Test
    void anOrganizerCancellationIsSaidAsTheirs() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), confirmed(AttendanceBasis.TICKET_PURCHASED),
                told(NotifiedFact.CONFERENCE_GOING), new ConferenceCancelled(socrates, "lack of sponsors"));

        notifyOfConference();

        FamilyMessage message = sent();
        assertThat(message.textContent())
                .contains("Hi, JitterTravel here. The organizers cancelled the conference below, so Ted is no longer going, and he wanted to let you know.")
                .doesNotContain("sponsors")
                .doesNotContain("rejected");
    }

    @Test
    void aRejectionIsSaidPlainly() {
        configuredWithHistory(planned(ConferenceFormat.ACCEPTANCE_REQUIRED), new TalkAccepted(socrates, NOW),
                told(NotifiedFact.CONFERENCE_GOING), new TalkRejected(socrates, NOW));

        NotifyFamily.Outcome outcome = notifyOfConference();

        assertThat(outcome)
                .isEqualTo(NotifyFamily.Outcome.SENT);
        assertThat(sent().textContent())
                .contains("SoCraTes 2026\nSeminarzentrum Rückersbach, Johannesberg, Germany\nMon 24 Aug – Thu 27 Aug 2026\n"
                          + "His talk was rejected, so he is not going.");
    }

    @Test
    void aRejectionRecordedAfterTedAlreadyDeclinedDoesNotChangeHowItEnded() {
        configuredWithHistory(planned(ConferenceFormat.ACCEPTANCE_REQUIRED), new TalkAccepted(socrates, NOW),
                told(NotifiedFact.CONFERENCE_GOING), declined(""), new TalkRejected(socrates, NOW));

        notifyOfConference();

        assertThat(sent().textContent())
                .as("it ended when he declined; a later rejection is not what family are being corrected about")
                .doesNotContain("rejected");
    }

    @Test
    void anExitFromAConferenceFamilyWereNeverToldAboutIsSilent() {
        configuredWithHistory(planned(ConferenceFormat.ACCEPTANCE_REQUIRED), new TalkSubmitted(socrates, NOW),
                new TalkRejected(socrates, NOW));

        assertThat(notifyOfConference())
                .as("positive-first: a rejection on a conference nobody heard of announces nothing")
                .isEqualTo(NotifyFamily.Outcome.NEVER_TOLD);
        verify(persister, never()).saveCommand(any(), any());
        verify(brevo, never()).send(any());
    }

    @Test
    void anExitAlreadyToldIsNotToldAgain() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), confirmed(AttendanceBasis.TICKET_PURCHASED),
                told(NotifiedFact.CONFERENCE_GOING), declined(""), told(NotifiedFact.CONFERENCE_NOT_GOING));

        assertThat(notifyOfConference())
                .isEqualTo(NotifyFamily.Outcome.ALREADY_TOLD);
        verify(brevo, never()).send(any());
    }

    @Test
    void aTalkRejectedWhereAttendingWithoutATalkIsStillAllowedDoesNotDropIt() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), confirmed(AttendanceBasis.TICKET_PURCHASED),
                told(NotifiedFact.CONFERENCE_GOING), new TalkRejected(socrates, NOW));

        assertThat(notifyOfConference())
                .as("a rejection where attending is still possible moves only the speaking axis")
                .isEqualTo(NotifyFamily.Outcome.ALREADY_TOLD);
    }

    @Test
    void acceptRejectThenATicketIsThreeSendsAndConfirmThenReconfirmIsOne() {
        given(brevo.configured()).willReturn(true);
        ConferencePlanned plannedByAcceptance = planned(ConferenceFormat.ACCEPTANCE_REQUIRED);
        TalkAccepted accepted = new TalkAccepted(socrates, NOW);
        TalkRejected rejected = new TalkRejected(socrates, NOW);
        ConferenceAttendanceConfirmed ticket = confirmed(AttendanceBasis.TICKET_PURCHASED);

        history(plannedByAcceptance, accepted);
        NotifyFamily.Outcome first = notifyOfConference();
        history(plannedByAcceptance, accepted, told(NotifiedFact.CONFERENCE_GOING), rejected);
        NotifyFamily.Outcome second = notifyOfConference();
        history(plannedByAcceptance, accepted, told(NotifiedFact.CONFERENCE_GOING), rejected,
                told(NotifiedFact.CONFERENCE_NOT_GOING), ticket);
        NotifyFamily.Outcome third = notifyOfConference();
        history(plannedByAcceptance, accepted, told(NotifiedFact.CONFERENCE_GOING), rejected,
                told(NotifiedFact.CONFERENCE_NOT_GOING), ticket, told(NotifiedFact.CONFERENCE_GOING), ticket);
        NotifyFamily.Outcome fourth = notifyOfConference();

        assertThat(List.of(first, second, third, fourth))
                .containsExactly(NotifyFamily.Outcome.SENT, NotifyFamily.Outcome.SENT,
                        NotifyFamily.Outcome.SENT, NotifyFamily.Outcome.ALREADY_TOLD);
        verify(brevo, times(3)).send(any());
    }

    @Test
    void aDisabledNotifierSaysNothingAboutAConference() {
        assertThat(new NotifyFamily(new CommandExecutor(persister, eventStore), brevo,
                new FamilyNotificationMessages(new StaticAirportCityResolver(), ""), false)
                .notifyOfConference(UUID.randomUUID(), socrates, NOW))
                .isEqualTo(NotifyFamily.Outcome.NOT_ENABLED);
        given(brevo.configured()).willReturn(false);
        assertThat(notifyOfConference())
                .isEqualTo(NotifyFamily.Outcome.NOT_CONFIGURED);
        verify(persister, never()).saveCommand(any(), any());
        verify(brevo, never()).send(any());
    }

    @Test
    void aFailedSendLeavesNoFamilyNotifiedEvent() {
        configuredWithHistory(planned(ConferenceFormat.CALL_FOR_PAPERS), confirmed(AttendanceBasis.TICKET_PURCHASED));
        willThrow(new IllegalStateException("Brevo returned 502")).given(brevo).send(any());

        assertThatThrownBy(this::notifyOfConference)
                .hasMessage("Brevo returned 502");

        verify(eventStore, never()).append(any(), any());
        verify(brevo, atLeastOnce()).send(any());
    }
}
