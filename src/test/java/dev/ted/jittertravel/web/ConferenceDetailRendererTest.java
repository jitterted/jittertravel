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

    // --- When ---

    @Test
    void bothEndsAreShownWithTheirClockTimeAndTheVenueZone() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .as("the calendar and the dashboard show days; this is the page with the times")
                .contains(">Thu, Nov 5, 2026 at 9:00 AM</time>")
                .contains(">Sat, Nov 7, 2026 at 6:00 PM</time>")
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

    // --- Where ---

    @Test
    void theVenueItsStreetAndItsCityAreAllShown() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-panel-line conf-panel-lead\">Reehorst</div>")
                .contains("<div class=\"conf-panel-line\">1 Conf St</div>")
                .contains("<div class=\"conf-panel-line\">Ede, Netherlands</div>");
    }

    @Test
    void aMissingCountryLeavesTheCityStandingAlone() {
        ConferenceDetailView conference = conference()
                .at("Reehorst", new Address("1 Conf St", "Ede", "", "6710", "", null))
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<div class=\"conf-panel-line\">Ede</div>")
                .as("no country recorded means no trailing comma")
                .doesNotContain(">Ede, <");
    }

    /**
     * The conference's own site lives here now — the dashboard's name was given to this page, so
     * this is the only place the owner reaches it from. External, so it opens in a new tab.
     */
    @Test
    void theConferencesOwnPageHangsOffTheWherePanel() {
        String html = ConferenceDetailRenderer.render(
                conference().withInfoUrl("https://jfall.nl/").build(), NOW);

        assertThat(html)
                .contains("<a class=\"conf-panel-link\" title=\"Open the conference&#x27;s own page\" "
                          + "target=\"_blank\" rel=\"noopener\" href=\"https://jfall.nl/\">"
                          + "Conference website</a>");
    }

    @Test
    void aConferenceWithNoPageOfItsOwnGetsNoWebsiteLink() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .as("no page recorded means no link, not a link to nowhere")
                .doesNotContain(">Conference website</a>");
    }

    // --- Attendance, and why ---

    /**
     * <strong>Why Ted is going is on this page and on no other.</strong> {@code AttendanceBasis} is
     * on CLAUDE.md's private list because it re-states the submission outcome; it is showable here
     * only because {@code /conferences/*} is OWNER-only in {@code SecurityConfig}.
     */
    @Test
    void aTicketBoughtIsSaidOutLoud() {
        ConferenceDetailView conference = conference()
                .going(AttendanceBasis.TICKET_PURCHASED)
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<span class=\"conf-commitment conf-commitment--going\">Going</span>")
                .contains("<p class=\"conf-panel-note\">You bought a ticket.</p>");
    }

    @Test
    void anAcceptedInvitationIsSaidOutLoud() {
        ConferenceDetailView conference = conference()
                .going(AttendanceBasis.SPEAKING_INVITED)
                .speaking(SpeakingStatus.INVITED, true)
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<p class=\"conf-panel-note\">You accepted an invitation to speak.</p>");
    }

    /**
     * An acceptance commits attendance on its own and writes no confirmation at all, so there is no
     * basis to read — the page says why from the submission stream instead of saying nothing.
     */
    @Test
    void anAcceptedTalkExplainsTheCommitmentWithNoBasisRecorded() {
        ConferenceDetailView conference = conference()
                .going(null)
                .speaking(SpeakingStatus.ACCEPTED, true)
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<p class=\"conf-panel-note\">"
                          + "A submitted talk was accepted, which is what committed you.</p>");
    }

    /**
     * <strong>A ticket bought before a talk was accepted keeps its own reason.</strong> The
     * acceptance did not commit him — he was already going — so saying it "is what committed you"
     * would be false, and the Talk panel beside this one already reports the acceptance. This is
     * the case that caught the precedence being the wrong way round; mutation-verified by flipping
     * it back.
     */
    @Test
    void aTicketBoughtBeforeATalkWasAcceptedIsStillTheReasonHeIsGoing() {
        ConferenceDetailView conference = conference()
                .going(AttendanceBasis.TICKET_PURCHASED)
                .speaking(SpeakingStatus.ACCEPTED, true)
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<p class=\"conf-panel-note\">You bought a ticket.</p>")
                .doesNotContain("which is what committed you");
    }

    @Test
    void aConferenceMerelyWatchedSaysMaybeAndGivesNoReason() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .contains("<span class=\"conf-commitment conf-commitment--watching\">Maybe</span>")
                .contains("<div class=\"conf-panel-line conf-panel-lead\">Maybe</div>")
                .doesNotContain("You bought a ticket.");
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
                .contains("<div class=\"conf-panel-line conf-panel-lead\">Not going</div>")
                .contains("<p class=\"conf-panel-note\">You had bought a ticket.</p>")
                .as("the reason is stated once, in the tense that is true now")
                .doesNotContain("You bought a ticket.");
    }

    /**
     * Declined while merely watched: there is no basis, because he never confirmed. The panel says
     * the commitment and stops rather than inventing a reason.
     */
    @Test
    void aConferenceDroppedBeforeHeEverCommittedGivesNoReason() {
        String html = ConferenceDetailRenderer.render(conference().dropped().build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-panel-line conf-panel-lead\">Not going</div>")
                .doesNotContain("You had bought a ticket.")
                .doesNotContain("You were going");
    }

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

    // --- The CFP panel: four absences, four sentences ---

    /**
     * The pair that is easiest to get wrong, and the reason this panel exists: {@code null} means
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
                .contains("<div class=\"conf-panel-line conf-panel-lead\">None</div>")
                .contains("<p class=\"conf-panel-note\">Sessions are chosen on the day.</p>")
                .as("nothing to record, so no way in to the CFP form")
                .doesNotContain("href=\"" + BASE + "/cfp\"");
    }

    /**
     * A deadline recorded before the conference was marked open-space — the pending SoCraTes/PLoP
     * re-marking. {@code CfpDeadlineSource} does not filter by format, so that row is still putting
     * alarms on Ted's phone; printing "None" over the top of it would be the page hiding something
     * live. No link, because {@code OpenCfpCommand} refuses a CFP on an open space.
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
                .contains(">Sat, Sep 12, 2026 at 11:59 PM</time>")
                .contains("<p class=\"conf-panel-note\">"
                          + "It is still setting a reminder, and there is no way to clear it yet.</p>")
                .as("the domain refuses a CFP on an open space, so there is nowhere to send him")
                .doesNotContain("href=\"" + BASE + "/cfp\"");
    }

    @Test
    void aCfpNobodyHasRecordedYetAsksTedToGoAndFindIt() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-panel-line conf-panel-lead\">Not recorded</div>")
                .contains("<p class=\"conf-panel-note\">"
                          + "Find the closing date and record it, so a reminder can be set.</p>")
                .contains("<a class=\"conf-panel-link\" href=\"" + BASE + "/cfp\">"
                          + "Record when this CFP closes</a>");
    }

    @Test
    void anOpenCfpSaysWhenItClosesAndHowLongIsLeft() {
        ConferenceDetailView conference = conference()
                .withCfp("2026-09-12T23:59", "")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<span>Open until </span>")
                .contains(">Sat, Sep 12, 2026 at 11:59 PM</time>")
                .contains("<p class=\"conf-panel-note\">Closes in 8 days.</p>");
    }

    @Test
    void aDeadlineTomorrowSaysTomorrowRatherThanOneDay() {
        ConferenceDetailView conference = conference()
                .withCfp("2026-09-05T23:59", "")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html).contains("<p class=\"conf-panel-note\">Closes tomorrow.</p>");
    }

    @Test
    void aDeadlineLaterTodaySaysToday() {
        ConferenceDetailView conference = conference()
                .withCfp("2026-09-04T23:59", "")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html).contains("<p class=\"conf-panel-note\">Closes today.</p>");
    }

    /**
     * Once the window has shut the countdown is meaningless and the way in to submitting is gone.
     * Re-recording the deadline stays, because a moved deadline is corrected by recording it again.
     */
    @Test
    void aClosedCfpSaysClosedAndOffersNoWayToSubmit() {
        ConferenceDetailView conference = conference()
                .withCfp("2026-08-01T23:59", "https://sessionize.com/jfall/")
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<span>Closed </span>")
                .doesNotContain("Closes in")
                .doesNotContain(">Submit a talk</a>")
                .contains("<a class=\"conf-panel-link\" href=\"" + BASE + "/cfp\">"
                          + "Change the recorded deadline</a>");
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

    /**
     * A link inviting Ted to submit to a conference that already turned him down would be the page
     * arguing with itself — the same rule the dashboard's deadline line follows.
     */
    @Test
    void aRejectedTalkTakesTheSubmissionLinkWithIt() {
        ConferenceDetailView conference = conference()
                .withCfp("2026-09-12T23:59", "https://sessionize.com/jfall/")
                .speaking(SpeakingStatus.REJECTED, false)
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<span>Open until </span>")
                .doesNotContain(">Submit a talk</a>");
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
                .contains(">Sat, Sep 12, 2026 at 11:59 PM</time>")
                .doesNotContain("href=\"" + BASE + "/cfp\"")
                .doesNotContain(">Submit a talk</a>");
    }

    /**
     * The prompt and the link are one thing, and they leave together. Asking Ted to go and find a
     * closing date while withholding the only page that would accept it is the page asking for work
     * it has already decided is impossible — the domain refuses a CFP on a conference he is not
     * going to.
     */
    @Test
    void aDroppedConferenceWithNoCfpDoesNotAskTedToGoAndFindOne() {
        String html = ConferenceDetailRenderer.render(conference().dropped().build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-panel-line conf-panel-lead\">Not recorded</div>")
                .contains("<p class=\"conf-panel-note\">"
                          + "A CFP cannot be recorded for a conference you are not going to.</p>")
                .doesNotContain("Find the closing date and record it")
                .doesNotContain("href=\"" + BASE + "/cfp\"");
    }

    // --- The talk panel ---

    @Test
    void aSubmittedTalkSaysWhoHoldsItNow() {
        String html = ConferenceDetailRenderer.render(
                conference().speaking(SpeakingStatus.SUBMITTED, false).build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-panel-line conf-panel-lead\">Submitted</div>")
                .contains("<p class=\"conf-panel-note\">"
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
                .contains("<p class=\"conf-panel-note\">Going is still a decision to make.</p>");
        assertThat(acceptanceRequired)
                .contains("<p class=\"conf-panel-note\">"
                          + "Acceptance was the way in, so this one dropped off the calendar.</p>");
    }

    @Test
    void anUnansweredInvitationReadsAsAnOpenOffer() {
        String html = ConferenceDetailRenderer.render(
                conference().speaking(SpeakingStatus.INVITED, false).build(), NOW);

        assertThat(html)
                .contains("<div class=\"conf-panel-line conf-panel-lead\">Invited to speak</div>")
                .contains("<p class=\"conf-panel-note\">An open offer. Say yes, or decline.</p>");
    }

    // --- The actions band ---

    @Test
    void thePageOffersTheSameMovesTheDashboardRowDoes() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .contains("href=\"" + BASE + "/talk?outcome=SUBMITTED\"")
                .contains("href=\"" + BASE + "/confirm?basis=TICKET_PURCHASED\"")
                .contains("href=\"" + BASE + "/decline\"");
    }

    /**
     * The dead end, at the page that made it visible: a ticket bought and a talk out with the
     * organizers. The Talk panel says they are holding it, so the band beside it has to offer the
     * three ways that ends — before 2026-09-06 it offered Decline alone, and abandoning the whole
     * conference was the only thing the page could do about a pending talk.
     */
    @Test
    void aTalkOutOnAConferenceHeAlreadyHasATicketForCanStillBeResolved() {
        ConferenceDetailView conference = conference()
                .going(AttendanceBasis.TICKET_PURCHASED)
                .speaking(SpeakingStatus.SUBMITTED, false)
                .build();

        String html = ConferenceDetailRenderer.render(conference, NOW);

        assertThat(html)
                .contains("<p class=\"conf-panel-note\">"
                          + "Waiting to hear. The organizers hold this one.</p>")
                .contains("href=\"" + BASE + "/talk?outcome=ACCEPTED\"")
                .contains("href=\"" + BASE + "/talk?outcome=REJECTED\"")
                .contains("href=\"" + BASE + "/talk?outcome=WITHDRAWN\"");
    }

    @Test
    void aDroppedConferenceSaysThereIsNothingToRecord() {
        String html = ConferenceDetailRenderer.render(conference().dropped().build(), NOW);

        assertThat(html)
                .contains("Nothing to record — you are not going to this one.")
                .doesNotContain("href=\"" + BASE + "/decline\"");
    }

    /**
     * A panel can carry two links — the open CFP carries both a submission page and a way to
     * re-record the deadline — and they must not run together. They did:
     * {@code display: inline-block} rendered "Submit a talkChange the recorded deadline" with no
     * gap at all. Asserted as the pair CLAUDE.md's CSS rule asks for, since the bug is exactly the
     * old declaration still being there.
     */
    @Test
    void panelLinksEachTakeTheirOwnLine() {
        String html = ConferenceDetailRenderer.render(conference().build(), NOW);

        assertThat(html)
                .contains("display: block; width: fit-content; margin-top: 0.4rem;")
                .doesNotContain("display: inline-block; margin-top: 0.4rem;");
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
