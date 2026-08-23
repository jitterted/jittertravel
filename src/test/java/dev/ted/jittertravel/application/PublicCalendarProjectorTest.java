package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.CfpOpened;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.InvitedToSpeak;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.TalkSubmitted;
import dev.ted.jittertravel.domain.TalkWithdrawn;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.GroundTransferCancelled;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What an anonymous visitor is shown, per kind — and, in
 * {@link #everyEntryCarriesOnlyPublishableDetails()}, the one invariant that replaced the
 * redactor's compile-time forcing function.
 * <p>
 * Its assertion is written so it does <strong>not</strong> need editing when a kind is added: it
 * states "whatever this projector emits carries {@link EntryDetails.Publishable} details", not a
 * list of permitted types. A test that must be edited on every change stops guarding, because
 * editing it is exactly what a leaking change would do.
 * <p>
 * The <strong>fixture</strong> is the other half, and it is not self-maintaining — which is why
 * {@link #theEveryKindFixtureCoversEveryKind()} exists to fail until a new kind is added to it. A
 * kind-agnostic assertion over a fixture that has quietly stopped covering the new kind guards
 * nothing at all.
 */
class PublicCalendarProjectorTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    private final PublicCalendarProjector projector = new PublicCalendarProjector();

    /**
     * The replacement for redaction rule 1's compile-time check. Inside the projector the same
     * claim is a compiler check — every entry is built through a helper taking a
     * {@code Publishable}, enforced by {@code PublicCalendarBuildsOnlyPublishableEntriesTest} —
     * and this is the runtime backstop.
     * <p>
     * The <em>assertion</em> names no types, so it does not need editing when a kind is added. The
     * <em>fixture</em> does, and {@link #theEveryKindFixtureCoversEveryKind()} below is what forces
     * that: without it this test would silently stop covering the new kind, which is precisely the
     * failure mode a replacement for a compile-time check must not have.
     */
    @Test
    void everyEntryCarriesOnlyPublishableDetails() {
        projector.handle(oneOfEveryKind().stream());

        assertThat(projector.entries())
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.details())
                        .as("a %s entry reached the public calendar", entry.kind())
                        .isInstanceOf(EntryDetails.Publishable.class));
    }

    /**
     * Adding an {@link EntryKind} breaks this until {@link #oneOfEveryKind()} feeds the projector
     * an event that produces one — which is the point. It is the difference between "the assertion
     * is kind-agnostic" and "the test actually covers every kind", and only the second is worth
     * anything as a security guard.
     * <p>
     * A kind that is deliberately never published would fail here too. That is the right place to
     * have the argument: exempt it explicitly, in this test, where the exemption is visible.
     */
    @Test
    void theEveryKindFixtureCoversEveryKind() {
        projector.handle(oneOfEveryKind().stream());

        assertThat(projector.entries())
                .as("every EntryKind must appear on the public calendar fixture, or the invariant "
                    + "test above is not exercising it")
                .extracting(CalendarEntry::kind)
                .contains(EntryKind.values());
    }

    @Test
    void aStayPublishesTheWordHotelAndTheCityButNeverTheName() {
        projector.handle(Stream.of(stored(new HotelBooked(HotelBookingId.random(), "Marriott Lone Tree",
                new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                zoned(LocalDateTime.of(2026, 7, 5, 15, 0), DENVER),
                zoned(LocalDateTime.of(2026, 7, 9, 11, 0), DENVER),
                BookingIntent.FINAL, "https://maps.google.com/marriott", null))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("Hotel");
        assertThat(entry.continuationTitle()).isEqualTo("Hotel cont'd");
        assertThat(entry.subTitle()).isEqualTo(List.of(new SubtitleLine.Text("Lone Tree, US")));
        assertThat(entry.toString())
                .as("neither the hotel's name nor its map link may appear anywhere on the entry")
                .doesNotContain("Marriott Lone Tree")
                .doesNotContain("maps.google.com");
    }

    @Test
    void aCancelledStayLeavesThePublicCalendar() {
        HotelBookingId bookingId = HotelBookingId.random();
        projector.handle(Stream.of(stored(new HotelBooked(bookingId, "Marriott Lone Tree",
                new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                zoned(LocalDateTime.of(2026, 7, 5, 15, 0), DENVER),
                zoned(LocalDateTime.of(2026, 7, 9, 11, 0), DENVER),
                BookingIntent.FINAL, "", null))));

        projector.handle(Stream.of(stored(new HotelBookingCancelled(bookingId, "plans changed"))));

        assertThat(projector.entries()).isEmpty();
    }

    @Test
    void aFlightPublishesItsRouteAndNoTimeAtAll() {
        projector.handle(Stream.of(stored(new FlightBooked(FlightId.random(), "United", "UA123",
                new AirportCode("SFO"), zoned(LocalDateTime.of(2026, 6, 17, 9, 0), DENVER),
                new AirportCode("JFK"), zoned(LocalDateTime.of(2026, 6, 17, 17, 0), DENVER)))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("✈️ SFO→JFK");
        assertThat(entry.subTitle())
                .as("a leg's only possible subtitle is when it runs, and that is private")
                .isNull();
        assertThat(entry.toString())
                .as("the flight number is a carrier identifier, never published")
                .doesNotContain("UA123");
    }

    @Test
    void anOvernightFlightStillSplitsIntoTheTwoDayColumnsItOccupies() {
        projector.handle(Stream.of(stored(new FlightBooked(FlightId.random(), "United", "UA59",
                new AirportCode("SFO"), zoned(LocalDateTime.of(2026, 6, 26, 13, 55), DENVER),
                new AirportCode("FRA"), zoned(LocalDateTime.of(2026, 6, 27, 9, 45), LONDON)))));

        assertThat(projector.entries())
                .as("which days a journey occupies is public; when it departs is not")
                .hasSize(2)
                .allSatisfy(entry -> assertThat(entry.subTitle()).isNull());
    }

    @Test
    void aTrainPublishesItsCitiesButNeverItsServiceId() {
        projector.handle(Stream.of(stored(new TrainBooked(TrainTripId.random(),
                new TrainStationAddress("Frankfurt Hbf", "Frankfurt", "DE", ""),
                zoned(LocalDateTime.of(2026, 6, 28, 9, 0), LONDON),
                new TrainStationAddress("Gare du Nord", "Paris", "FR", ""),
                zoned(LocalDateTime.of(2026, 6, 28, 14, 30), LONDON),
                "ICE 123"))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("🚄 Frankfurt → Paris");
        assertThat(entry.toString()).doesNotContain("ICE 123");
    }

    /**
     * The transfer whose owner title reads "DEN → Marriott Lone Tree". The public projector never
     * builds that title, so there is nothing to strip: it names the endpoints in their publishable
     * form, which for a hotel end is its city.
     */
    @Test
    void aTransferPublishesTheGenericWordAndCitiesNeverTheHotelItGoesTo() {
        projector.handle(Stream.of(stored(new GroundTransferPlanned(GroundTransferId.random(),
                "DEN", "", new Address("", "Denver", "CO", "", "US", null),
                "", "Marriott Lone Tree",
                new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                zoned(LocalDateTime.of(2026, 7, 5, 12, 0), DENVER),
                zoned(LocalDateTime.of(2026, 7, 5, 12, 45), DENVER)))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("🚕 Ground transfer");
        assertThat(entry.subTitle())
                .isEqualTo(List.of(new SubtitleLine.Text("DEN → Lone Tree, CO, US")));
        assertThat(entry.toString()).doesNotContain("Marriott Lone Tree");
    }

    @Test
    void aTransferOutOfAHotelReadsTheOtherWayRoundAndStillNamesNoHotel() {
        projector.handle(Stream.of(stored(new GroundTransferPlanned(GroundTransferId.random(),
                "", "Marriott Lone Tree",
                new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                "DEN", "", new Address("", "Denver", "CO", "", "US", null),
                zoned(LocalDateTime.of(2026, 7, 9, 11, 0), DENVER),
                zoned(LocalDateTime.of(2026, 7, 9, 11, 45), DENVER)))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.subTitle())
                .isEqualTo(List.of(new SubtitleLine.Text("Lone Tree, CO, US → DEN")));
        assertThat(entry.toString()).doesNotContain("Marriott Lone Tree");
    }

    @Test
    void aCancelledTransferLeavesThePublicCalendar() {
        GroundTransferId transferId = GroundTransferId.random();
        projector.handle(Stream.of(stored(new GroundTransferPlanned(transferId,
                "DEN", "", new Address("", "Denver", "CO", "", "US", null),
                "", "Marriott Lone Tree",
                new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                zoned(LocalDateTime.of(2026, 7, 5, 12, 0), DENVER),
                zoned(LocalDateTime.of(2026, 7, 5, 12, 45), DENVER)))));

        projector.handle(Stream.of(stored(new GroundTransferCancelled(transferId))));

        assertThat(projector.entries()).isEmpty();
    }

    /**
     * "Busy", the city, and the time in the event's own zone — built that way from the start rather
     * than reverse-engineered out of an owner subtitle, which is what the redactor had to do.
     */
    @Test
    void aPrivateEventPublishesBusyTheCityAndAFixedTimeOnly() {
        ZonedTimestamp start = zoned(LocalDateTime.of(2026, 6, 20, 19, 0), TORONTO);
        ZonedTimestamp end = zoned(LocalDateTime.of(2026, 6, 20, 22, 0), TORONTO);
        projector.handle(Stream.of(stored(new PrivateEventPlanned(PrivateEventId.random(),
                "Dinner with the Smiths", "Alo",
                new Address("5 Dine Way", "Toronto", "ON", "M5V", "Canada", null), start, end))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("Busy");
        assertThat(entry.subTitle()).isEqualTo(List.of(
                new SubtitleLine.FixedRange(start, end),
                new SubtitleLine.Text("Toronto, Canada")));
        assertThat(entry.toString())
                .doesNotContain("Dinner with the Smiths")
                .doesNotContain("Alo");
    }

    @Test
    void aGatheringIsPublishedInFullIncludingItsSpeakingMarkerAndInfoUrl() {
        projector.handle(Stream.of(stored(new GatheringPlanned(GatheringId.random(),
                "London Java Community", "Skills Matter",
                new Address("3 Meet Ln", "London", "", "EC1A 1BB", "GB", null),
                zoned(LocalDateTime.of(2026, 6, 18, 18, 0), LONDON),
                zoned(LocalDateTime.of(2026, 6, 18, 21, 0), LONDON),
                true, "https://meetup.com/ljc/events/123"))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("London Java Community");
        assertThat(entry.subTitle()).hasSize(3);
        assertThat(entry.details()).isEqualTo(
                new EntryDetails.PublicGathering("https://meetup.com/ljc/events/123", true));
    }

    @Test
    void aPlannedConferenceIsPublishedAsMerelyWatched() {
        projector.handle(Stream.of(stored(conferencePlanned(ConferenceId.random(), "J-Fall"))));

        assertThat(projector.entries().getFirst().details())
                .isEqualTo(new EntryDetails.PublicConference(AttendanceCommitment.WATCHING, false, null));
    }

    /**
     * A conference's own page is public by decision, like its venue and its times — CLAUDE.md lists
     * {@code infoUrl} among the things a conference publishes in full. It is read off the event by
     * name, which is the allow-list rule.
     */
    @Test
    void aConferencesOwnPageIsPublished() {
        projector.handle(Stream.of(stored(
                conferencePlanned(ConferenceId.random(), "J-Fall", "https://jfall.nl/"))));

        assertThat(projector.entries().getFirst().details())
                .isEqualTo(new EntryDetails.PublicConference(
                        AttendanceCommitment.WATCHING, false, "https://jfall.nl/"));
    }

    /**
     * The page survives a later move, because it is a property of the conference rather than a
     * position on either axis — and the rebuild in {@code moveTo} has no event to re-read it from.
     * A regression here would silently drop the link the first time Ted confirmed attendance.
     */
    @Test
    void theConferencesOwnPageSurvivesALaterMove() {
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(
                conferencePlanned(conferenceId, "J-Fall", "https://jfall.nl/"))));

        projector.handle(Stream.of(stored(new ConferenceAttendanceConfirmed(conferenceId,
                AttendanceBasis.TICKET_PURCHASED, RECORDED_ON))));

        assertThat(projector.entries().getFirst().details())
                .isEqualTo(new EntryDetails.PublicConference(
                        AttendanceCommitment.GOING, false, "https://jfall.nl/"));
    }

    /**
     * <strong>The CFP is not published, URL and all.</strong> A link to Ted's talk-submission page
     * says he is considering submitting somewhere, which is the pipeline the public calendar exists
     * to keep out — and unlike the deadline, a URL would look harmless in the markup. The
     * projector simply does not read {@code CfpOpened}, so this asserts on the whole entry rather
     * than on one field: nothing about the CFP reached it.
     */
    @Test
    void nothingAboutTheCfpReachesThePublicEntry() {
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(
                conferencePlanned(conferenceId, "J-Fall", "https://jfall.nl/"))));

        projector.handle(Stream.of(stored(new CfpOpened(conferenceId,
                zoned(LocalDateTime.of(2026, 9, 12, 23, 59), LONDON),
                "https://sessionize.com/jfall-2027/"))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.details())
                .as("the CFP moved nothing at all")
                .isEqualTo(new EntryDetails.PublicConference(
                        AttendanceCommitment.WATCHING, false, "https://jfall.nl/"));
        assertThat(entry.toString())
                .doesNotContain("sessionize")
                .doesNotContain("2026-09-12");
    }

    @Test
    void confirmingAttendanceTurnsThePublicEntryIntoGoing() {
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(conferencePlanned(conferenceId, "dev2next"))));

        projector.handle(Stream.of(stored(new ConferenceAttendanceConfirmed(conferenceId,
                AttendanceBasis.SPEAKING_ACCEPTED, Instant.parse("2026-05-01T00:00:00Z")))));

        assertThat(projector.entries().getFirst().details())
                .isEqualTo(new EntryDetails.PublicConference(AttendanceCommitment.GOING, true, null));
    }

    /**
     * The basis is submission status wearing a different hat: the projector never reads it, so
     * redaction rule 1 is satisfied structurally rather than by anything stripping it later.
     */
    @Test
    void theBasisForGoingNeverReachesThePublicEntry() {
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(conferencePlanned(conferenceId, "dev2next"))));

        projector.handle(Stream.of(stored(new ConferenceAttendanceConfirmed(conferenceId,
                AttendanceBasis.SPEAKING_ACCEPTED, Instant.parse("2026-05-01T00:00:00Z")))));

        assertThat(projector.entries().getFirst().toString())
                .doesNotContain("SPEAKING_ACCEPTED");
    }

    @Test
    void aDeclinedOrCancelledConferenceLeavesThePublicCalendarEntirely() {
        ConferenceId declined = ConferenceId.random();
        ConferenceId cancelled = ConferenceId.random();
        projector.handle(Stream.of(
                stored(conferencePlanned(declined, "Declined Conf")),
                stored(conferencePlanned(cancelled, "Cancelled Conf"))));

        projector.handle(Stream.of(
                stored(new ConferenceAttendanceDeclined(declined, "clash",
                        Instant.parse("2026-05-01T00:00:00Z"))),
                stored(new ConferenceCancelled(cancelled, "organizers pulled it"))));

        assertThat(projector.entries()).isEmpty();
    }

    /**
     * The acceptance commits attendance and makes the badge publishable in one move — there is no
     * confirmation event here at all.
     */
    @Test
    void anAcceptedTalkPublishesGoingAndSpeaking() {
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(conferencePlanned(conferenceId, "dev2next"))));

        projector.handle(Stream.of(
                stored(new TalkSubmitted(conferenceId, RECORDED_ON)),
                stored(new TalkAccepted(conferenceId, RECORDED_ON))));

        assertThat(projector.entries().getFirst().details())
                .isEqualTo(new EntryDetails.PublicConference(AttendanceCommitment.GOING, true, null));
    }

    /**
     * <strong>The gate.</strong> An unanswered invitation is speaking evidence, and publishing it
     * would tell a stranger Ted had been asked to speak somewhere he has not decided about. The
     * published entry is byte-identical to a conference nobody ever invited him to.
     */
    @Test
    void anUnansweredInvitationPublishesNothingAtAll() {
        ConferenceId invited = ConferenceId.random();
        ConferenceId untouched = ConferenceId.random();
        projector.handle(Stream.of(
                stored(conferencePlanned(invited, "J-Fall")),
                stored(conferencePlanned(untouched, "J-Fall"))));

        projector.handle(Stream.of(stored(new InvitedToSpeak(invited, RECORDED_ON))));

        assertThat(projector.entries().get(0).details())
                .as("an invitation Ted has not answered changes nothing an anonymous viewer sees")
                .isEqualTo(projector.entries().get(1).details());
        assertThat(projector.entries().getFirst().details())
                .isEqualTo(new EntryDetails.PublicConference(AttendanceCommitment.WATCHING, false, null));
    }

    /** Saying yes is what makes it publishable, and the basis is what says he said yes to speaking. */
    @Test
    void anAcceptedInvitationPublishesTheBadgeAndAPlainTicketDoesNot() {
        ConferenceId speaking = ConferenceId.random();
        ConferenceId attending = ConferenceId.random();
        projector.handle(Stream.of(
                stored(conferencePlanned(speaking, "J-Fall")),
                stored(conferencePlanned(attending, "J-Fall"))));

        projector.handle(Stream.of(
                stored(new InvitedToSpeak(speaking, RECORDED_ON)),
                stored(new ConferenceAttendanceConfirmed(speaking,
                        AttendanceBasis.SPEAKING_INVITED, RECORDED_ON)),
                stored(new InvitedToSpeak(attending, RECORDED_ON)),
                stored(new ConferenceAttendanceConfirmed(attending,
                        AttendanceBasis.TICKET_PURCHASED, RECORDED_ON))));

        // Order-independent: both conferences start at the same moment, so entries() has no
        // defined order between them — the claim is about the pair, not their positions.
        assertThat(projector.entries())
                .extracting(CalendarEntry::details)
                .as("going on a bought ticket after an invitation is attending, not speaking")
                .containsExactlyInAnyOrder(
                        new EntryDetails.PublicConference(AttendanceCommitment.GOING, true, null),
                        new EntryDetails.PublicConference(AttendanceCommitment.GOING, false, null));
    }

    /**
     * Submission status is OWNER-only in full: a talk out for review, and a talk turned down, both
     * publish exactly what a conference with no submission publishes.
     */
    @Test
    void aSubmissionAndARejectionAreBothInvisibleToAnAnonymousViewer() {
        ConferenceId submitted = ConferenceId.random();
        ConferenceId rejected = ConferenceId.random();
        ConferenceId untouched = ConferenceId.random();
        projector.handle(Stream.of(
                stored(conferencePlanned(submitted, "J-Fall")),
                stored(conferencePlanned(rejected, "J-Fall")),
                stored(conferencePlanned(untouched, "J-Fall"))));

        projector.handle(Stream.of(
                stored(new TalkSubmitted(submitted, RECORDED_ON)),
                stored(new TalkSubmitted(rejected, RECORDED_ON)),
                stored(new TalkRejected(rejected, RECORDED_ON))));

        assertThat(projector.entries())
                .extracting(CalendarEntry::details)
                .containsOnly(new EntryDetails.PublicConference(AttendanceCommitment.WATCHING, false, null));
    }

    /**
     * The auto-drop reaches the public calendar too: where acceptance was the way in, a rejection
     * takes the conference off the calendar entirely, exactly as a decline does.
     */
    @Test
    void aRejectionDropsAnAcceptanceRequiredConferenceFromThePublicCalendar() {
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(new ConferencePlanned(conferenceId, "PLoP",
                zoned(LocalDateTime.of(2026, 10, 12, 9, 0), LONDON),
                zoned(LocalDateTime.of(2026, 10, 15, 17, 0), LONDON),
                "Allerton House",
                new Address("1 Conf St", "Monticello", "", "61856", "USA", null),
                ConferenceFormat.ACCEPTANCE_REQUIRED))));

        projector.handle(Stream.of(
                stored(new TalkSubmitted(conferenceId, RECORDED_ON)),
                stored(new TalkRejected(conferenceId, RECORDED_ON))));

        assertThat(projector.entries()).isEmpty();
    }

    /** Pulling a talk moves one axis: Ted still goes, so the entry stays and the badge goes. */
    @Test
    void withdrawingAnAcceptedTalkKeepsTheEntryAndDropsTheBadge() {
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(conferencePlanned(conferenceId, "dev2next"))));

        projector.handle(Stream.of(
                stored(new TalkSubmitted(conferenceId, RECORDED_ON)),
                stored(new TalkAccepted(conferenceId, RECORDED_ON)),
                stored(new TalkWithdrawn(conferenceId, RECORDED_ON))));

        assertThat(projector.entries().getFirst().details())
                .isEqualTo(new EntryDetails.PublicConference(AttendanceCommitment.GOING, false, null));
    }

    private static final Instant RECORDED_ON = Instant.parse("2026-05-01T00:00:00Z");

    private static ConferencePlanned conferencePlanned(ConferenceId conferenceId, String name) {
        return conferencePlanned(conferenceId, name, "");
    }

    private static ConferencePlanned conferencePlanned(ConferenceId conferenceId, String name,
                                                       String infoUrl) {
        return new ConferencePlanned(conferenceId, name,
                zoned(LocalDateTime.of(2026, 11, 5, 9, 0), LONDON),
                zoned(LocalDateTime.of(2026, 11, 6, 17, 0), LONDON),
                "Grand Venue",
                new Address("1 Conf St", "Ede", "", "6710", "Netherlands", null),
                ConferenceFormat.CALL_FOR_PAPERS,
                infoUrl);
    }

    /** One event of every kind the public calendar can show. */
    private static List<StoredEvent> oneOfEveryKind() {
        return List.of(
                stored(conferencePlanned(ConferenceId.random(), "J-Fall")),
                stored(new GatheringPlanned(GatheringId.random(), "London Java Community",
                        "Skills Matter",
                        new Address("3 Meet Ln", "London", "", "EC1A 1BB", "GB", null),
                        zoned(LocalDateTime.of(2026, 6, 18, 18, 0), LONDON),
                        zoned(LocalDateTime.of(2026, 6, 18, 21, 0), LONDON),
                        true, "https://meetup.com/ljc/events/123")),
                stored(new PrivateEventPlanned(PrivateEventId.random(), "Dinner", "Alo",
                        new Address("5 Dine Way", "Toronto", "ON", "M5V", "Canada", null),
                        zoned(LocalDateTime.of(2026, 6, 20, 19, 0), TORONTO),
                        zoned(LocalDateTime.of(2026, 6, 20, 22, 0), TORONTO))),
                stored(new FlightBooked(FlightId.random(), "United", "UA123",
                        new AirportCode("SFO"), zoned(LocalDateTime.of(2026, 6, 17, 9, 0), DENVER),
                        new AirportCode("JFK"), zoned(LocalDateTime.of(2026, 6, 17, 17, 0), DENVER))),
                stored(new TrainBooked(TrainTripId.random(),
                        new TrainStationAddress("Frankfurt Hbf", "Frankfurt", "DE", ""),
                        zoned(LocalDateTime.of(2026, 6, 28, 9, 0), LONDON),
                        new TrainStationAddress("Gare du Nord", "Paris", "FR", ""),
                        zoned(LocalDateTime.of(2026, 6, 28, 14, 30), LONDON), "ICE 123")),
                stored(new HotelBooked(HotelBookingId.random(), "Marriott Lone Tree",
                        new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                        zoned(LocalDateTime.of(2026, 7, 5, 15, 0), DENVER),
                        zoned(LocalDateTime.of(2026, 7, 9, 11, 0), DENVER),
                        BookingIntent.FINAL, "", null)),
                stored(new GroundTransferPlanned(GroundTransferId.random(),
                        "DEN", "", new Address("", "Denver", "CO", "", "US", null),
                        "", "Marriott Lone Tree",
                        new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                        zoned(LocalDateTime.of(2026, 7, 5, 12, 0), DENVER),
                        zoned(LocalDateTime.of(2026, 7, 5, 12, 45), DENVER))));
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }

    private static ZonedTimestamp zoned(LocalDateTime local, ZoneId zone) {
        return ZonedTimestamp.fromLocal(local, zone);
    }
}
