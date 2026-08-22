package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.CfpOpened;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ConferenceProjectorTest {

    // ALL ignores now; any instant works for those cases.
    private static final Instant NOW = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant CONFIRMED_ON = Instant.parse("2026-08-19T16:45:00Z");
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

        assertThat(projector.views(TimeView.ALL, NOW))
                .hasSize(1);
        assertThat(projector.views(TimeView.ALL, NOW).getFirst().conferenceId())
                .isEqualTo(conferenceId);
        assertThat(projector.views(TimeView.ALL, NOW).getFirst().name())
                .isEqualTo("Conference Name");
        assertThat(projector.views(TimeView.ALL, NOW).getFirst().city())
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

        assertThat(projector.views(TimeView.ALL, NOW))
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
        assertThat(projector.views(TimeView.FUTURE, nowInstant))
                .extracting(ConferenceView::name)
                .containsExactly("In Progress");
        assertThat(projector.views(TimeView.ALL, nowInstant))
                .extracting(ConferenceView::name)
                .containsExactlyInAnyOrder("In Progress", "Finished");
    }

    @Test
    void decliningAttendanceRemovesTheConferenceFromTheList() {
        ConferenceProjector projector = new ConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal", "Country", null);
        ConferenceId conferenceId = ConferenceId.random();
        ConferencePlanned planned = new ConferencePlanned(
                conferenceId, "Devoxx Morocco",
                zt(LocalDateTime.of(2026, 10, 7, 9, 0)), zt(LocalDateTime.of(2026, 10, 9, 17, 0)),
                "Venue", address);
        projector.handle(Stream.of(new StoredEvent(
                1, planned.getClass(), UUID.randomUUID(), Instant.now(), planned, UUID.randomUUID())));
        assertThat(projector.views(TimeView.ALL, NOW))
                .hasSize(1);

        ConferenceAttendanceDeclined declined = new ConferenceAttendanceDeclined(
                conferenceId, "Schedule clash", Instant.parse("2026-08-16T18:30:00Z"));
        projector.handle(Stream.of(new StoredEvent(
                2, declined.getClass(), UUID.randomUUID(), Instant.now(), declined, UUID.randomUUID())));

        assertThat(projector.views(TimeView.ALL, NOW))
                .as("a declined conference leaves the conferences list, like a cancelled one")
                .isEmpty();
    }

    @Test
    void aPlannedConferenceStartsOutMerelyWatched() {
        ConferenceProjector projector = new ConferenceProjector();
        plan(projector, ConferenceId.random());

        assertThat(projector.views(TimeView.ALL, NOW))
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

        assertThat(projector.views(TimeView.ALL, NOW))
                .singleElement()
                .extracting(ConferenceView::commitment)
                .isEqualTo(AttendanceCommitment.GOING);
    }

    @Test
    void confirmingAttendanceLeavesTheRestOfTheViewAlone() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);
        ConferenceView before = projector.views(TimeView.ALL, NOW).getFirst();

        confirm(projector, conferenceId, AttendanceBasis.SPEAKING_ACCEPTED);

        assertThat(projector.views(TimeView.ALL, NOW).getFirst())
                .isEqualTo(new ConferenceView(
                        before.conferenceId(), before.name(), before.venueName(),
                        before.venueAddress(), before.startDate(), before.endDate(),
                        AttendanceCommitment.GOING, true, null));
    }

    @Test
    void confirmingAttendanceForAnUnknownConferenceAddsNothing() {
        ConferenceProjector projector = new ConferenceProjector();

        confirm(projector, ConferenceId.random(), AttendanceBasis.TICKET_PURCHASED);

        assertThat(projector.views(TimeView.ALL, NOW)).isEmpty();
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

        assertThat(projector.views(TimeView.ALL, NOW)).isEmpty();
    }

    @Test
    void aPlannedConferenceHasNoCfpDeadlineUntilOneIsRecorded() {
        ConferenceProjector projector = new ConferenceProjector();

        plan(projector, ConferenceId.random());

        assertThat(projector.views(TimeView.ALL, NOW).getFirst().cfpClosesOn())
                .as("null means 'no CFP recorded', which is a different question from 'no CFP exists'")
                .isNull();
    }

    @Test
    void recordingACfpPutsItsClosingDeadlineOnTheView() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        plan(projector, conferenceId);

        openCfp(projector, conferenceId, LocalDateTime.of(2026, 9, 12, 23, 59));

        assertThat(projector.views(TimeView.ALL, NOW).getFirst().cfpClosesOn())
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

        assertThat(projector.views(TimeView.ALL, NOW).getFirst().cfpClosesOn())
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

        ConferenceView view = projector.views(TimeView.ALL, NOW).getFirst();
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

        ConferenceView view = projector.views(TimeView.ALL, NOW).getFirst();
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

        assertThat(projector.views(TimeView.ALL, NOW)).isEmpty();
    }

    private static void openCfp(ConferenceProjector projector, ConferenceId conferenceId,
                                LocalDateTime closesOn) {
        CfpOpened opened = new CfpOpened(conferenceId, zt(closesOn));
        projector.handle(Stream.of(new StoredEvent(
                3, opened.getClass(), UUID.randomUUID(), Instant.now(), opened, UUID.randomUUID())));
    }

    private static void plan(ConferenceProjector projector, ConferenceId conferenceId) {
        ConferencePlanned planned = new ConferencePlanned(
                conferenceId, "dev2next",
                zt(LocalDateTime.of(2026, 9, 28, 9, 0)), zt(LocalDateTime.of(2026, 10, 1, 17, 0)),
                "Venue", new Address("Street", "Denver", "CO", "80202", "USA", null));
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

        ConferenceView view = projector.views(TimeView.ALL, NOW).getFirst();
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

        assertThat(projector.views(TimeView.ALL, NOW).getFirst().speaking())
                .as("planning a conference says nothing about whether Ted will speak at it")
                .isFalse();
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
