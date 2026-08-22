package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.ConferenceId;
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
        String html = ConferencesRenderer.render(List.of(), TimeView.ALL);

        assertThat(html)
                .contains("No conferences yet.")
                .doesNotContain("<td");
    }

    @Test
    void emptyFutureListRendersNoUpcomingMessage() {
        String html = ConferencesRenderer.render(List.of(), TimeView.FUTURE);

        assertThat(html).contains("No upcoming conferences.");
    }

    @Test
    void activeFilterMarkedOnToggleLink() {
        String html = ConferencesRenderer.render(List.of(), TimeView.ALL);

        assertThat(html)
                .contains("<a href=\"/conferences?filter=all\" class=\"active\">All</a>")
                .contains("<a href=\"/conferences?filter=future\">Upcoming</a>");
    }

    @Test
    void conferenceNameCityAndCountryAreRendered() {
        String html = ConferencesRenderer.render(List.of(
                view("DDD Europe 2026", "2026-06-07T11:00", "2026-06-10T17:00", "Frankfurt", "Germany")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("DDD Europe 2026")
                .contains("Frankfurt")
                .contains("Germany");
    }

    @Test
    void startAndEndDatesAreFormatted() {
        String html = ConferencesRenderer.render(List.of(
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
        String html = ConferencesRenderer.render(List.of(
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
                // and giving those 96px back to the table makes it fit at ~820px.
                .contains(".conference-container { margin: 2rem 0; padding: 0; }")
                .doesNotContain("padding: 0 1rem");
    }

    @Test
    void eachConferenceRowHasADeclineLinkToItsDeclinePage() {
        ConferenceView conf = view("Devoxx Morocco", "2026-10-07T09:00", "2026-10-09T17:00",
                "Marrakesh", "Morocco");

        String html = ConferencesRenderer.render(List.of(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("/conferences/" + conf.conferenceId().id() + "/decline")
                .contains(">Decline</a>");
    }

    @Test
    void speculativeConferenceShowsMaybeAndOffersConfirmAttendance() {
        ConferenceView conf = view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00",
                "Ede", "Netherlands", AttendanceCommitment.WATCHING);

        String html = ConferencesRenderer.render(List.of(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("<span class=\"conf-commitment conf-commitment--watching\">Maybe</span>")
                .contains("href=\"/conferences/" + conf.conferenceId().id() + "/confirm\"")
                .contains(">Confirm</a>");
    }

    @Test
    void committedConferenceShowsGoingAndDropsTheConfirmLink() {
        ConferenceView conf = view("dev2next", "2026-09-28T09:00", "2026-10-01T17:00",
                "Denver", "USA", AttendanceCommitment.GOING);

        String html = ConferencesRenderer.render(List.of(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("<span class=\"conf-commitment conf-commitment--going\">Going</span>")
                .doesNotContain("/confirm\"")
                .doesNotContain(">Confirm</a>");
    }

    @Test
    void declineKeepsItsPlaceOnRowsThatHaveNoConfirmLink() {
        // Action affordances never move, and an unavailable one is disabled rather than removed:
        // a GOING row fills the first slot with greyed, non-interactive text. Leaving the slot
        // empty slid Decline into it; leaving it invisible left a blank line when the cell wrapped.
        String html = ConferencesRenderer.render(List.of(
                view("dev2next", "2026-09-28T09:00", "2026-10-01T17:00", "Denver", "USA",
                     AttendanceCommitment.GOING)
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("<span class=\"conf-confirm-disabled\"")
                .contains(">Confirm</span>")
                .contains("color: var(--muted-text); cursor: default;")
                // Visible, not the old invisible placeholder.
                .doesNotContain("visibility: hidden")
                // Left-justified within their slots, never flush right.
                .doesNotContain("justify-content: flex-end");
    }

    @Test
    void disabledConfirmSaysWhyItIsUnavailableAndCannotBeActivated() {
        // A greyed control with no reason is a dead end. The reason names the *presentation* limit
        // it really is — the domain allows re-confirming with a different basis — and it is a span,
        // so it is neither focusable nor clickable.
        String html = ConferencesRenderer.render(List.of(
                view("dev2next", "2026-09-28T09:00", "2026-10-01T17:00", "Denver", "USA",
                     AttendanceCommitment.GOING)
        ), TimeView.FUTURE);

        assertThat(html)
                .contains("title=\"Already confirmed. Changing why you&#x27;re going "
                          + "arrives with submission tracking.\"")
                .contains("aria-disabled=\"true\"")
                .doesNotContain(">Confirm</a>");
    }

    @Test
    void speculativeRowFillsTheConfirmSlotWithTheRealLinkNotTheDisabledText() {
        String html = ConferencesRenderer.render(List.of(
                view("J-Fall", "2026-11-05T09:00", "2026-11-05T18:00", "Ede", "Netherlands",
                     AttendanceCommitment.WATCHING)
        ), TimeView.FUTURE);

        assertThat(html)
                .contains(">Confirm</a>")
                // The class name alone appears in the stylesheet on every page; only the element
                // is a claim about this row.
                .doesNotContain("<span class=\"conf-confirm-disabled\"");
    }

    @Test
    void actionsHeaderIsCentredAcrossBothSlots() {
        // The header labels the pair, so it belongs over neither link in particular.
        String html = ConferencesRenderer.render(List.of(
                view("Conf", "2026-06-07T11:00", "2026-06-10T17:00", "City", "Country")
        ), TimeView.FUTURE);

        assertThat(html)
                .contains(".conference-table th:last-child { text-align: center; }")
                .doesNotContain("th:last-child { text-align: right; }");
    }

    @Test
    void decliningStaysAvailableOnACommittedConference() {
        // Changing your mind about a conference you committed to is exactly what Decline is for,
        // so unlike Confirm it is not gated on the commitment level.
        ConferenceView conf = view("dev2next", "2026-09-28T09:00", "2026-10-01T17:00",
                "Denver", "USA", AttendanceCommitment.GOING);

        String html = ConferencesRenderer.render(List.of(conf), TimeView.FUTURE);

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

        String html = ConferencesRenderer.render(List.of(conf), TimeView.FUTURE);

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

        String html = ConferencesRenderer.render(List.of(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("<span class=\"conf-commitment conf-commitment--watching\">Maybe</span>")
                .contains(SPEAKER_MARKER);
    }

    @Test
    void aConferenceTedMerelyAttendsCarriesNoSpeakerMarker() {
        ConferenceView conf = view("SoCraTes DE", "2026-08-20T09:00", "2026-08-23T17:00",
                "Soltau", "Germany", AttendanceCommitment.GOING, false);

        String html = ConferencesRenderer.render(List.of(conf), TimeView.FUTURE);

        assertThat(html)
                .contains("<span class=\"conf-commitment conf-commitment--going\">Going</span>")
                .doesNotContain(SPEAKER_MARKER);
    }

    @Test
    void theBasisForGoingNeverReachesTheList() {
        // AttendanceBasis is OWNER-private submission status wearing a different hat: it is not
        // carried on ConferenceView at all, so no wording of it can appear here.
        String html = ConferencesRenderer.render(List.of(
                view("dev2next", "2026-09-28T09:00", "2026-10-01T17:00", "Denver", "USA",
                        AttendanceCommitment.GOING)
        ), TimeView.FUTURE);

        assertThat(html)
                .doesNotContain("SPEAKING_ACCEPTED")
                .doesNotContain("TICKET_PURCHASED")
                .doesNotContain("SPEAKING_INVITED");
    }

    @Test
    void planConferenceLinkIsPresent() {
        String html = ConferencesRenderer.render(List.of(), TimeView.ALL);

        assertThat(html).contains("/plan-conference");
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
        return new ConferenceView(
                ConferenceId.random(), name, "Venue",
                new Address("1 Street", city, "", "", country, null),
                ZonedTimestamp.fromLocal(LocalDateTime.parse(start), ZONE),
                ZonedTimestamp.fromLocal(LocalDateTime.parse(end), ZONE),
                commitment, speaking
        );
    }
}