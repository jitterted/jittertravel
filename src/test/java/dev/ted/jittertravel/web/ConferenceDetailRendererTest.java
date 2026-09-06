package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ConferenceDetailView;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The page is laid out by volatility (Ted, 2026-09-06): a live column of three tracks — attendance,
 * talk, CFP — each carrying its own moves, beside a quiet rail of when and where. These tests are
 * grouped the same way.
 */
class ConferenceDetailRendererTest {

    private static final ConferenceId CONFERENCE_ID =
            ConferenceId.of(UUID.fromString("11111111-2222-3333-4444-555555555555"));
    private static final String BASE = "/conferences/11111111-2222-3333-4444-555555555555";
    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    /** Well before the conference and before every CFP deadline in these fixtures. */
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    @Test
    void thePageIsTitledAfterTheConferenceItIsAbout() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .contains("<title>J-Fall</title>")
                .contains("<h1>J-Fall</h1>");
    }

    @Test
    void theOwnerNavIsTheOneOnThisPage() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .contains("<a href=\"/conferences\">Conferences</a>")
                .contains("<a href=\"/schedule-problems\">Schedule Problems</a>");
    }

    // --- The header ---

    /**
     * The commitment chip is <strong>not</strong> up here (Ted, 2026-09-06): the attendance track
     * says "Going" two lines below, and saying it twice is what made the old header read as
     * decoration. Asserted on the class rather than the word, because the word legitimately appears
     * in the track.
     */
    @Test
    void theHeaderDoesNotRepeatTheCommitmentTheAttendanceTrackAlreadySays() {
        String html = ConferenceDetailRenderer.render(
                conference().going(AttendanceBasis.TICKET_PURCHASED).build(), NOW);

        assertThat(html)
                .doesNotContain("conf-commitment")
                .as("it is said once, on the track whose state it is")
                .contains("<div class=\"conf-track-state\">Going</div>");
    }

    /**
     * The speaking badge stays, precisely because it is <em>not</em> restated: {@code speaking()} is
     * derived, so it can be true while the talk track reads "Invited to speak" or even "Nothing
     * submitted".
     */
    @Test
    void theSpeakerBadgeIsWornOnlyWhenTedSpeaks() {
        String speaking = ConferenceDetailRenderer.render(
                conference().going(null).speaking(SpeakingStatus.ACCEPTED, true).build(), NOW);
        String notSpeaking = ConferenceDetailRenderer.render(
                conference().going(AttendanceBasis.TICKET_PURCHASED).build(), NOW);

        assertThat(speaking)
                .contains("<span class=\"conf-speaker\" title=\"Ted is speaking at this one\">"
                          + "Speaker</span>");
        assertThat(notSpeaking)
                .doesNotContain("<span class=\"conf-speaker\"");
    }

    /**
     * ACCEPTANCE_REQUIRED is a fact nothing else on the page states until a rejection makes it
     * visible, and by then it is too late to be useful — so the kind line survives the redesign.
     */
    @Test
    void theKindLineNamesTheFormatBeforeAnOutcomeMakesItMatter() {
        String html = ConferenceDetailRenderer.render(
                conference().withFormat(ConferenceFormat.ACCEPTANCE_REQUIRED).build(), NOW);

        assertThat(html)
                .contains("<p class=\"conf-detail-kind\">Conference · "
                          + ConferenceFormat.ACCEPTANCE_REQUIRED.label() + "</p>");
    }

    // --- The layout itself ---

    /**
     * Two <strong>fixed</strong> tracks, not {@code auto-fit}. The old grid reflowed 3 → 2 → 1
     * columns, so a panel sat somewhere different on the iPad than on the desktop; the pair of
     * assertions is what makes this precise, since the bug would be the old declaration still being
     * there.
     */
    @Test
    void theGridIsTwoFixedTracksSoNothingMovesBetweenDevices() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .contains("grid-template-columns: 2fr 1fr;")
                .doesNotContain("repeat(auto-fit, minmax(260px, 1fr))");
    }

    /**
     * The three tracks are always all present and always in this order, whatever state the
     * conference is in — that is what makes a row findable without hunting. Asserted as positions
     * rather than as three separate contains, which would pass in any order.
     */
    @Test
    void theThreeTracksAreAlwaysInTheSameOrder() {
        String html = ConferenceDetailRenderer.render(
                conference().dropped().withFormat(ConferenceFormat.OPEN_SPACE).build(), NOW);

        assertThat(html.indexOf("<h2>Attendance</h2>"))
                .as("attendance comes first")
                .isPositive()
                .isLessThan(html.indexOf("<h2>Talk</h2>"));
        assertThat(html.indexOf("<h2>Talk</h2>"))
                .isLessThan(html.indexOf("<h2>Call for papers</h2>"));
    }

    // --- The fact rail ---

    @Test
    void bothEndsAreShownWithTheirClockTimeAndTheVenueZone() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .as("the calendar and the dashboard show days; this is the page with the times")
                .contains(">Thu, Nov 5, 2026 at 9:00 AM</time>")
                .contains(">Sat, Nov 7 at 6:00 PM</time>")
                .contains("3 days · times in Europe/Amsterdam");
    }

    @Test
    void aOneDayConferenceCountsOneDay() {
        ConferenceDetailView conference = conference()
                .running("2026-11-05T09:00", "2026-11-05T18:00")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html).contains("1 day · times in Europe/Amsterdam");
    }

    @Test
    void theVenueItsStreetAndItsCityAreAllShown() {
        ConferenceDetailView conference = conference()
                .at("Reehorst", new Address("1 Conf St", "Ede", "", "6710", "Netherlands", null))
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<div class=\"conf-rail-lead\">Reehorst</div>")
                .contains("<p class=\"conf-rail-line\">1 Conf St</p>")
                .contains("<p class=\"conf-rail-line\">Ede, Netherlands</p>");
    }

    @Test
    void aMissingCountryLeavesTheCityStandingAlone() {
        ConferenceDetailView conference = conference()
                .at("Reehorst", new Address("1 Conf St", "Ede", "", "6710", "", null))
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<p class=\"conf-rail-line\">Ede</p>")
                .doesNotContain("Ede,");
    }

    /**
     * Its own rail block rather than a line under the address: it is the only outbound link on the
     * page, and burying it under a street is how it went unfound on the dashboard.
     */
    @Test
    void theConferencesOwnPageGetsItsOwnRailBlock() {
        ConferenceDetailView conference = conference()
                .withInfoUrl("https://jfall.nl")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<h2>Elsewhere</h2>")
                .contains("<a class=\"conf-panel-link\" title=\"Open the conference&#x27;s own page\" "
                          + "target=\"_blank\" rel=\"noopener\" href=\"https://jfall.nl\">"
                          + "Conference website</a>");
    }

    @Test
    void aConferenceWithNoPageOfItsOwnGetsNoElsewhereBlock() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .doesNotContain("<h2>Elsewhere</h2>")
                .doesNotContain(">Conference website</a>");
    }

    // --- The attendance track ---

    @Test
    void aTicketBoughtIsSaidOutLoud() {
        String html = ConferenceDetailRenderer.render(
                conference().going(AttendanceBasis.TICKET_PURCHASED).build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-track-state\">Going</div>")
                .contains("<p class=\"conf-track-note\">You bought a ticket.</p>");
    }

    @Test
    void anAcceptedInvitationIsSaidOutLoud() {
        ConferenceDetailView conference = conference()
                .going(AttendanceBasis.SPEAKING_INVITED)
                .speaking(SpeakingStatus.INVITED, true)
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<p class=\"conf-track-note\">You accepted an invitation to speak.</p>");
    }

    /**
     * An acceptance commits attendance on its own, writing no confirmation — so a conference can be
     * GOING with no basis, and this is the only sentence that explains it.
     */
    @Test
    void anAcceptedTalkExplainsTheCommitmentWithNoBasisRecorded() {
        ConferenceDetailView conference = conference()
                .going(null)
                .speaking(SpeakingStatus.ACCEPTED, true)
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<p class=\"conf-track-note\">"
                          + "A submitted talk was accepted, which is what committed you.</p>");
    }

    /**
     * The recorded basis wins over the acceptance, which is the opposite precedence to
     * {@code speaking()} and deliberate: on a ticket bought <em>before</em> a talk was accepted, the
     * acceptance is not what committed him.
     */
    @Test
    void aTicketBoughtBeforeATalkWasAcceptedIsStillTheReasonHeIsGoing() {
        ConferenceDetailView conference = conference()
                .going(AttendanceBasis.TICKET_PURCHASED)
                .speaking(SpeakingStatus.ACCEPTED, true)
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<p class=\"conf-track-note\">You bought a ticket.</p>")
                .doesNotContain("which is what committed you");
    }

    /**
     * An unexplained state word was what the old panel gave a watched conference, and it read as
     * missing data rather than as "nothing has happened yet".
     */
    @Test
    void aConferenceMerelyWatchedSaysSoRatherThanStandingEmpty() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-track-state\">Maybe</div>")
                .contains("<p class=\"conf-track-note\">Not decided yet.</p>")
                .doesNotContain("You bought a ticket.");
    }

    /**
     * Saying yes to an invitation is an <em>attendance</em> move — it writes
     * {@code ConferenceAttendanceConfirmed} — so the attendance track is where it is offered, and
     * the track's own sentence names the offer rather than leaving a bare "Maybe" beside it.
     */
    @Test
    void anOpenInvitationIsNamedOnTheTrackThatAnswersIt() {
        String html = ConferenceDetailRenderer.render(
                conference().speaking(SpeakingStatus.INVITED, false).build(), NOW);

        assertThat(html)
                .contains("<p class=\"conf-track-note\">"
                          + "You have been invited to speak. Saying yes is what commits you.</p>");
        assertThat(html.indexOf("href=\"" + BASE + "/confirm?basis=SPEAKING_INVITED\""))
                .as("offered on the attendance track, above the talk — not beside the offer")
                .isBetween(0, html.indexOf("<h2>Talk</h2>"));
    }

    /**
     * The reason {@code ConferenceProjector.Tracked} keeps the basis through a decline rather than
     * clearing it, and the reason a dropped conference has a detail page at all
     * ({@code ConferenceDetailAndChangePlan.md} Q3). Past tense: a decline is a later fact about the
     * same conference, not an erasure of the earlier one.
     */
    @Test
    void aDroppedConferenceStillSaysWhyHeWasGoing() {
        String html = ConferenceDetailRenderer.render(
                conference().going(AttendanceBasis.TICKET_PURCHASED).dropped().build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-track-state\">Not going</div>")
                .contains("<p class=\"conf-track-note\">You had bought a ticket.</p>")
                .as("the reason is stated once, in the tense that is true now")
                .doesNotContain("You bought a ticket.");
    }

    @Test
    void aConferenceDroppedBeforeHeEverCommittedGivesNoReason() {
        String html = ConferenceDetailRenderer.render(conference().dropped().build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-track-state\">Not going</div>")
                .doesNotContain("You had bought a ticket.")
                .doesNotContain("You were going");
    }

    // --- Colour is semantic: amber waiting, green settled, nothing when nothing is happening ---

    /**
     * The whole colour vocabulary, on the state that exercises all three at once: going is settled,
     * a submitted talk waits on the organizers, and a closed CFP is over.
     */
    @Test
    void eachTrackWearsTheEdgeItsStateEarns() {
        ConferenceDetailView conference = conference()
                .going(AttendanceBasis.TICKET_PURCHASED)
                .speaking(SpeakingStatus.SUBMITTED, false)
                .withCfp("2026-08-01T23:59", "")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .as("going is settled")
                .contains("<div class=\"conf-track conf-track--settled\"><h2>Attendance</h2>")
                .as("the organizers are holding the talk")
                .contains("<div class=\"conf-track conf-track--waiting\"><h2>Talk</h2>")
                .as("a closed CFP is not waiting on anyone")
                .contains("<div class=\"conf-track\"><h2>Call for papers</h2>");
    }

    /** Nothing is pending on a conference that is over, so nothing is marked. */
    @Test
    void aDroppedConferenceWearsNoColourAtAll() {
        String html = ConferenceDetailRenderer.render(conference().dropped().build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-track\"><h2>Attendance</h2>")
                .as("the class names also live in the stylesheet, so assert the elements")
                .doesNotContain("<div class=\"conf-track conf-track--settled\">")
                .doesNotContain("<div class=\"conf-track conf-track--waiting\">");
    }

    // --- The CFP track: four absences, four sentences ---

    /**
     * The pair that is easiest to get wrong, and the reason this track exists: {@code null} means
     * only "not recorded", never "no CFP exists". One is a task for Ted; the other is nothing at
     * all, and they must not read alike.
     */
    @Test
    void anOpenSpaceConferenceHasNoCallForPapersAtAll() {
        ConferenceDetailView conference = conference()
                .withFormat(ConferenceFormat.OPEN_SPACE)
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<div class=\"conf-track-state\">None</div>")
                .contains("<p class=\"conf-track-note\">Sessions are chosen on the day.</p>")
                .as("nothing to record, so no way in to the CFP form")
                .doesNotContain("href=\"" + BASE + "/cfp\"");
    }

    /**
     * A deadline recorded before the conference was marked open-space — the pending SoCraTes/PLoP
     * re-marking. {@code CfpDeadlineSource} does not filter by format, so that row is still putting
     * alarms on Ted's phone; printing "None" over the top of it would hide something live. No link,
     * because {@code OpenCfpCommand} refuses a CFP on an open space.
     */
    @Test
    void anOpenSpaceThatStillHasADeadlineOnRecordSaysSo() {
        ConferenceDetailView conference = conference()
                .withFormat(ConferenceFormat.OPEN_SPACE)
                .withCfp("2026-09-12T23:59", "")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<span>Sessions are chosen on the day, "
                          + "but a deadline is still recorded for </span>")
                .contains(">Sat, Sep 12, 2026 at 11:59 PM (CEST)</time>")
                .contains("<p class=\"conf-track-note\">"
                          + "It is still setting a reminder, and there is no way to clear it yet.</p>")
                .as("the domain refuses a CFP on an open space, so there is nowhere to send him")
                .doesNotContain("href=\"" + BASE + "/cfp\"");
    }

    @Test
    void aCfpNobodyHasRecordedYetAsksTedToGoAndFindIt() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-track-state\">Not recorded</div>")
                .contains("<p class=\"conf-track-note\">"
                          + "Find the closing date and record it, so a reminder can be set.</p>")
                .contains("<a class=\"conf-panel-link\" href=\"" + BASE + "/cfp\">"
                          + "Record when this CFP closes</a>");
    }

    /**
     * <strong>The countdown is the state line and the timestamp is the note</strong> (Ted,
     * 2026-09-06). "Closes in 8 days" is what he acts on; the exact deadline is what he checks
     * afterwards. It shipped the other way round — the reference value in the emphasised slot, the
     * answer in the muted one — so both halves are asserted, in their slots.
     */
    @Test
    void anOpenCfpLeadsWithHowLongIsLeftAndKeepsTheDeadlineUnderneath() {
        ConferenceDetailView conference = conference()
                .withCfp("2026-09-12T23:59", "")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<div class=\"conf-track-state\">Closes in 8 days</div>")
                .contains("<p class=\"conf-track-note\"><span>Open until </span>")
                .contains(">Sat, Sep 12, 2026 at 11:59 PM (CEST)</time>.</p>")
                .as("the deadline is no longer the emphasised line")
                .doesNotContain("<div class=\"conf-track-state\"><span>Open until </span>");
    }

    /**
     * <strong>The zone is resolved against the instant, not the zone id</strong> — the same
     * Amsterdam conference reads CEST for a September deadline and CET for a January one. Asserted
     * as the pair, because a hard-coded string would pass either half alone.
     * <p>
     * It is the only time on this page that names its zone: the conference's own start and end are
     * "when am I there", and the rail says which zone once beneath them, while a deadline is a
     * cutoff someone else set and Ted is routinely not in its zone when he checks whether he still
     * has time.
     */
    @Test
    void aDeadlineNamesTheZoneItActuallyFallsIn() {
        String summer = ConferenceDetailRenderer.render(
                conference().withCfp("2026-09-12T23:59", "").build(), NOW);
        String winter = ConferenceDetailRenderer.render(
                conference().withCfp("2027-01-15T23:59", "").build(), NOW);

        assertThat(summer).contains(">Sat, Sep 12, 2026 at 11:59 PM (CEST)</time>");
        assertThat(winter).contains(">Fri, Jan 15, 2027 at 11:59 PM (CET)</time>");
        assertThat(summer)
                .as("the conference's own times are not restated with a zone; the rail says it once")
                .contains(">Thu, Nov 5, 2026 at 9:00 AM</time>")
                .contains("3 days · times in Europe/Amsterdam");
    }

    @Test
    void aDeadlineTomorrowSaysTomorrowRatherThanOneDay() {
        ConferenceDetailView conference = conference()
                .withCfp("2026-09-05T23:59", "")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html).contains("<div class=\"conf-track-state\">Closes tomorrow</div>");
    }

    /** Counted in venue-local days, so a deadline later today is "today" and not "in 0 days". */
    @Test
    void aDeadlineLaterTodaySaysToday() {
        ConferenceDetailView conference = conference()
                .withCfp("2026-09-04T23:59", "")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html).contains("<div class=\"conf-track-state\">Closes today</div>");
    }

    /**
     * A closed CFP has no countdown to promote, so it leads with the one word and keeps the
     * timestamp in the note — which is what keeps the column scannable, every state line being a
     * short phrase beside "Going" and "Submitted".
     */
    @Test
    void aClosedCfpSaysClosedAndOffersNoWayToSubmit() {
        ConferenceDetailView conference = conference()
                .withCfp("2026-08-01T23:59", "https://sessionize.com/jfall/")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<div class=\"conf-track-state\">Closed</div>")
                .contains("<p class=\"conf-track-note\"><span>Was open until </span>")
                .contains(">Sat, Aug 1, 2026 at 11:59 PM (CEST)</time>.</p>")
                .doesNotContain(">Submit a talk</a>")
                .as("re-recording a deadline is still legal, and is how an extension gets in")
                .contains("href=\"" + BASE + "/cfp\"");
    }

    /**
     * A countdown to a deadline he cannot use is noise: he is not going, so how long is left is not
     * a fact about anything he could do with the time. The date stays as a record, and it leads,
     * because it is the only thing left to say.
     */
    @Test
    void aDroppedConferencesDeadlineIsARecordRatherThanACountdown() {
        ConferenceDetailView conference = conference()
                .dropped()
                .withCfp("2026-09-12T23:59", "")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<div class=\"conf-track-state\"><span>Open until </span>")
                .doesNotContain("Closes in 8 days")
                .as("nothing is waiting on anyone, so nothing is marked")
                .doesNotContain("<div class=\"conf-track conf-track--waiting\">");
    }

    @Test
    void anOpenCfpWithASubmissionPageLinksToIt() {
        ConferenceDetailView conference = conference()
                .withCfp("2026-09-12T23:59", "https://sessionize.com/jfall/")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<a class=\"conf-panel-link\" title=\"Open the CFP&#x27;s submission page\" "
                          + "target=\"_blank\" rel=\"noopener\" href=\"https://sessionize.com/jfall/\">"
                          + "Submit a talk</a>");
    }

    /** Offering it after they said no would be the page arguing with itself. */
    @Test
    void aRejectedTalkTakesTheSubmissionLinkWithIt() {
        ConferenceDetailView conference = conference()
                .speaking(SpeakingStatus.REJECTED, false)
                .withCfp("2026-09-12T23:59", "https://sessionize.com/jfall/")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html).doesNotContain(">Submit a talk</a>");
    }

    /**
     * The domain refuses to record a CFP against a conference Ted declined, so a link there would
     * lead somewhere that says no. The deadline itself stays, as a record.
     */
    @Test
    void aDroppedConferenceKeepsItsDeadlineButOffersNoCfpLinks() {
        ConferenceDetailView conference = conference()
                .dropped()
                .withCfp("2026-09-12T23:59", "https://sessionize.com/jfall/")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains(">Sat, Sep 12, 2026 at 11:59 PM (CEST)</time>")
                .doesNotContain("href=\"" + BASE + "/cfp\"")
                .doesNotContain(">Submit a talk</a>");
    }

    /**
     * The prompt and the link are one thing, and they leave together. Asking Ted to go and find a
     * closing date while withholding the only page that would accept it is the page asking for work
     * it has already decided is impossible.
     */
    @Test
    void aDroppedConferenceWithNoCfpDoesNotAskTedToGoAndFindOne() {
        String html = ConferenceDetailRenderer.render(conference().dropped().build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-track-state\">Not recorded</div>")
                .contains("<p class=\"conf-track-note\">"
                          + "A CFP cannot be recorded for a conference you are not going to.</p>")
                .doesNotContain("Find the closing date and record it")
                .doesNotContain("href=\"" + BASE + "/cfp\"");
    }

    // --- The talk track ---

    @Test
    void aSubmittedTalkSaysWhoHoldsItNow() {
        String html = ConferenceDetailRenderer.render(
                conference().speaking(SpeakingStatus.SUBMITTED, false).build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-track-state\">Submitted</div>")
                .contains("<p class=\"conf-track-note\">"
                          + "Waiting to hear. The organizers hold this one.</p>");
    }

    /**
     * The one place a conference's format changes what an outcome <em>means</em>, so the page says
     * it rather than leaving Ted to infer it from the conference having left his calendar.
     */
    @Test
    void aRejectionMeansSomethingDifferentWhereAcceptanceWasTheWayIn() {
        String callForPapers = ConferenceDetailRenderer.render(
                conference().speaking(SpeakingStatus.REJECTED, false).build(), NOW);
        String acceptanceRequired = ConferenceDetailRenderer.render(
                conference().withFormat(ConferenceFormat.ACCEPTANCE_REQUIRED)
                            .speaking(SpeakingStatus.REJECTED, false).build(), NOW);

        assertThat(callForPapers)
                .contains("<p class=\"conf-track-note\">Going is still a decision to make.</p>");
        assertThat(acceptanceRequired)
                .contains("<p class=\"conf-track-note\">"
                          + "Acceptance was the way in, so this one dropped off the calendar.</p>");
    }

    /**
     * The sentence deliberately does not say "say yes below": the answer is an attendance move and
     * lives on the track above, and a spatial instruction would be wrong the moment the grid stacks
     * on a narrow viewport.
     */
    @Test
    void anUnansweredInvitationReadsAsAnOpenOfferWithoutPointingAnywhere() {
        String html = ConferenceDetailRenderer.render(
                conference().speaking(SpeakingStatus.INVITED, false).build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-track-state\">Invited to speak</div>")
                .contains("<p class=\"conf-track-note\">An open offer, still unanswered.</p>");
    }

    // --- Each move beside the state it changes ---

    /**
     * The point of the whole arrangement. On a watched conference with nothing submitted, the talk
     * track offers the submission and the attendance track offers the two attendance moves — the
     * same three the dashboard row shows in one band, but each beside the state it changes.
     */
    @Test
    void eachMoveSitsOnTheTrackWhoseStateItChanges() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);
        int talkTrack = html.indexOf("<h2>Talk</h2>");
        int cfpTrack = html.indexOf("<h2>Call for papers</h2>");

        assertThat(html.indexOf("href=\"" + BASE + "/talk?outcome=SUBMITTED\""))
                .as("the submission is offered on the talk track")
                .isBetween(talkTrack, cfpTrack);
        assertThat(html.indexOf("href=\"" + BASE + "/confirm?basis=TICKET_PURCHASED\""))
                .as("buying a ticket is offered on the attendance track, above the talk")
                .isBetween(0, talkTrack);
        assertThat(html.indexOf("href=\"" + BASE + "/decline\""))
                .isBetween(0, talkTrack);
    }

    /**
     * The dead end the state machine fix removed, at the page that made it visible: a ticket bought
     * and a talk out with the organizers. All three answers sit on the talk track.
     */
    @Test
    void aTalkOutOnAConferenceHeAlreadyHasATicketForCanStillBeResolved() {
        ConferenceDetailView conference = conference()
                .going(AttendanceBasis.TICKET_PURCHASED)
                .speaking(SpeakingStatus.SUBMITTED, false)
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<p class=\"conf-track-note\">"
                          + "Waiting to hear. The organizers hold this one.</p>")
                .contains("href=\"" + BASE + "/talk?outcome=ACCEPTED\"")
                .contains("href=\"" + BASE + "/talk?outcome=REJECTED\"")
                .contains("href=\"" + BASE + "/talk?outcome=WITHDRAWN\"");
    }

    /**
     * A dropped conference offers nothing anywhere, and the attendance track says why rather than
     * leaving the absence to be read as a bug: the domain refuses every command against a
     * conference Ted is not going to.
     */
    @Test
    void aDroppedConferenceOffersNoMoveAndSaysWhy() {
        String html = ConferenceDetailRenderer.render(conference().dropped().build(), NOW);

        assertThat(html)
                .contains("<p class=\"conf-track-note\">"
                          + "Going after all means planning it again.</p>")
                .doesNotContain("<div class=\"conf-track-actions\">")
                .doesNotContain("href=\"" + BASE + "/decline\"");
    }

    /**
     * An empty move set adds no container at all — where a state machine decides what a surface
     * offers, an inapplicable move is absent rather than greyed, and so is the box it would sit in.
     */
    @Test
    void aTrackWithNoMovesRendersNoActionRow() {
        ConferenceDetailView conference = conference()
                .going(AttendanceBasis.TICKET_PURCHASED)
                .speaking(SpeakingStatus.SUBMITTED, false)
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .as("SUBMITTED spends the whole budget on talk moves, so attendance has none")
                .containsOnlyOnce("<div class=\"conf-track-actions\">");
    }

    // --- Standing rules ---

    /**
     * Every link is underlined at rest: the iPad has no pointer, so a hover-revealed affordance is
     * invisible at every moment (CLAUDE.md). Asserted as the pair, since the bug would be the old
     * declaration still being there.
     */
    @Test
    void panelLinksAreUnderlinedAtRestAndTakeTheirOwnLine() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .contains("display: block; width: fit-content; margin-top: 0.4rem;")
                .contains("color: var(--accent-color); text-decoration: underline;")
                .doesNotContain("display: inline-block; margin-top: 0.4rem;");
    }

    /**
     * <strong>Every link in the live column is underlined at rest, including the moves.</strong>
     * {@code .conf-action} is unadorned on the dashboard, where it is a nowrap row in a 240px cell
     * with no other link near it — but here it sits four lines above a {@code .conf-panel-link} in
     * the same column, and two links a thumb apart that do not look alike is worse than either
     * treatment on its own. Scoped to this page so the dashboard is untouched.
     */
    @Test
    void aTracksMovesAreUnderlinedLikeEveryOtherLinkInTheColumn() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .contains(".conf-track-actions .conf-action,\n"
                          + ".conf-track-actions .conf-decline { text-decoration: underline; }")
                .as("the dashboard's own rule is untouched — colour at rest, underline on hover")
                .contains(".conf-action:hover { text-decoration: underline; }");
    }

    /**
     * A pencil means edit and nothing else (CLAUDE.md), and there is nothing to edit here yet —
     * Change Conference is deferred. Pinned so the pencil is not borrowed for "open this page"
     * when someone next looks for an affordance.
     */
    @Test
    void thereIsNoEditPencilAnywhereOnThePage() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html).doesNotContain("class=\"edit-pencil\"");
    }

    // --- Fixture ---

    private static Builder conference() {
        return new Builder();
    }

    private static final class Builder {
        private String infoUrl = "";
        private String venueName = "Reehorst";
        private Address address = new Address("1 Conf St", "Ede", "", "6710", "Netherlands", null);
        private String start = "2026-11-05T09:00";
        private String end = "2026-11-07T18:00";
        private AttendanceCommitment commitment = AttendanceCommitment.WATCHING;
        private AttendanceBasis basis;
        private boolean speaking;
        private SpeakingStatus speakingStatus = SpeakingStatus.NOT_SPEAKING;
        private ZonedTimestamp cfpClosesOn;
        private String cfpSubmissionUrl = "";
        private ConferenceFormat format = ConferenceFormat.CALL_FOR_PAPERS;

        Builder withInfoUrl(String url) {
            infoUrl = url;
            return this;
        }

        Builder at(String name, Address venueAddress) {
            venueName = name;
            address = venueAddress;
            return this;
        }

        Builder running(String from, String to) {
            start = from;
            end = to;
            return this;
        }

        Builder going(AttendanceBasis attendanceBasis) {
            commitment = AttendanceCommitment.GOING;
            basis = attendanceBasis;
            return this;
        }

        Builder dropped() {
            commitment = AttendanceCommitment.NOT_GOING;
            return this;
        }

        Builder speaking(SpeakingStatus status, boolean speaks) {
            speakingStatus = status;
            speaking = speaks;
            return this;
        }

        Builder withCfp(String closesOn, String submissionUrl) {
            cfpClosesOn = ZonedTimestamp.fromLocal(LocalDateTime.parse(closesOn), AMSTERDAM);
            cfpSubmissionUrl = submissionUrl;
            return this;
        }

        Builder withFormat(ConferenceFormat conferenceFormat) {
            format = conferenceFormat;
            return this;
        }

        ConferenceDetailView build() {
            return new ConferenceDetailView(
                    CONFERENCE_ID, "J-Fall", venueName, address,
                    ZonedTimestamp.fromLocal(LocalDateTime.parse(start), AMSTERDAM),
                    ZonedTimestamp.fromLocal(LocalDateTime.parse(end), AMSTERDAM),
                    commitment, basis, speaking, speakingStatus,
                    cfpClosesOn, cfpSubmissionUrl, format, infoUrl);
        }
    }
}
