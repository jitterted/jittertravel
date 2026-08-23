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
                .contains("<td class=\"conf-city\">Frankfurt, Germany</td>");
    }

    /**
     * The two dates are one range in one column, day-only (Ted, 2026-08-22). Each is its own
     * .nowrap unit in a wrapping row, so a squeezed column breaks between them and never inside
     * one; the hyphen rides with the first, which is what keeps it off a line of its own.
     */
    @Test
    void theDateRangeIsOneColumnOfTwoNowrapDays() {
        String html = ConferencesRenderer.render(oneSection(
                view("Conf", "2026-06-07T11:00", "2026-06-10T17:00", "City", "Country")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("<div class=\"conf-dates\">"
                          + "<span class=\"nowrap\">Sun 6/7 -</span>"
                          + "<span class=\"nowrap\">Wed 6/10</span></div>");
    }

    /**
     * <strong>No clock time on a conference row.</strong> Merging Start/End into one column is what
     * pays for the fixed Actions column, and the times went with them: the page is scanned for
     * which days a conference occupies. The claim is about the whole cell, including the UTC
     * instant a {@code <time>} element would put in an attribute where nothing renders it — so it
     * asserts the element is absent, not merely the visible text.
     */
    @Test
    void theDateRangeCarriesNoTimeOfDayAtAll() {
        String html = ConferencesRenderer.render(oneSection(
                view("Conf", "2026-06-07T11:00", "2026-06-10T17:00", "City", "Country")
        ), TimeView.FUTURE);

        assertThat(datesCellOf(html))
                .doesNotContain("11:00 AM")
                .doesNotContain("5:00 PM")
                .doesNotContain("<time");
    }

    /**
     * City and country in one cell, joined in the renderer — the domain type holds the two values
     * and has no say in the comma (CLAUDE.md, "Presentation formatting stays out of the domain").
     */
    @Test
    void cityAndCountryShareOneCell() {
        String html = ConferencesRenderer.render(oneSection(
                view("Conf", "2026-06-07T11:00", "2026-06-10T17:00", "Ede", "Netherlands")
        ), TimeView.FUTURE);

        assertThat(html).contains("<td class=\"conf-city\">Ede, Netherlands</td>");
    }

    /** An absent country is {@code ""}, and the city then stands alone rather than trailing a comma. */
    @Test
    void aConferenceWithNoCountryShowsTheCityAlone() {
        String html = ConferencesRenderer.render(oneSection(
                view("Conf", "2026-06-07T11:00", "2026-06-10T17:00", "Ede", "")
        ), TimeView.FUTURE);

        assertThat(html).contains("<td class=\"conf-city\">Ede</td>");
    }

    /**
     * The column model the fixed layout needs. Going? and Actions are fixed because neither wraps;
     * Name, Dates and City are elastic and absorb a narrow viewport instead. Asserted as whole
     * elements, because the widths only bind while {@code table-layout: fixed} is sizing from this
     * row.
     */
    @Test
    void theHeaderRowCarriesTheFixedColumnModel() {
        String html = ConferencesRenderer.render(oneSection(
                view("Conf", "2026-06-07T11:00", "2026-06-10T17:00", "City", "Country")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("<th class=\"conf-col-name\">Name</th>")
                .contains("<th class=\"conf-col-going\">Going?</th>")
                .contains("<th>Dates</th>")
                .contains("<th class=\"conf-col-city\">City</th>")
                .contains("<th class=\"conf-col-actions\">Actions</th>")
                .contains("table-layout: fixed")
                .contains(".conference-table th.conf-col-actions { width: 240px; }")
                // The columns the merge replaced.
                .doesNotContain("<th>Start Date</th>")
                .doesNotContain("<th>End Date</th>")
                .doesNotContain("<th>Country</th>");
    }

    private static String datesCellOf(String html) {
        int start = html.indexOf("<div class=\"conf-dates\">");
        return html.substring(start, html.indexOf("</td>", start));
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
                // The columns need the whole viewport: measured 2026-08-19, the old
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
                .contains("<span class=\"nowrap\">Sat 9/12</span>");
    }

    /**
     * The deadline under the name reads in the same date vocabulary as the Dates column beside it
     * (Ted, 2026-08-22). They disagreed — {@code Wed 9/2} in the column, {@code Sat, Sep 12}
     * directly under the name — which is one row saying the same kind of thing two ways.
     * <p>
     * <strong>Unpadded, and that is the assertion.</strong> A single-digit month is the only case
     * that can tell {@code M/d} from {@code MM/dd}, so the fixture picks September deliberately;
     * a two-digit month would pass under either. The time stays — a CFP deadline is a moment, and
     * 11:59 PM is the whole point of it — so only the date half is in question here.
     */
    @Test
    void theCfpDeadlineUsesTheSameUnpaddedDateFormatAsTheDatesColumn() {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING, false,
                ZonedTimestamp.fromLocal(LocalDateTime.parse("2026-09-01T23:59"), ZONE));

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("<span class=\"nowrap\">Tue 9/1</span>")
                .contains("<span class=\"nowrap\">11:59 PM</span>")
                // Whole spans, not bare words: the zero-padded form, and the comma-and-abbreviated-
                // month one it replaced. "Sep" or "09" alone would each match something else.
                .doesNotContain("<span class=\"nowrap\">Tue 09/01</span>")
                .doesNotContain("<span class=\"nowrap\">Tue, Sep 1</span>")
                .doesNotContain("data-fmt=\"EEE, MMM d h:mm a\"");
    }

    /**
     * The same claim on the Dates column, since the two now share one constant: a single-digit
     * month is unpadded there too. Paired with the CFP case above so neither can be changed alone.
     */
    @Test
    void theDatesColumnIsUnpaddedToo() {
        String html = ConferencesRenderer.render(oneSection(
                view("Oops", "2026-09-02T09:00", "2026-09-03T17:00", "Hamburg", "Germany")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("<span class=\"nowrap\">Wed 9/2 -</span>")
                .contains("<span class=\"nowrap\">Thu 9/3</span>")
                .doesNotContain("<span class=\"nowrap\">Wed 09/02 -</span>");
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

    /**
     * The case that prompted the change (Ted, 2026-08-22): a deadline is the wrong thing to show a
     * conference that turned the talk down. It is not merely noise — it is the date the row is
     * grouped under {@code Decide} for having passed, so it re-states the question instead of
     * answering it.
     * <p>
     * The {@code doesNotContain} is the real assertion, and it names the whole recorded date rather
     * than a bare word: the claim is that the deadline is <em>gone</em>, not that a new line was
     * added beside it.
     */
    @Test
    void aRejectedTalkSaysSoUnderTheNameInsteadOfTheDeadline() {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING, false,
                SpeakingStatus.REJECTED,
                ZonedTimestamp.fromLocal(LocalDateTime.parse("2026-09-12T23:59"), ZONE),
                ConferenceFormat.CALL_FOR_PAPERS);

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("<div class=\"conf-cfp-deadline\">Talk rejected</div>")
                .doesNotContain("<span class=\"nowrap\">Sat 9/12</span>")
                .doesNotContain("title=\"Change the recorded CFP deadline\"")
                .doesNotContain("href=\"/conferences/" + conf.conferenceId().id() + "/cfp\"");
    }

    /**
     * The whole rule, one row per state the submission stream can be in. Written as one test
     * because the claim <em>is</em> the mapping: each state says its own thing, and no two say the
     * same thing.
     */
    @Test
    void everyStateTheStreamHasSpokenToSaysWhereTheTalkStands() {
        assertThat(subLineFor(SpeakingStatus.SUBMITTED))
                .isEqualTo("<div class=\"conf-cfp-deadline\">Talk submitted</div>");
        assertThat(subLineFor(SpeakingStatus.ACCEPTED))
                .isEqualTo("<div class=\"conf-cfp-deadline\">Talk accepted</div>");
        assertThat(subLineFor(SpeakingStatus.REJECTED))
                .isEqualTo("<div class=\"conf-cfp-deadline\">Talk rejected</div>");
        assertThat(subLineFor(SpeakingStatus.INVITED))
                .isEqualTo("<div class=\"conf-cfp-deadline\">Invited to speak</div>");
    }

    /**
     * The other half of the rule, and the reason it is not simply "always show the state": where
     * submitting is still on the table the deadline is the live fact, and it stays a link so a
     * moved deadline can be recorded. {@code WITHDRAWN} is in here rather than with the states
     * above because pulling a talk puts submitting back on the table — the dashboard offers
     * {@code Submitted} on exactly these two rows.
     */
    @Test
    void whileSubmittingIsStillOpenTheDeadlineStaysAndStaysALink() {
        for (SpeakingStatus stillOpen : List.of(SpeakingStatus.NOT_SPEAKING, SpeakingStatus.WITHDRAWN)) {
            ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                    "Ede", "Netherlands", AttendanceCommitment.WATCHING, false, stillOpen,
                    ZonedTimestamp.fromLocal(LocalDateTime.parse("2026-09-12T23:59"), ZONE),
                    ConferenceFormat.CALL_FOR_PAPERS);

            assertThat(ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE))
                    .as("%s still has a CFP to submit to", stillOpen)
                    .contains("title=\"Change the recorded CFP deadline\"")
                    .contains("<span class=\"nowrap\">Sat 9/12</span>")
                    .doesNotContain("Talk submitted")
                    .doesNotContain("Talk rejected");
        }
    }

    /**
     * <strong>Why the rule earns its keep.</strong> The {@code Decide} group holds two unrelated
     * situations — a talk was turned down, and a CFP closed with nothing submitted — and before
     * this both rows read {@code CFP <date>}, so the page could not say which was which. One
     * section, two rows, and the assertion is that they no longer render alike.
     */
    @Test
    void theTwoWaysIntoDecideNoLongerLookTheSame() {
        ZonedTimestamp deadline =
                ZonedTimestamp.fromLocal(LocalDateTime.parse("2026-09-12T23:59"), ZONE);
        ConferenceView turnedDown = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING, false,
                SpeakingStatus.REJECTED, deadline, ConferenceFormat.CALL_FOR_PAPERS);
        ConferenceView neverSubmitted = view("ExploreDDD", "2026-09-22T09:00", "2026-09-24T17:00",
                "Denver", "USA", AttendanceCommitment.WATCHING, false,
                SpeakingStatus.NOT_SPEAKING, deadline, ConferenceFormat.CALL_FOR_PAPERS);

        String html = ConferencesRenderer.render(
                List.of(new DashboardSection(DashboardGroup.DECIDE,
                                             List.of(turnedDown, neverSubmitted))),
                TimeView.FUTURE);

        assertThat(html)
                .contains("<div class=\"conf-cfp-deadline\">Talk rejected</div>")
                // Exactly one deadline survives, and it is the row that never submitted.
                .containsOnlyOnce("<span class=\"nowrap\">Sat 9/12</span>");
    }

    /**
     * The name links out to the conference's own page — public, unlike anything CFP-shaped, and the
     * same treatment a gathering's title already gets on the calendar and the itinerary.
     */
    @Test
    void theNameLinksToTheConferencesOwnPageWhenThereIsOne() {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING, false,
                SpeakingStatus.NOT_SPEAKING, null, "", ConferenceFormat.CALL_FOR_PAPERS,
                "https://jfall.nl/");

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("<a class=\"conf-info-link\" title=\"Open the conference&#x27;s own page\" "
                          + "target=\"_blank\" rel=\"noopener\" href=\"https://jfall.nl/\">J-Fall</a>");
    }

    @Test
    void aConferenceWithNoPageOfItsOwnKeepsAPlainName() {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands");

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("<div>J-Fall</div>")
                .as("no page recorded means no link, not a link to nowhere")
                // The whole opening tag, not the class name: that also appears in the page's own
                // stylesheet, where its presence says nothing about this row.
                .doesNotContain("<a class=\"conf-info-link\"");
    }

    /**
     * Where the talk goes, hanging off the deadline it was recorded with. External, so it opens in
     * a new tab like every other outbound link in the app.
     */
    @Test
    void aRecordedSubmissionUrlHangsOffTheDeadlineLine() {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING, false,
                SpeakingStatus.NOT_SPEAKING,
                ZonedTimestamp.fromLocal(LocalDateTime.parse("2026-09-12T23:59"), ZONE),
                "https://sessionize.com/jfall-2027/", ConferenceFormat.CALL_FOR_PAPERS, "");

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("<a class=\"conf-cfp-submit\" title=\"Open the CFP&#x27;s submission page\" "
                          + "target=\"_blank\" rel=\"noopener\" "
                          + "href=\"https://sessionize.com/jfall-2027/\">Submit</a>")
                .as("it sits on the deadline line, not in the actions cell")
                .doesNotContain("<div class=\"conf-actions\"><a class=\"conf-cfp-submit\"");
    }

    /**
     * It goes when the deadline goes, and for the same reason: a link inviting Ted to submit to a
     * conference that already turned him down would be the dashboard arguing with itself.
     */
    @Test
    void theSubmitLinkGoesOnceTheTalkHasBeenTurnedDown() {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING, false,
                SpeakingStatus.REJECTED,
                ZonedTimestamp.fromLocal(LocalDateTime.parse("2026-09-12T23:59"), ZONE),
                "https://sessionize.com/jfall-2027/", ConferenceFormat.CALL_FOR_PAPERS, "");

        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);

        assertThat(html)
                .doesNotContain("https://sessionize.com/jfall-2027/")
                // The anchor, not the class name — the stylesheet carries the class either way.
                .doesNotContain("<a class=\"conf-cfp-submit\"");
    }

    /**
     * The line for one submission state, isolated. Rendered through the real renderer rather than
     * asserted against a helper, so the wrapping element is part of the claim.
     */
    private static String subLineFor(SpeakingStatus status) {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING, false, status,
                ZonedTimestamp.fromLocal(LocalDateTime.parse("2026-09-12T23:59"), ZONE),
                ConferenceFormat.CALL_FOR_PAPERS);
        String html = ConferencesRenderer.render(oneSection(conf), TimeView.FUTURE);
        int start = html.indexOf("<div class=\"conf-cfp-deadline\">");
        return start < 0 ? "" : html.substring(start, html.indexOf("</div>", start) + "</div>".length());
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
     * Under the name, not in a column of its own: this table only just fits at ~820px, and a new
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
                .contains("<span class=\"nowrap\">Sat 9/12</span>")
                // Still five columns: the header row is what would grow if this became one.
                .contains("<th class=\"conf-col-actions\">Actions</th>")
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
     * The filter row owns the gap under the heading. Both halves matter: the container must not add
     * a top margin of its own, and the shared toggle's must be cancelled — a flex item's margin
     * does not collapse, so the three stacked into 3rem of empty space under the title.
     */
    @Test
    void theFilterRowOwnsTheGapUnderTheHeadingSoTheMarginsCannotStack() {
        String html = ConferencesRenderer.render(
                List.<DashboardSection>of(), TimeView.ALL, DroppedView.HIDE, 0);

        assertThat(html)
                .contains(".conference-container { margin: 0 0 2rem; padding: 0; }")
                .contains(".conference-filters .time-toggle { margin-top: 0; }")
                .doesNotContain(".conference-container { margin: 2rem 0;");
    }

    /**
     * <strong>The switch's words never change — only its box does.</strong> That is the whole point
     * of replacing the "Show dropped"/"Hide dropped" link: whichever word was showing named the
     * <em>action</em>, so nothing on the page said whether the dropped conferences were currently
     * in or out. Now the label reports the state and the tick carries the answer.
     */
    @Test
    void theDroppedSwitchReadsTheSameInBothStatesAndOnlyItsBoxChanges() {
        String hidden = ConferencesRenderer.render(
                List.<DashboardSection>of(), TimeView.ALL, DroppedView.HIDE, 2);
        String shown = ConferencesRenderer.render(
                List.<DashboardSection>of(), TimeView.ALL, DroppedView.SHOW, 2);

        assertThat(hidden)
                .as("hidden: unticked box, and the label still says Show dropped")
                .contains("aria-pressed=\"false\"")
                .contains("<span class=\"conf-dropped-box\"></span>Show dropped<b>2</b></a>")
                // The label that made the old link ambiguous. Pinned as markup, not the bare
                // words: the stylesheet's own comment explains why "Hide dropped" went, so a
                // loose doesNotContain would fail for the wrong reason.
                .doesNotContain(">Hide dropped<");
        assertThat(shown)
                .as("shown: the same label, with the box ticked")
                .contains("aria-pressed=\"true\"")
                .contains("<span class=\"conf-dropped-box\">&#10003;</span>Show dropped<b>2</b></a>")
                .doesNotContain(">Hide dropped<");
    }

    /**
     * The count has to survive the filter that hides the rows it counts — it says how many are
     * being held back, which is exactly the thing the page cannot show.
     */
    @Test
    void theDroppedSwitchCountsTheConferencesItIsHiding() {
        String html = ConferencesRenderer.render(
                List.<DashboardSection>of(), TimeView.ALL, DroppedView.HIDE, 3);

        assertThat(html).contains("Show dropped<b>3</b></a>");
    }

    @Test
    void theDroppedSwitchTurnsTheFilterOnAndKeepsTheActiveTimeFilter() {
        String html = ConferencesRenderer.render(
                List.<DashboardSection>of(), TimeView.ALL, DroppedView.HIDE, 2);

        assertThat(html)
                .contains("<a class=\"conf-dropped-toggle\" role=\"button\" aria-pressed=\"false\" "
                          + "title=\"Dropped conferences are hidden. Click to show them.\" "
                          + "href=\"/conferences?filter=all&amp;dropped=show\">");
    }

    @Test
    void theDroppedSwitchTurnsTheFilterOffAgainWhenItIsOn() {
        String html = ConferencesRenderer.render(
                List.<DashboardSection>of(), TimeView.FUTURE, DroppedView.SHOW, 2);

        assertThat(html)
                .contains("<a class=\"conf-dropped-toggle\" role=\"button\" aria-pressed=\"true\" "
                          + "title=\"Dropped conferences are shown. Click to hide them.\" "
                          + "href=\"/conferences?filter=future\">");
    }

    /**
     * The two filters are independent, so neither control may reset the other: switching
     * Upcoming/All while dropped conferences are shown has to keep showing them.
     */
    @Test
    void theTimeToggleCarriesTheDroppedFilterThrough() {
        String html = ConferencesRenderer.render(
                List.<DashboardSection>of(), TimeView.FUTURE, DroppedView.SHOW, 2);

        assertThat(html)
                .contains("href=\"/conferences?filter=all&amp;dropped=show\"")
                .contains("href=\"/conferences?filter=future&amp;dropped=show\"");
    }

    /**
     * A count per section, jumping to it — and every jump lands somewhere, because the bar is built
     * from the sections that rendered. A link to a heading that is not in the document would do
     * nothing at all, silently.
     */
    @Test
    void theJumpBarCountsEachSectionAndLinksToIt() {
        String html = ConferencesRenderer.render(List.of(
                new DashboardSection(DashboardGroup.CFP_CLOSES_SOON, List.of(
                        view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "Netherlands"))),
                new DashboardSection(DashboardGroup.GOING, List.of(
                        view("dev2next", "2026-09-28T09:00", "2026-10-01T17:00", "Denver", "USA"),
                        view("ExploreDDD", "2026-09-23T09:00", "2026-09-25T17:00", "Denver", "USA")))
        ), TimeView.FUTURE, DroppedView.HIDE, 0);

        assertThat(html)
                .contains("<a class=\"conf-jump\" title=\"Jump to CFP closes soon\" "
                          + "href=\"#section-cfp-closes-soon\"><b>1</b>CFP closing</a>")
                .contains("<a class=\"conf-jump\" title=\"Jump to Going\" "
                          + "href=\"#section-going\"><b>2</b>going</a>")
                .contains("<div class=\"dashboard-section\" id=\"section-cfp-closes-soon\">")
                .contains("<div class=\"dashboard-section\" id=\"section-going\">");
    }

    /** A section with no rows is not on the page, so it gets no count and no jump. */
    @Test
    void aSectionThatDidNotRenderGetsNoJump() {
        String html = ConferencesRenderer.render(oneSection(
                view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "Netherlands")
        ), TimeView.FUTURE, DroppedView.HIDE, 0);

        assertThat(html)
                .contains("href=\"#section-cfp-closes-soon\"")
                .doesNotContain("href=\"#section-waiting-to-hear\"")
                .doesNotContain("href=\"#section-invited\"")
                .doesNotContain("href=\"#section-going\"");
    }

    /**
     * Dropped is the switch's own number rather than a count button of its own — and the small
     * "jump" beside it appears only once there is a Dropped section to land on.
     */
    @Test
    void droppedGetsAJumpOnlyWhileItsSectionIsOnThePage() {
        String hidden = ConferencesRenderer.render(oneSection(
                view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "Netherlands")
        ), TimeView.ALL, DroppedView.HIDE, 1);
        String shown = ConferencesRenderer.render(List.of(
                new DashboardSection(DashboardGroup.DROPPED, List.of(
                        view("Oops", "2026-09-02T09:00", "2026-09-03T17:00", "Hamburg", "Germany",
                                AttendanceCommitment.NOT_GOING)))
        ), TimeView.ALL, DroppedView.SHOW, 1);

        assertThat(hidden)
                .as("nothing to jump to while the section is filtered out")
                .doesNotContain("href=\"#section-dropped\"");
        assertThat(shown)
                .contains("<a class=\"conf-jump conf-jump--small\" title=\"Jump to Dropped\" "
                          + "href=\"#section-dropped\">jump</a>")
                // Dropped's number lives on the switch; it is not also a count button.
                .doesNotContain("<b>1</b>dropped</a>");
    }

    @Test
    void aDroppedConferenceWearsANotGoingChip() {
        String html = ConferencesRenderer.render(List.of(
                new DashboardSection(DashboardGroup.DROPPED, List.of(
                        view("PLoP", "2026-10-12T09:00", "2026-10-15T17:00", "Allerton", "USA",
                                AttendanceCommitment.NOT_GOING)))
        ), TimeView.ALL, DroppedView.SHOW, 1);

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
        ), TimeView.ALL, DroppedView.SHOW, 1);

        // Asserted as whole hrefs, not the bare words: "Confirm, then Decline" appears in the
        // stylesheet's own comment, so doesNotContain("Decline") would fail for the wrong reason.
        assertThat(html)
                .contains("<div class=\"conf-actions\"></div>")
                .doesNotContain("/decline\"")
                .doesNotContain("/confirm\"")
                .doesNotContain("/cfp\"");
    }

    /**
     * The create action sits at the right edge of the toolbar, not under the last table (Ted,
     * 2026-08-22): at the bottom it was reachable only by scrolling past every section, and it grew
     * further away the more conferences there were.
     * <p>
     * Both halves are the claim — that it is inside the filter row, and that nothing is left behind
     * it at the foot of the page. The trailing {@code <br>} went with it: it existed only to space
     * the link off the last table.
     */
    @Test
    void thePlanLinkSitsAtTheRightEdgeOfTheToolbar() {
        String html = ConferencesRenderer.render(oneSection(
                view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "Netherlands")
        ), TimeView.ALL);

        assertThat(html)
                .contains("<a class=\"conf-plan-link\" href=\"/plan-conference\">"
                          + "Plan another conference</a></div>")
                // The mechanism, not just the class: margin-left on the last flex item is what
                // holds the right edge without a spacer element.
                .contains(".conf-plan-link {\n    margin-left: auto;")
                .doesNotContain("<br>");
    }

    /**
     * The one create action in a row of filters, so it is filled and a different hue (Ted,
     * 2026-08-22). Outlined in the accent colour it read as a third filter — the active time
     * segment is accent-filled and the jump links are accent text, so accent on this toolbar
     * already means "filter".
     * <p>
     * The border matching the fill is load-bearing, not decoration: it keeps the box exactly
     * {@code .time-toggle}'s size, so the three controls stay one height.
     * <p>
     * <strong>The exact green is the claim, not just "green".</strong> CSS {@code forestgreen}
     * (#228B22) is 4.4:1 against white — under WCAG AA's 4.5:1 for text this size — and #1e7a1e is
     * the visually-identical shade that clears it at 5.4:1. A test asserting only that the fill is
     * some green would let that regress silently, which is the whole reason the value changed.
     */
    @Test
    void thePlanLinkIsFilledGreenRatherThanOutlinedInTheAccentColour() {
        String html = ConferencesRenderer.render(List.<DashboardSection>of(), TimeView.ALL);

        assertThat(html)
                .contains("padding: 6px 16px; font-size: 0.875rem; font-weight: 700;")
                .contains("border: 1px solid #1e7a1e; border-radius: 6px;")
                .contains("color: #fff; background: #1e7a1e;")
                .contains(".conf-plan-link:hover { background: #1b6f1b; border-color: #1b6f1b; }")
                // The shade that does not clear AA, in both places it could appear. Pinned as
                // whole declarations: the stylesheet's own comment explains why it went, so a
                // bare doesNotContain("forestgreen") would fail for the wrong reason.
                .doesNotContain("background: forestgreen;")
                .doesNotContain("solid forestgreen;")
                // The outlined-accent styling it replaced, which made it read as a filter.
                .doesNotContain("border: 1px solid var(--accent-color); border-radius: 6px;\n"
                                + "                color: var(--accent-color);");
    }

    /**
     * The dropped chip is the only one whose fill is nearly the row's own colour, so with no edge
     * it had no visible left boundary and read as misaligned beside the solid chips (Ted,
     * 2026-08-22).
     * <p>
     * <strong>The compensated padding is the actual fix.</strong> A 1px border on top of the shared
     * {@code 2px 6px} would have made this chip a pixel bigger all round — moving the misalignment
     * rather than removing it. Both halves are asserted together for that reason: the border alone
     * would pass a test and still look wrong.
     */
    @Test
    void theNotGoingChipCarriesABorderWithoutOutgrowingTheFilledChips() {
        String html = ConferencesRenderer.render(List.of(
                new DashboardSection(DashboardGroup.DROPPED, List.of(
                        view("Oops", "2026-09-02T09:00", "2026-09-03T17:00", "Hamburg", "Germany",
                                AttendanceCommitment.NOT_GOING)))
        ), TimeView.ALL, DroppedView.SHOW, 1);

        assertThat(html)
                .contains("""
                          .conf-commitment--dropped {
                              background: var(--header-bg); color: var(--muted-text);
                              border: 1px solid var(--border-color); padding: 1px 5px;
                          }""")
                // The two filled chips keep the base padding and no border, which is what the
                // reduced padding above is compensating against.
                .contains(".conf-commitment--going { background: #166534; color: #ffffff; }")
                .contains("padding: 2px 6px; border-radius: 4px; white-space: nowrap;");
        assertThat(html.indexOf("/plan-conference"))
                .as("inside the filter row, so it comes before the first section heading")
                .isLessThan(html.indexOf("<div class=\"dashboard-section\""));
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
        return view(name, start, end, city, country, commitment, speaking, speakingStatus,
                    cfpClosesOn, "", format, "");
    }

    private static ConferenceView view(String name, String start, String end,
                                       String city, String country,
                                       AttendanceCommitment commitment, boolean speaking,
                                       SpeakingStatus speakingStatus,
                                       ZonedTimestamp cfpClosesOn, String cfpSubmissionUrl,
                                       ConferenceFormat format, String infoUrl) {
        return new ConferenceView(
                ConferenceId.random(), name, "Venue",
                new Address("1 Street", city, "", "", country, null),
                ZonedTimestamp.fromLocal(LocalDateTime.parse(start), ZONE),
                ZonedTimestamp.fromLocal(LocalDateTime.parse(end), ZONE),
                commitment, speaking, speakingStatus, cfpClosesOn, cfpSubmissionUrl, format, infoUrl
        );
    }
}