package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.application.DashboardGroup;
import dev.ted.jittertravel.application.DashboardSection;
import dev.ted.jittertravel.application.DroppedView;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConferencesRendererTest {

    // The test JVM is pinned to UTC (pom.xml), so an explicit venue zone is what proves the
    // rendered text is the venue's wall-clock rather than the server's.
    private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

    @Test
    void emptyAllListRendersEmptyStateMessage() {
        String html = ConferencesRenderer.render(List.<DashboardSection>of(), TimeView.ALL);

        assertThat(html)
                .contains("No conferences yet.")
                .doesNotContain("<td");
    }

    @Test
    void emptyFutureListRendersNoUpcomingMessage() {
        String html = ConferencesRenderer.render(List.<DashboardSection>of(), TimeView.FUTURE);

        assertThat(html).contains("No upcoming conferences.");
    }

    @Test
    void activeFilterMarkedOnToggleLink() {
        String html = ConferencesRenderer.render(List.<DashboardSection>of(), TimeView.ALL);

        assertThat(html)
                .contains("<a href=\"/conferences?filter=all\" class=\"active\">All</a>")
                .contains("<a href=\"/conferences?filter=future\">Upcoming</a>");
    }

    @Test
    void conferenceNameCityAndCountryAreRendered() {
        String html = ConferencesRenderer.render(oneSection(
                view("DDD Europe 2026", "2026-06-07T11:00", "2026-06-10T17:00", "Frankfurt", "Germany")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("DDD Europe 2026")
                .contains("Frankfurt")
                .contains("Germany");
    }

    @Test
    void startAndEndDatesAreFormatted() {
        String html = ConferencesRenderer.render(oneSection(
                view("Conf", "2026-06-07T11:00", "2026-06-10T17:00", "City", "Country")
        ), TimeView.FUTURE);

        // Date and time are separate .nowrap spans so a narrow cell breaks only between them
        // (never mid-value); there is no longer a comma joining date and time.
        assertThat(html)
                .contains("<span class=\"nowrap\">Sun, Jun 7</span>")
                .contains("<span class=\"nowrap\">11:00 AM</span>")
                .contains("<span class=\"nowrap\">Wed, Jun 10</span>")
                .contains("<span class=\"nowrap\">5:00 PM</span>");
    }

    @Test
    void tableIsNotWrappedInAHorizontalScroller() {
        String html = ConferencesRenderer.render(oneSection(
                view("Conf", "2026-06-07T11:00", "2026-06-10T17:00", "City", "Country")
        ), TimeView.FUTURE);

        // No page may ever scroll sideways: the fix removed both the overflow-x scroller and the
        // container width cap in favour of content that stacks.
        assertThat(html)
                .doesNotContain("overflow-x")
                .doesNotContain("table-responsive")
                .doesNotContain("max-width: 100ch")
                // The seven columns need the whole viewport: measured 2026-08-19, the old
                // `margin: 2rem; padding: 0 1rem` gutter made this page scroll sideways at ~860px,
                // and giving those 96px back to the table makes it fit at ~820px. The claim is
                // about the *horizontal* gutter — the vertical margin is spacing and moves freely
                // (see theFilterRowOwnsTheGapUnderTheHeading...).
                .contains(".conference-container { margin: 0 0 2rem; padding: 0; }")
                .doesNotContain("padding: 0 1rem");
    }

    @Test
    void eachConferenceRowHasADeclineLinkToItsDeclinePage() {
        ConferenceView conf = view("Devoxx Morocco", "2026-10-07T09:00", "2026-10-09T17:00",
                "Marrakesh", "Morocco");

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("/conferences/" + conf.conferenceId().id() + "/decline")
                .contains(">Decline</a>");
    }

    /**
     * Nothing submitted and a CFP to submit to: the three moves are recording a submission, buying
     * a ticket, and saying no. Labels are past tense throughout, because the app records what has
     * already happened.
     */
    @Test
    void aWatchedConferenceWithACfpOffersSubmittedTicketBoughtAndDecline() {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING);

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);
        String base = "/conferences/" + conf.conferenceId().id();

        assertThat(html)
                .contains("<span class=\"conf-commitment conf-commitment--watching\">Maybe</span>")
                .contains("href=\"" + base + "/talk?outcome=SUBMITTED\"")
                .contains(">Submitted</a>")
                .contains("href=\"" + base + "/confirm?basis=TICKET_PURCHASED\"")
                .contains(">Ticket Bought</a>")
                .contains("href=\"" + base + "/decline\"");
    }

    /** No CFP to submit to, so that move is not offered at all. */
    @Test
    void anOpenSpaceConferenceIsNotOfferedASubmission() {
        String html = ConferencesRenderer.render(oneSection(
                view("SoCraTes DE", "2026-08-20T09:00", "2026-08-23T17:00", "Soltau", "Germany",
                     AttendanceCommitment.WATCHING, false, SpeakingStatus.NOT_SPEAKING, null,
                     ConferenceFormat.OPEN_SPACE)
        ), TimeView.FUTURE);

        assertThat(html)
                .doesNotContain("outcome=SUBMITTED")
                .contains(">Ticket Bought</a>");
    }

    /** Submitted and waiting: the moves are what the organizers say, and pulling it. */
    @Test
    void aSubmittedTalkOffersTheOutcomesAndAWithdrawal() {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING, false,
                SpeakingStatus.SUBMITTED, null, ConferenceFormat.CALL_FOR_PAPERS);

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);
        String base = "/conferences/" + conf.conferenceId().id();

        assertThat(html)
                .contains("href=\"" + base + "/talk?outcome=ACCEPTED\"")
                .contains("href=\"" + base + "/talk?outcome=REJECTED\"")
                .contains("href=\"" + base + "/talk?outcome=WITHDRAWN\"")
                // Deciding not to go while a talk is out is withdrawing it; Decline becomes
                // available in the state that follows. This is what keeps every row at three.
                .doesNotContain(base + "/decline");
    }

    /**
     * An invitation is an offer. Saying yes is a confirmation that names the invitation as the
     * reason — which is what separates speaking there from merely turning up.
     */
    @Test
    void anInvitationOffersAcceptingItOrDeclining() {
        ConferenceView conf = view("PLoP", "2026-10-12T09:00", "2026-10-15T17:00",
                "Allerton", "USA", AttendanceCommitment.WATCHING, false,
                SpeakingStatus.INVITED, null, ConferenceFormat.CALL_FOR_PAPERS);

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);
        String base = "/conferences/" + conf.conferenceId().id();

        assertThat(html)
                .contains("href=\"" + base + "/confirm?basis=SPEAKING_INVITED\"")
                .contains(">Invitation Accepted</a>")
                .contains("href=\"" + base + "/decline\"")
                .doesNotContain("outcome=ACCEPTED");
    }

    /** Turned down, still watching: go as an attendee, or say no. */
    @Test
    void aRejectedTalkOffersGoingAnywayOrDeclining() {
        ConferenceView conf = view("ExploreDDD", "2026-09-14T09:00", "2026-09-16T17:00",
                "Denver", "USA", AttendanceCommitment.WATCHING, false,
                SpeakingStatus.REJECTED, null, ConferenceFormat.CALL_FOR_PAPERS);

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);
        String base = "/conferences/" + conf.conferenceId().id();

        assertThat(html)
                .contains("href=\"" + base + "/confirm?basis=TICKET_PURCHASED\"")
                .contains("href=\"" + base + "/decline\"")
                .doesNotContain("outcome=SUBMITTED");
    }

    /**
     * Committed on a bought ticket: nothing is left on the speaking axis, so the row is down to
     * changing his mind about going.
     */
    @Test
    void aCommittedConferenceOffersOnlyDecline() {
        ConferenceView conf = view("dev2next", "2026-09-28T09:00", "2026-10-01T17:00",
                "Denver", "USA", AttendanceCommitment.GOING);

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);
        String base = "/conferences/" + conf.conferenceId().id();

        assertThat(html)
                .contains("<span class=\"conf-commitment conf-commitment--going\">Going</span>")
                .contains("href=\"" + base + "/decline\"")
                .doesNotContain("/confirm?")
                .doesNotContain("/talk?");
    }

    /**
     * The one talk-side move left once a talk is in the program. Pulling it says nothing about
     * attending, which is why Decline is still there beside it.
     */
    @Test
    void anAcceptedTalkCanStillBeWithdrawnWhileGoing() {
        ConferenceView conf = view("dev2next", "2026-09-28T09:00", "2026-10-01T17:00",
                "Denver", "USA", AttendanceCommitment.GOING, true,
                SpeakingStatus.ACCEPTED, null, ConferenceFormat.CALL_FOR_PAPERS);

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);
        String base = "/conferences/" + conf.conferenceId().id();

        assertThat(html)
                .contains("href=\"" + base + "/talk?outcome=WITHDRAWN\"")
                .contains("href=\"" + base + "/decline\"");
    }

    /**
     * No state offers more than three, which is what keeps the actions cell to plain links: a menu
     * is only worth its extra click above three (CLAUDE.md), and this table only just fits.
     */
    @Test
    void noStateOffersMoreThanThreeActions() {
        List<ConferenceView> everyState = List.of(
                view("watching", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "NL",
                     AttendanceCommitment.WATCHING, false, SpeakingStatus.NOT_SPEAKING, null,
                     ConferenceFormat.CALL_FOR_PAPERS),
                view("submitted", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "NL",
                     AttendanceCommitment.WATCHING, false, SpeakingStatus.SUBMITTED, null,
                     ConferenceFormat.CALL_FOR_PAPERS),
                view("invited", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "NL",
                     AttendanceCommitment.WATCHING, false, SpeakingStatus.INVITED, null,
                     ConferenceFormat.CALL_FOR_PAPERS),
                view("rejected", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "NL",
                     AttendanceCommitment.WATCHING, false, SpeakingStatus.REJECTED, null,
                     ConferenceFormat.CALL_FOR_PAPERS),
                view("withdrawn", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "NL",
                     AttendanceCommitment.WATCHING, false, SpeakingStatus.WITHDRAWN, null,
                     ConferenceFormat.CALL_FOR_PAPERS),
                view("accepted", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "NL",
                     AttendanceCommitment.GOING, true, SpeakingStatus.ACCEPTED, null,
                     ConferenceFormat.CALL_FOR_PAPERS),
                view("going", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "NL",
                     AttendanceCommitment.GOING, false, SpeakingStatus.NOT_SPEAKING, null,
                     ConferenceFormat.CALL_FOR_PAPERS));

        for (ConferenceView conf : everyState) {
            String cell = actionsCellOf(ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE));
            assertThat(cell.split("<a ").length - 1)
                    .as("actions offered on a '%s' row", conf.name())
                    .isLessThanOrEqualTo(3);
        }
    }

    private static String actionsCellOf(String html) {
        int start = html.indexOf("<div class=\"conf-actions\">");
        return html.substring(start, html.indexOf("</td>", start));
    }

    @Test
    void decliningStaysAvailableOnACommittedConference() {
        // Changing your mind about a conference you committed to is exactly what Decline is for,
        // so unlike Confirm it is not gated on the commitment level.
        ConferenceView conf = view("dev2next", "2026-09-28T09:00", "2026-10-01T17:00",
                "Denver", "USA", AttendanceCommitment.GOING);

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("href=\"/conferences/" + conf.conferenceId().id() + "/decline\"");
    }

    // The chip's own CSS names the class, so a bare "conf-speaker" appears in every response
    // whether or not a marker renders. Both directions assert the whole element.
    private static final String SPEAKER_MARKER =
            "<span class=\"conf-speaker\" title=\"Ted is speaking at this one\">Speaker</span>";

    @Test
    void aConferenceTedSpeaksAtIsMarkedBesideItsCommitmentChip() {
        ConferenceView conf = view("dev2next", "2026-09-28T09:00", "2026-10-01T17:00",
                "Denver", "USA", AttendanceCommitment.GOING, true);

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .as("the marker joins the commitment chip in the Going? column, not replacing it")
                .contains("<span class=\"conf-commitment conf-commitment--going\">Going</span>")
                .contains(SPEAKER_MARKER);
    }

    /**
     * Speaking and commitment are separate axes: an invitation Ted has not answered yet is a
     * speculative conference he is nonetheless speaking at, and the row has to say both.
     */
    @Test
    void aSpeculativeConferenceCanAlsoBeMarkedSpeaker() {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING, true);

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("<span class=\"conf-commitment conf-commitment--watching\">Maybe</span>")
                .contains(SPEAKER_MARKER);
    }

    @Test
    void aConferenceTedMerelyAttendsCarriesNoSpeakerMarker() {
        ConferenceView conf = view("SoCraTes DE", "2026-08-20T09:00", "2026-08-23T17:00",
                "Soltau", "Germany", AttendanceCommitment.GOING, false);

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("<span class=\"conf-commitment conf-commitment--going\">Going</span>")
                .doesNotContain(SPEAKER_MARKER);
    }

    /**
     * Recording the deadline is reached from under the name, not from the actions cell: it is a
     * property of the conference rather than a move in the submission state machine, and keeping
     * it out is what lets every state fit in three actions.
     */
    @Test
    void aConferenceWithNoDeadlineSaysSoUnderItsNameAndLinksThere() {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING, false, null);

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("href=\"/conferences/" + conf.conferenceId().id() + "/cfp\"")
                .contains(">CFP date unknown</a>")
                .contains("title=\"Record when this conference&#x27;s CFP closes\"")
                // In the name cell, never the actions cell.
                .doesNotContain("<div class=\"conf-actions\"><a class=\"conf-cfp\"");
    }

    @Test
    void aRecordedDeadlineIsItselfTheLinkToChangeIt() {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING, false,
                ZonedTimestamp.fromLocal(LocalDateTime.parse("2026-09-12T23:59"), ZONE));

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("href=\"/conferences/" + conf.conferenceId().id() + "/cfp\"")
                .contains("title=\"Change the recorded CFP deadline\"")
                .contains("<span class=\"nowrap\">Sat, Sep 12</span>");
    }

    /** An open-space conference has no call for papers, so there is nothing to record. */
    @Test
    void anOpenSpaceConferenceShowsNoCfpLineAtAll() {
        String html = ConferencesRenderer.render(oneSection(
                view("SoCraTes DE", "2026-08-20T09:00", "2026-08-23T17:00", "Soltau", "Germany",
                     AttendanceCommitment.WATCHING, false, SpeakingStatus.NOT_SPEAKING, null,
                     ConferenceFormat.OPEN_SPACE)
        ), TimeView.FUTURE);

        assertThat(html)
                .doesNotContain("/cfp\"")
                .doesNotContain("CFP date unknown");
    }

    @Test
    void eachGroupIsHeadedAndSaysWhatToDoAboutIt() {
        String html = ConferencesRenderer.render(List.of(
                new DashboardSection(DashboardGroup.CFP_CLOSES_SOON, List.of(
                        view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "Netherlands"))),
                new DashboardSection(DashboardGroup.GOING, List.of(
                        view("dev2next", "2026-09-28T09:00", "2026-10-01T17:00", "Denver", "USA")))
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("<h2 class=\"dashboard-heading\">CFP closes soon</h2>")
                .contains("<p class=\"dashboard-guidance\">Submit, or decide not to.</p>")
                .contains("<h2 class=\"dashboard-heading\">Going</h2>")
                .contains("<p class=\"dashboard-guidance\">Committed — nothing to do.</p>");
    }

    /**
     * The whole point of the grouping: a reader scanning down the page meets the group with someone
     * else's clock running before the one that needs nothing.
     */
    @Test
    void groupsRenderInTheOrderTheyAreGiven() {
        String html = ConferencesRenderer.render(List.of(
                new DashboardSection(DashboardGroup.CFP_CLOSES_SOON, List.of(
                        view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "Netherlands"))),
                new DashboardSection(DashboardGroup.GOING, List.of(
                        view("dev2next", "2026-09-28T09:00", "2026-10-01T17:00", "Denver", "USA")))
        ), TimeView.FUTURE);

        assertThat(html.indexOf("CFP closes soon"))
                .isLessThan(html.indexOf("<h2 class=\"dashboard-heading\">Going</h2>"));
    }

    /**
     * Under the name, not in an eighth column: this table only just fits at ~820px, and a new
     * column would push it into the horizontal scroll that is ruled out everywhere.
     */
    @Test
    void aRecordedDeadlineRendersUnderTheConferenceNameNotInItsOwnColumn() {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING, false,
                ZonedTimestamp.fromLocal(LocalDateTime.parse("2026-09-12T23:59"), ZONE));

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("<div class=\"conf-cfp-deadline\">")
                .contains("<span class=\"nowrap\">Sat, Sep 12</span>")
                // Still seven columns: the header row is what would grow if this became one.
                .contains("<th>Actions</th>")
                .doesNotContain("<th>CFP</th>");
    }

    @Test
    void theBasisForGoingNeverReachesTheList() {
        // AttendanceBasis is OWNER-private submission status wearing a different hat: it is not
        // carried on ConferenceView at all, so no wording of it can appear here.
        String html = ConferencesRenderer.render(oneSection(
                view("dev2next", "2026-09-28T09:00", "2026-10-01T17:00", "Denver", "USA",
                        AttendanceCommitment.GOING)
        ), TimeView.FUTURE);

        assertThat(html)
                .doesNotContain("SPEAKING_ACCEPTED")
                .doesNotContain("TICKET_PURCHASED")
                .doesNotContain("SPEAKING_INVITED");
    }

    /**
     * The dropped switch is a control, not a sentence: it was plain muted text and read as words
     * left dangling beside the toggle. It takes the shared toggle's padding, font-size and radius
     * so the two read as a pair, and stays outlined rather than filled.
     */
    @Test
    void theDroppedToggleIsStyledAsAControlBesideTheTimeToggle() {
        String html = ConferencesRenderer.render(
                List.<DashboardSection>of(), TimeView.ALL, DroppedView.HIDE);

        assertThat(html)
                .contains("padding: 6px 16px; font-size: 0.9rem;")
                .contains("border: 1px solid var(--border-color); border-radius: 6px;")
                .contains(".dropped-toggle:hover { background-color: var(--header-bg);");
    }

    /**
     * The filter row owns the gap under the heading. Both halves matter: the container must not add
     * a top margin of its own, and the shared toggle's must be cancelled — a flex item's margin
     * does not collapse, so the three stacked into 3rem of empty space under the title.
     */
    @Test
    void theFilterRowOwnsTheGapUnderTheHeadingSoTheMarginsCannotStack() {
        String html = ConferencesRenderer.render(
                List.<DashboardSection>of(), TimeView.ALL, DroppedView.HIDE);

        assertThat(html)
                .contains(".conference-container { margin: 0 0 2rem; padding: 0; }")
                .contains(".conference-filters .time-toggle { margin-top: 0; }")
                .doesNotContain(".conference-container { margin: 2rem 0;");
    }

    @Test
    void droppedToggleOffersToShowDroppedConferencesAndKeepsTheActiveTimeFilter() {
        String html = ConferencesRenderer.render(
                List.<DashboardSection>of(), TimeView.ALL, DroppedView.HIDE);

        assertThat(html)
                .contains("<a class=\"dropped-toggle\" href=\"/conferences?filter=all&amp;dropped=show\">"
                          + "Show dropped</a>")
                .doesNotContain("Hide dropped");
    }

    @Test
    void showingDroppedConferencesOffersToHideThemAgain() {
        String html = ConferencesRenderer.render(
                List.<DashboardSection>of(), TimeView.FUTURE, DroppedView.SHOW);

        assertThat(html)
                .contains("<a class=\"dropped-toggle\" href=\"/conferences?filter=future\">"
                          + "Hide dropped</a>")
                .doesNotContain("Show dropped");
    }

    /**
     * The two filters are independent, so neither toggle may reset the other: switching
     * Upcoming/All while dropped conferences are shown has to keep showing them.
     */
    @Test
    void theTimeToggleCarriesTheDroppedFilterThrough() {
        String html = ConferencesRenderer.render(
                List.<DashboardSection>of(), TimeView.FUTURE, DroppedView.SHOW);

        assertThat(html)
                .contains("href=\"/conferences?filter=all&amp;dropped=show\"")
                .contains("href=\"/conferences?filter=future&amp;dropped=show\"");
    }

    @Test
    void aDroppedConferenceWearsANotGoingChip() {
        String html = ConferencesRenderer.render(List.of(
                new DashboardSection(DashboardGroup.DROPPED, List.of(
                        view("PLoP", "2026-10-12T09:00", "2026-10-15T17:00", "Allerton", "USA",
                                AttendanceCommitment.NOT_GOING)))
        ), TimeView.ALL, DroppedView.SHOW);

        assertThat(html)
                .contains("<span class=\"conf-commitment conf-commitment--dropped\">Not going</span>")
                .contains("<h2 class=\"dashboard-heading\">Dropped</h2>")
                .contains("<p class=\"dashboard-guidance\">Said no to these. "
                          + "Kept as a record for next year.</p>");
    }

    /**
     * Every command against a declined conference is refused by the domain, so the row carries no
     * action at all — not even a disabled one, which would advertise a capability that does not
     * exist. This is the state machine deciding what a row offers, which is the one case where an
     * affordance may legitimately be absent rather than greyed.
     */
    @Test
    void aDroppedConferenceOffersNoActions() {
        String html = ConferencesRenderer.render(List.of(
                new DashboardSection(DashboardGroup.DROPPED, List.of(
                        view("PLoP", "2026-10-12T09:00", "2026-10-15T17:00", "Allerton", "USA",
                                AttendanceCommitment.NOT_GOING)))
        ), TimeView.ALL, DroppedView.SHOW);

        // Asserted as whole hrefs, not the bare words: "Confirm, then Decline" appears in the
        // stylesheet's own comment, so doesNotContain("Decline") would fail for the wrong reason.
        assertThat(html)
                .contains("<div class=\"conf-actions\"></div>")
                .doesNotContain("/decline\"")
                .doesNotContain("/confirm\"")
                .doesNotContain("/cfp\"");
    }

    @Test
    void planConferenceLinkIsPresent() {
        String html = ConferencesRenderer.render(List.<DashboardSection>of(), TimeView.ALL);

        assertThat(html).contains("/plan-conference");
    }

    /**
     * Most cases here are about how a <em>row</em> renders, which is independent of its group — so
     * they wrap their conferences in one arbitrary section. The grouping itself is
     * {@code ConferenceDashboardTest}'s subject; how a group is headed is asserted below.
     */
    private static List<DashboardSection> oneSection(ConferenceView... conferences) {
        return List.of(new DashboardSection(DashboardGroup.CFP_CLOSES_SOON, List.of(conferences)));
    }

    private static ConferenceView view(String name, String start, String end,
                                       String city, String country) {
        return view(name, start, end, city, country, AttendanceCommitment.WATCHING);
    }

    private static ConferenceView view(String name, String start, String end,
                                       String city, String country,
                                       AttendanceCommitment commitment) {
        return view(name, start, end, city, country, commitment, false);
    }

    private static ConferenceView view(String name, String start, String end,
                                       String city, String country,
                                       AttendanceCommitment commitment, boolean speaking) {
        return view(name, start, end, city, country, commitment, speaking, null);
    }

    private static ConferenceView view(String name, String start, String end,
                                       String city, String country,
                                       AttendanceCommitment commitment, boolean speaking,
                                       ZonedTimestamp cfpClosesOn) {
        return view(name, start, end, city, country, commitment, speaking, cfpClosesOn,
                    ConferenceFormat.CALL_FOR_PAPERS);
    }

    private static ConferenceView view(String name, String start, String end,
                                       String city, String country,
                                       AttendanceCommitment commitment, boolean speaking,
                                       ZonedTimestamp cfpClosesOn, ConferenceFormat format) {
        return view(name, start, end, city, country, commitment, speaking,
                    SpeakingStatus.NOT_SPEAKING, cfpClosesOn, format);
    }

    private static ConferenceView view(String name, String start, String end,
                                       String city, String country,
                                       AttendanceCommitment commitment, boolean speaking,
                                       SpeakingStatus speakingStatus,
                                       ZonedTimestamp cfpClosesOn, ConferenceFormat format) {
        return new ConferenceView(
                ConferenceId.random(), name, "Venue",
                new Address("1 Street", city, "", "", country, null),
                ZonedTimestamp.fromLocal(LocalDateTime.parse(start), ZONE),
                ZonedTimestamp.fromLocal(LocalDateTime.parse(end), ZONE),
                commitment, speaking, speakingStatus, cfpClosesOn, format
        );
    }
}