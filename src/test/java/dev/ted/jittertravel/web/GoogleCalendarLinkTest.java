package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleCalendarLinkTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final ZoneId DENVER = ZoneId.of("America/Denver");

    @Test
    void templateUrlCarriesTitleLocalTimesAndZone() {
        String href = GoogleCalendarLink.href(
                "SoCraTes",
                berlin(2026, 9, 15, 9, 0),
                berlin(2026, 9, 17, 17, 0),
                "Hotel Park, Soltau, DE",
                "https://socrates-conference.de");

        assertThat(href)
                .isEqualTo("https://calendar.google.com/calendar/render?action=TEMPLATE"
                                   + "&text=SoCraTes"
                                   + "&dates=20260915T090000/20260917T170000"
                                   + "&ctz=Europe%2FBerlin"
                                   + "&location=Hotel+Park%2C+Soltau%2C+DE"
                                   + "&details=https%3A%2F%2Fsocrates-conference.de");
    }

    /**
     * The whole reason the link exists rather than a UTC {@code Z} form: a 9 AM start in Berlin must
     * arrive in Google as 9 AM in Berlin, not as the 7 AM UTC instant behind it — which Google would
     * re-display in whatever zone it thinks the reader is in.
     */
    @Test
    void timesAreTheVenuesWallClockNeverUtc() {
        String href = GoogleCalendarLink.href(
                "Dinner", berlin(2026, 9, 15, 19, 30), berlin(2026, 9, 15, 22, 0), "", "");

        assertThat(href)
                .contains("&dates=20260915T193000/20260915T220000")
                .doesNotContain("20260915T173000")
                .doesNotContain("Z/");
    }

    /**
     * {@code ctz} is one parameter for the pair, so an end recorded in another zone is re-read in
     * the start's — otherwise the two halves of {@code dates} would be measured against different
     * clocks and Google would silently believe the wrong one.
     */
    @Test
    void endIsFormattedInTheStartsZone() {
        ZonedTimestamp start = berlin(2026, 9, 15, 9, 0);
        ZonedTimestamp end = new ZonedTimestamp(
                LocalDateTime.of(2026, 9, 15, 3, 0).atZone(DENVER).toInstant(), DENVER);

        String href = GoogleCalendarLink.href("Odd one", start, end, "", "");

        // 03:00 in Denver is 11:00 in Berlin on that date.
        assertThat(href)
                .contains("&dates=20260915T090000/20260915T110000")
                .contains("&ctz=Europe%2FBerlin");
    }

    @Test
    void blankLocationAndDetailsAreOmittedEntirely() {
        String href = GoogleCalendarLink.href(
                "Bare", berlin(2026, 9, 15, 9, 0), berlin(2026, 9, 15, 10, 0), "", "");

        assertThat(href)
                .doesNotContain("&location=")
                .doesNotContain("&details=");
    }

    @Test
    void titleWithSpacesAndPunctuationIsUrlEncoded() {
        String href = GoogleCalendarLink.href(
                "Ted & Friends: dinner",
                berlin(2026, 9, 15, 19, 0), berlin(2026, 9, 15, 21, 0), "", "");

        assertThat(href)
                .contains("&text=Ted+%26+Friends%3A+dinner");
    }

    @Test
    void iconLinkOpensInANewTabAndCarriesItsReasonInATitle() {
        String html = GoogleCalendarLink.icon("https://example.com/x", "Add Dinner to Google Calendar")
                                        .render();

        assertThat(html)
                .contains("class=\"gcal-add\"")
                .contains("href=\"https://example.com/x\"")
                .contains("title=\"Add Dinner to Google Calendar\"")
                .contains("target=\"_blank\"")
                .contains("rel=\"noopener\"")
                .contains("viewBox=\"0 0 640 640\"");
    }

    /** The label's underline is permanent (site.css) — the icon plus the words, never a hover. */
    @Test
    void labelledLinkRendersTheWordsBesideTheIcon() {
        String html = GoogleCalendarLink.labelled("https://example.com/x", "Add to Google", "Add it")
                                        .render();

        assertThat(html)
                .contains("<span class=\"gcal-label\">Add to Google</span>")
                .contains("viewBox=\"0 0 640 640\"");
    }

    private static ZonedTimestamp berlin(int year, int month, int day, int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(year, month, day, hour, minute), BERLIN);
    }
}
