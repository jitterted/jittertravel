package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.CfpOpened;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.InvitedToSpeak;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.TalkSubmitted;
import dev.ted.jittertravel.domain.TalkWithdrawn;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ConferenceProjectorTest {

    // ALL ignores now; any instant works for those cases.
    private static final Instant NOW = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant CONFIRMED_ON = Instant.parse("2026-08-19T16:45:00Z");
    private static final Instant RECORDED_ON = Instant.parse("2026-08-22T10:15:00Z");
    // The test JVM is pinned to UTC (pom.xml), so fixtures name a venue zone explicitly —
    // otherwise "is it over?" would accidentally agree with the server and prove nothing.
    private static final ZoneId VENUE_ZONE = ZoneId.of("America/Los_Angeles");

    @Test
    void projectorCreatesViewFromEvents() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        Address address = new Address("123 Venue Street", "Venue City", "Venue State", "Venue Postal Code", "Venue Country", null);
        ConferencePlanned event = new ConferencePlanned(
                conferenceId,
                "Conference Name",
                zt(LocalDateTime.of(2026, 6, 1, 9, 0)),
                zt(LocalDateTime.of(2026, 6, 3, 17, 0)),
                "Venue",
                address
        );
        StoredEvent storedEvent = new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());

        projector.handle(Stream.of(storedEvent));

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW))
                .hasSize(1);
        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW).getFirst().conferenceId())
                .isEqualTo(conferenceId);
        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW).getFirst().name())
                .isEqualTo("Conference Name");
        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW).getFirst().city())
                .isEqualTo("Venue City");
    }

    @Test
    void projectedViewsAreSortedAscendingByStartDate() {
        ConferenceProjector projector = new ConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal Code", "Country", null);

        ConferencePlanned laterEvent = new ConferencePlanned(
                ConferenceId.random(),
                "Later Conference",
                zt(LocalDateTime.of(2026, 7, 1, 9, 0)),
                zt(LocalDateTime.of(2026, 7, 3, 17, 0)),
                "Later Venue",
                address
        );
        ConferencePlanned earlierEvent = new ConferencePlanned(
                ConferenceId.random(),
                "Earlier Conference",
                zt(LocalDateTime.of(2026, 6, 28, 9, 0)),
                zt(LocalDateTime.of(2026, 6, 30, 17, 0)),
                "Earlier Venue",
                address
        );

        projector.handle(Stream.of(
                new StoredEvent(1, laterEvent.getClass(), UUID.randomUUID(), Instant.now(), laterEvent, UUID.randomUUID()),
                new StoredEvent(2, earlierEvent.getClass(), UUID.randomUUID(), Instant.now(), earlierEvent, UUID.randomUUID())
        ));

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW))
                .hasSize(2)
                .extracting(ConferenceView::name)
                .containsExactly("Earlier Conference", "Later Conference");
    }

    @Test
    void futureFilterKeepsInProgressConferenceButDropsFinishedOne() {
        ConferenceProjector projector = new ConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal", "Country", null);
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 12, 0);
        // started yesterday, ends tomorrow -> still "upcoming" by endDate
        handle(projector, 1, "In Progress",
                now.minusDays(1), now.plusDays(1), address);
        // ended last week -> past
        handle(projector, 2, "Finished",
                now.minusDays(10), now.minusDays(8), address);

        // "Now" is a moment, read against the venue's own zone — not the server's.
        Instant nowInstant = now.atZone(VENUE_ZONE).toInstant();
        assertThat(projector.views(TimeView.FUTURE, DroppedView.HIDE, nowInstant))
                .extracting(ConferenceView::name)
                .containsExactly("In Progress");
        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, nowInstant))
                .extracting(ConferenceView::name)
                .containsExactlyInAnyOrder("In Progress", "Finished");
    }

    @Test
    void decliningAttendanceKeepsTheConferenceAsDropped() {
        ConferenceProjector projector = new ConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal", "Country", null);
        ConferenceId conferenceId = ConferenceId.random();
        ConferencePlanned planned = new ConferencePlanned(
                conferenceId, "Devoxx Morocco",
                zt(LocalDateTime.of(2026, 10, 7, 9, 0)), zt(LocalDateTime.of(2026, 10, 9, 17, 0)),
                "Venue", address);
        projector.handle(Stream.of(new StoredEvent(
                1, planned.getClass(), UUID.randomUUID(), Instant.now(), planned, UUID.randomUUID())));
        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW))
                .hasSize(1);

        ConferenceAttendanceDeclined declined = new ConferenceAttendanceDeclined(
                conferenceId, "Schedule clash", Instant.parse("2026-08-16T18:30:00Z"));
        projector.handle(Stream.of(new StoredEvent(
                2, declined.getClass(), UUID.randomUUID(), Instant.now(), declined, UUID.randomUUID())));

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW))
                .as("a declined conference is out of the default view of the list")
                .isEmpty();

        assertThat(projector.views(TimeView.ALL, DroppedView.SHOW, NOW))
                .as("but it is still there, and asking for dropped conferences finds it")
                .singleElement()
                .extracting(ConferenceView::commitment)
                .isEqualTo(AttendanceCommitment.NOT_GOING);
    }

    /**
     * The one place a decline behaves differently from an organizer cancellation. Ted's own "no" is
     * an answer worth keeping — next year's entry benefits from it — while a cancelled conference
     * is not a conference any more, so nothing is left to have a view of.
     */
    @Test
    void aConferenceCancelledByOrganizersIsGoneEvenWhenDroppedOnesAreAskedFor() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);

        ConferenceCancelled cancelled = new ConferenceCancelled(conferenceId, "Organizers pulled it");
        projector.handle(Stream.of(new StoredEvent(
                2, cancelled.getClass(), UUID.randomUUID(), Instant.now(), cancelled, UUID.randomUUID())));

        assertThat(projector.views(TimeView.ALL, DroppedView.SHOW, NOW))
                .isEmpty();
    }

    @Test
    void aPlannedConferenceStartsOutMerelyWatched() {
        ConferenceProjector projector = new ConferenceProjector();
        plan(projector, ConferenceId.random());

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW))
                .singleElement()
                .extracting(ConferenceView::commitment)
                .isEqualTo(AttendanceCommitment.WATCHING);
    }

    @Test
    void confirmingAttendanceTurnsTheViewIntoGoing() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);

        confirm(projector, conferenceId, AttendanceBasis.SPEAKING_ACCEPTED);

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW))
                .singleElement()
                .extracting(ConferenceView::commitment)
                .isEqualTo(AttendanceCommitment.GOING);
    }

    @Test
    void confirmingAttendanceLeavesTheRestOfTheViewAlone() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);
        ConferenceView before = projector.views(TimeView.ALL, DroppedView.HIDE, NOW).getFirst();

        confirm(projector, conferenceId, AttendanceBasis.SPEAKING_ACCEPTED);

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW).getFirst())
                .isEqualTo(new ConferenceView(
                        before.conferenceId(), before.name(), before.venueName(),
                        before.venueAddress(), before.startDate(), before.endDate(),
                        AttendanceCommitment.GOING, true, SpeakingStatus.NOT_SPEAKING,
                        null, "", before.format(), before.infoUrl()));
    }

    @Test
    void confirmingAttendanceForAnUnknownConferenceAddsNothing() {
        ConferenceProjector projector = new ConferenceProjector();

        confirm(projector, ConferenceId.random(), AttendanceBasis.TICKET_PURCHASED);

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW)).isEmpty();
    }

    @Test
    void aConfirmedConferenceCanStillBeDeclined() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);
        confirm(projector, conferenceId, AttendanceBasis.SPEAKING_ACCEPTED);

        ConferenceAttendanceDeclined declined = new ConferenceAttendanceDeclined(
                conferenceId, "Something came up", CONFIRMED_ON);
        projector.handle(Stream.of(new StoredEvent(
                3, declined.getClass(), UUID.randomUUID(), Instant.now(), declined, UUID.randomUUID())));

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW)).isEmpty();
    }

    @Test
    void aPlannedConferenceHasNoCfpDeadlineUntilOneIsRecorded() {
        ConferenceProjector projector = new ConferenceProjector();

        plan(projector, ConferenceId.random());

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW).getFirst().cfpClosesOn())
                .as("null means 'no CFP recorded', which is a different question from 'no CFP exists'")
                .isNull();
    }

    @Test
    void recordingACfpPutsItsClosingDeadlineOnTheView() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);

        openCfp(projector, conferenceId, LocalDateTime.of(2026, 9, 12, 23, 59));

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW).getFirst().cfpClosesOn())
                .isEqualTo(zt(LocalDateTime.of(2026, 9, 12, 23, 59)));
    }

    /** Organizers move CFP dates routinely, and re-recording is how an extension gets in. */
    @Test
    void recordingACfpAgainReplacesTheDeadline() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);
        openCfp(projector, conferenceId, LocalDateTime.of(2026, 9, 12, 23, 59));

        openCfp(projector, conferenceId, LocalDateTime.of(2026, 9, 26, 23, 59));

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW).getFirst().cfpClosesOn())
                .as("the last recorded deadline wins")
                .isEqualTo(zt(LocalDateTime.of(2026, 9, 26, 23, 59)));
    }

    /**
     * Recording a CFP and confirming attendance are independent facts about the same conference, so
     * neither may quietly reset the other — the case a fold that rebuilt the view from scratch would
     * get wrong.
     * <p>
     * <strong>Both orders, and that is the point.</strong> Testing one order only proves half of it:
     * whichever event is applied last restores the fields it owns, so a fold that clobbers the other
     * one still passes. Ted records these in either order — a CFP deadline noted months before he
     * commits, or a deadline added to a conference he is already going to.
     */
    @Test
    void aCfpDeadlineSurvivesAConfirmationThatFollowsIt() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);

        openCfp(projector, conferenceId, LocalDateTime.of(2026, 9, 12, 23, 59));
        confirm(projector, conferenceId, AttendanceBasis.SPEAKING_ACCEPTED);

        ConferenceView view = projector.views(TimeView.ALL, DroppedView.HIDE, NOW).getFirst();
        assertThat(view.cfpClosesOn())
                .as("confirming attendance must not clear the recorded CFP deadline")
                .isEqualTo(zt(LocalDateTime.of(2026, 9, 12, 23, 59)));
        assertThat(view.commitment()).isEqualTo(AttendanceCommitment.GOING);
        assertThat(view.speaking()).isTrue();
    }

    @Test
    void aConfirmationSurvivesACfpDeadlineRecordedAfterIt() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);

        confirm(projector, conferenceId, AttendanceBasis.SPEAKING_ACCEPTED);
        openCfp(projector, conferenceId, LocalDateTime.of(2026, 9, 12, 23, 59));

        ConferenceView view = projector.views(TimeView.ALL, DroppedView.HIDE, NOW).getFirst();
        assertThat(view.commitment())
                .as("recording a CFP deadline must not un-commit a conference Ted is going to")
                .isEqualTo(AttendanceCommitment.GOING);
        assertThat(view.speaking())
                .as("nor forget that he is speaking at it")
                .isTrue();
        assertThat(view.cfpClosesOn()).isEqualTo(zt(LocalDateTime.of(2026, 9, 12, 23, 59)));
    }

    @Test
    void aCfpForAnUnknownConferenceAddsNothing() {
        ConferenceProjector projector = new ConferenceProjector();

        openCfp(projector, ConferenceId.random(), LocalDateTime.of(2026, 9, 12, 23, 59));

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW)).isEmpty();
    }

    /**
     * The submission URL rides on the CFP, so it lands and is replaced with the deadline rather
     * than separately — recording the CFP again is how both are corrected.
     */
    @Test
    void theSubmissionUrlLandsWithTheDeadlineAndIsReplacedWithIt() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);

        openCfp(projector, conferenceId, LocalDateTime.of(2026, 9, 12, 23, 59),
                "https://sessionize.com/dev2next-2027/");

        assertThat(onlyView(projector).cfpSubmissionUrl())
                .isEqualTo("https://sessionize.com/dev2next-2027/");

        openCfp(projector, conferenceId, LocalDateTime.of(2026, 10, 3, 23, 59),
                "https://cfp.dev2next.com/");

        assertThat(onlyView(projector).cfpSubmissionUrl())
                .as("the last recorded CFP wins, URL and deadline together")
                .isEqualTo("https://cfp.dev2next.com/");
        assertThat(onlyView(projector).cfpClosesOn().localDateTime())
                .isEqualTo(LocalDateTime.of(2026, 10, 3, 23, 59));
    }

    /** A CFP recorded without a URL leaves the empty sentinel, never a null. */
    @Test
    void aCfpWithNoSubmissionUrlLeavesTheEmptyString() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);

        openCfp(projector, conferenceId, LocalDateTime.of(2026, 9, 12, 23, 59));

        assertThat(onlyView(projector).cfpSubmissionUrl()).isEmpty();
    }

    /**
     * The conference's own page comes off the plan and survives every later move, because it is a
     * property of the conference rather than a position on either axis.
     */
    @Test
    void theConferencesOwnPageComesOffThePlanAndSurvivesTheOtherEvents() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId, ConferenceFormat.CALL_FOR_PAPERS, "https://dev2next.com/");

        assertThat(onlyView(projector).infoUrl()).isEqualTo("https://dev2next.com/");

        confirm(projector, conferenceId, AttendanceBasis.TICKET_PURCHASED);

        assertThat(onlyView(projector).infoUrl())
                .as("confirming attendance says nothing about the conference's web page")
                .isEqualTo("https://dev2next.com/");
    }

    private static ConferenceView onlyView(ConferenceProjector projector) {
        List<ConferenceView> views = projector.views(TimeView.ALL, DroppedView.HIDE, NOW);
        assertThat(views).hasSize(1);
        return views.getFirst();
    }

    private static void openCfp(ConferenceProjector projector, ConferenceId conferenceId,
                                LocalDateTime closesOn) {
        openCfp(projector, conferenceId, closesOn, "");
    }

    private static void openCfp(ConferenceProjector projector, ConferenceId conferenceId,
                                LocalDateTime closesOn, String submissionUrl) {
        CfpOpened opened = new CfpOpened(conferenceId, zt(closesOn), submissionUrl);
        projector.handle(Stream.of(new StoredEvent(
                3, opened.getClass(), UUID.randomUUID(), Instant.now(), opened, UUID.randomUUID())));
    }

    private static void plan(ConferenceProjector projector, ConferenceId conferenceId) {
        plan(projector, conferenceId, ConferenceFormat.CALL_FOR_PAPERS);
    }

    private static void plan(ConferenceProjector projector, ConferenceId conferenceId,
                             ConferenceFormat format) {
        plan(projector, conferenceId, format, "");
    }

    private static void plan(ConferenceProjector projector, ConferenceId conferenceId,
                             ConferenceFormat format, String infoUrl) {
        ConferencePlanned planned = new ConferencePlanned(
                conferenceId, "dev2next",
                zt(LocalDateTime.of(2026, 9, 28, 9, 0)), zt(LocalDateTime.of(2026, 10, 1, 17, 0)),
                "Venue", new Address("Street", "Denver", "CO", "80202", "USA", null), format,
                infoUrl);
        projector.handle(Stream.of(new StoredEvent(
                1, planned.getClass(), UUID.randomUUID(), Instant.now(), planned, UUID.randomUUID())));
    }

    /**
     * The partition {@link AttendanceBasis}'s three values exist for: two speaking, one not. The
     * view carries the derived boolean and never the basis, so the accepted/invited distinction —
     * which is submission status — cannot be read back off {@code /conferences}.
     */
    @ParameterizedTest(name = "{0} means speaking = {1}")
    @MethodSource("basesAndWhetherTheyMeanSpeaking")
    void speakingIsDerivedFromTheBasisAndTheBasisItselfNeverReachesTheView(
            AttendanceBasis basis, boolean expectedSpeaking) {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);

        confirm(projector, conferenceId, basis);

        ConferenceView view = projector.views(TimeView.ALL, DroppedView.HIDE, NOW).getFirst();
        assertThat(view.speaking())
                .as("%s", basis)
                .isEqualTo(expectedSpeaking);
        assertThat(view.toString())
                .as("the basis is collapsed to a boolean, never carried")
                .doesNotContain(basis.name());
    }

    static Stream<Arguments> basesAndWhetherTheyMeanSpeaking() {
        return Stream.of(
                arguments(AttendanceBasis.SPEAKING_ACCEPTED, true),
                arguments(AttendanceBasis.SPEAKING_INVITED, true),
                arguments(AttendanceBasis.TICKET_PURCHASED, false));
    }

    @Test
    void aMerelyPlannedConferenceRecordsNoSpeakingEvidence() {
        ConferenceProjector projector = new ConferenceProjector();

        plan(projector, ConferenceId.random());

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW).getFirst().speaking())
                .as("planning a conference says nothing about whether Ted will speak at it")
                .isFalse();
    }

    /**
     * The auto-commit: an acceptance makes Ted GOING on its own, with no
     * {@link ConferenceAttendanceConfirmed} anywhere in the stream. Submitting the talk was
     * already the opt-in, so the acceptance completes a decision rather than posing a new one.
     */
    @Test
    void anAcceptedTalkCommitsAttendanceWithNoConfirmationEvent() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);
        talk(projector, 2, new TalkSubmitted(conferenceId, RECORDED_ON));

        talk(projector, 3, new TalkAccepted(conferenceId, RECORDED_ON));

        ConferenceView view = only(projector);
        assertThat(view.commitment()).isEqualTo(AttendanceCommitment.GOING);
        assertThat(view.speakingStatus()).isEqualTo(SpeakingStatus.ACCEPTED);
        assertThat(view.speaking())
                .as("accepted means speaking, with no basis to consult")
                .isTrue();
    }

    /**
     * An invitation is an offer, so it commits nothing on its own. That is the whole difference
     * from an acceptance, and it is why an unanswered invitation cannot reach the public calendar's
     * speaking badge.
     */
    @Test
    void anInvitationCommitsNothingUntilItIsAccepted() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);

        talk(projector, 2, new InvitedToSpeak(conferenceId, RECORDED_ON));

        assertThat(only(projector).commitment()).isEqualTo(AttendanceCommitment.WATCHING);
        assertThat(only(projector).speaking())
                .as("invited is not yet speaking — he has not said yes")
                .isFalse();

        confirm(projector, conferenceId, AttendanceBasis.SPEAKING_INVITED);

        assertThat(only(projector).commitment()).isEqualTo(AttendanceCommitment.GOING);
        assertThat(only(projector).speaking()).isTrue();
    }

    /**
     * Going to a conference he was invited to, on a bought ticket, is attending — not speaking.
     * The basis is what separates the two, which is why it has to be folded even though it never
     * reaches the view.
     */
    @Test
    void anInvitationTakenUpAsAPlainTicketIsNotSpeaking() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);
        talk(projector, 2, new InvitedToSpeak(conferenceId, RECORDED_ON));

        confirm(projector, conferenceId, AttendanceBasis.TICKET_PURCHASED);

        assertThat(only(projector).commitment()).isEqualTo(AttendanceCommitment.GOING);
        assertThat(only(projector).speaking()).isFalse();
    }

    /**
     * The auto-drop. Acceptance <em>gated</em> attendance at this conference (PLoP), so a rejection
     * takes the conference with it — there is no going anyway.
     */
    @Test
    void aRejectionDropsAConferenceWhereAcceptanceWasRequired() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId, ConferenceFormat.ACCEPTANCE_REQUIRED);
        talk(projector, 2, new TalkSubmitted(conferenceId, RECORDED_ON));

        talk(projector, 3, new TalkRejected(conferenceId, RECORDED_ON));

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW))
                .as("it leaves the default view of the list, like a decline")
                .isEmpty();
        assertThat(projector.views(TimeView.ALL, DroppedView.SHOW, NOW))
                .singleElement()
                .extracting(ConferenceView::commitment)
                .isEqualTo(AttendanceCommitment.NOT_GOING);
    }

    /**
     * The same event at a conference that does not require acceptance leaves it merely watched,
     * with a decision to make: go as an attendee, or drop it. This is the rejected-but-undecided
     * state the two-axis model exists to represent.
     */
    @Test
    void aRejectionLeavesACallForPapersConferenceStillWatched() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId, ConferenceFormat.CALL_FOR_PAPERS);
        talk(projector, 2, new TalkSubmitted(conferenceId, RECORDED_ON));

        talk(projector, 3, new TalkRejected(conferenceId, RECORDED_ON));

        assertThat(only(projector).commitment()).isEqualTo(AttendanceCommitment.WATCHING);
        assertThat(only(projector).speakingStatus()).isEqualTo(SpeakingStatus.REJECTED);
    }

    /**
     * Pulling a talk moves one axis only. Ted keeps his hotel and his flights — he is simply not
     * speaking any more.
     */
    @Test
    void withdrawingAnAcceptedTalkLeavesHimGoing() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);
        talk(projector, 2, new TalkSubmitted(conferenceId, RECORDED_ON));
        talk(projector, 3, new TalkAccepted(conferenceId, RECORDED_ON));

        talk(projector, 4, new TalkWithdrawn(conferenceId, RECORDED_ON));

        assertThat(only(projector).commitment())
                .as("withdrawing a talk says nothing about attending")
                .isEqualTo(AttendanceCommitment.GOING);
        assertThat(only(projector).speaking()).isFalse();
    }

    /**
     * The stream is authoritative and the basis is only the fallback: a conference backfilled as
     * "going because a talk was accepted" stops counting as speaking the moment the stream says the
     * talk was in fact turned down.
     */
    @Test
    void theSubmissionStreamOverridesTheBasisWhenTheyDisagree() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);
        confirm(projector, conferenceId, AttendanceBasis.SPEAKING_ACCEPTED);
        assertThat(only(projector).speaking()).isTrue();

        talk(projector, 3, new TalkSubmitted(conferenceId, RECORDED_ON));
        talk(projector, 4, new TalkRejected(conferenceId, RECORDED_ON));

        assertThat(only(projector).speaking())
                .as("the events are history; the basis is a manual annotation and loses")
                .isFalse();
    }

    /**
     * The count the dashboard's "Show dropped <em>n</em>" switch reads. It has to survive the very
     * filter that removes the rows it counts — the switch reports how many the page is holding
     * back, which is precisely what the page cannot show.
     */
    @Test
    void droppedCountCountsTheConferencesTheDefaultViewLeavesOut() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId going = ConferenceId.random();
        ConferenceId dropped = ConferenceId.random();
        plan(projector, going);
        plan(projector, dropped);
        decline(projector, dropped);

        assertThat(projector.views(TimeView.ALL, DroppedView.HIDE, NOW))
                .as("the dropped one is off the page")
                .hasSize(1);
        assertThat(projector.droppedCount(TimeView.ALL, NOW))
                .as("and the switch can still say how many are off it")
                .isEqualTo(1);
    }

    /**
     * Filtered by time, like the rows are: the switch says how many the <em>dropped</em> filter is
     * holding back, not how many exist — so a conference already past does not inflate it while the
     * page is showing upcoming ones.
     */
    @Test
    void droppedCountObeysTheTimeFilter() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        // dev2next runs 2026-09-28 to 2026-10-01, so this instant is well past it.
        Instant afterItEnded = Instant.parse("2027-01-01T00:00:00Z");
        plan(projector, conferenceId);
        decline(projector, conferenceId);

        assertThat(projector.droppedCount(TimeView.ALL, afterItEnded))
                .as("ALL counts it")
                .isEqualTo(1);
        assertThat(projector.droppedCount(TimeView.FUTURE, afterItEnded))
                .as("FUTURE does not: the page it would be on is not showing it either")
                .isZero();
    }

    private static void decline(ConferenceProjector projector, ConferenceId conferenceId) {
        ConferenceAttendanceDeclined declined = new ConferenceAttendanceDeclined(
                conferenceId, "Schedule clash", Instant.parse("2026-08-16T18:30:00Z"));
        projector.handle(Stream.of(new StoredEvent(
                5, declined.getClass(), UUID.randomUUID(), Instant.now(), declined, UUID.randomUUID())));
    }

    /** A talk event for a conference nobody planned changes nothing. */
    @Test
    void aTalkEventForAnUnknownConferenceAddsNothing() {
        ConferenceProjector projector = new ConferenceProjector();

        talk(projector, 1, new TalkSubmitted(ConferenceId.random(), RECORDED_ON));

        assertThat(projector.views(TimeView.ALL, DroppedView.SHOW, NOW)).isEmpty();
    }

    private static ConferenceView only(ConferenceProjector projector) {
        return projector.views(TimeView.ALL, DroppedView.SHOW, NOW).getFirst();
    }

    private static void talk(ConferenceProjector projector, long sequence, Event payload) {
        projector.handle(Stream.of(new StoredEvent(
                sequence, payload.getClass(), UUID.randomUUID(), Instant.now(), payload,
                UUID.randomUUID())));
    }

    private static void confirm(ConferenceProjector projector, ConferenceId conferenceId,
                                AttendanceBasis basis) {
        ConferenceAttendanceConfirmed confirmed =
                new ConferenceAttendanceConfirmed(conferenceId, basis, CONFIRMED_ON);
        projector.handle(Stream.of(new StoredEvent(
                2, confirmed.getClass(), UUID.randomUUID(), Instant.now(), confirmed, UUID.randomUUID())));
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, VENUE_ZONE);
    }

    private static void handle(ConferenceProjector projector, long seq, String name,
                               LocalDateTime start, LocalDateTime end, Address address) {
        ConferencePlanned event = new ConferencePlanned(
                ConferenceId.random(), name, zt(start), zt(end), "Venue", address);
        projector.handle(Stream.of(
                new StoredEvent(seq, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID())));
    }
}
