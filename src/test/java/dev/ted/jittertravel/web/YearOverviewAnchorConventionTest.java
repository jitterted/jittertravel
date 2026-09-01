package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryDetails;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <strong>Every month the year overview links to must resolve to an anchor id emitted in the same
 * page.</strong>
 * <p>
 * This is the test to insist on. Every jump in this feature is a scroll — there is no
 * {@code ?from=}/{@code ?to=} fallback and no server round trip on any path — so a link to an id
 * nobody emitted produces <em>no error, no navigation, and nothing in the console</em>. It is the
 * one failure mode that is invisible until Ted taps a month and the page sits still.
 * <p>
 * Both sides are derived from the render rather than from a fixture, so a future change to how the
 * range is computed, or to how weeks are filed under months, fails here instead of in Ted's hands.
 * Sibling in spirit to {@code TimeFilterToggleConventionTest}.
 */
class YearOverviewAnchorConventionTest {

    private static final Pattern MONTH_LINK = Pattern.compile("href=\"#(m-\\d{4}-\\d{2})\"");
    private static final Pattern ANCHOR_ID = Pattern.compile("id=\"(m-\\d{4}-\\d{2})\"");

    private static String page(LocalDate today, LocalDate from, LocalDate to) {
        CalendarEntry entry = new CalendarEntry(
                today.plusDays(3).atTime(9, 0), today.plusDays(5).atTime(17, 0),
                "A conference", List.of(), new EntryDetails.Conference(null, false, null));
        return CalendarRenderer.render(List.of(entry), today, false, true, from, to);
    }

    private static Set<String> matches(Pattern pattern, String html) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Test
    void everyMonthLinkedFromTheOverlayHasAnAnchorOnThePage() {
        String html = page(LocalDate.of(2026, 9, 1), null, null);

        Set<String> linked = matches(MONTH_LINK, html);
        assertThat(linked).isNotEmpty();
        assertThat(matches(ANCHOR_ID, html))
                .as("a linked month with no anchor is a silently dead click")
                .containsAll(linked);
    }

    /**
     * The arrangement that broke anchoring the overlay to the <em>month bands</em>, kept as a
     * standing case because it is the one that would fail if anyone ever moves the ids back there.
     * <p>
     * A week is filed under the month its <strong>Sunday</strong> falls in, and {@code gridEnd} is a
     * Saturday — so a {@code gridEnd} landing on the 1st–5th of a month leaves that month with days
     * on the page and no band at all. Roughly one Saturday in six. The month-start day cells have no
     * such hole: {@code gridStart} itself counts as a month start, and the grid is contiguous, so
     * every later month has its 1st inside it.
     */
    @Test
    void aTailMonthWithNoMonthBandStillHasItsAnchor() {
        // to = Mon 2026-11-30 rounds up to Sat 2026-12-05, whose week begins Sun 2026-11-29.
        String html = page(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30));

        assertThat(html)
                .as("December's days are on the page, and so is its anchor")
                .contains("id=\"m-2026-12\"");

        Set<String> linked = matches(MONTH_LINK, html);
        assertThat(linked).contains("m-2026-12");
        assertThat(matches(ANCHOR_ID, html)).containsAll(linked);
    }

    @Test
    void theOverlayCoversEveryMonthTheGridDrawsNeitherMoreNorFewer() {
        String html = page(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30));

        // gridStart = Sun 2026-08-30 (rounded back from Sep 1), gridEnd = Sat 2026-12-05.
        assertThat(matches(MONTH_LINK, html))
                .containsExactly("m-2026-08", "m-2026-09", "m-2026-10", "m-2026-11", "m-2026-12");
    }

    @Test
    void noAnchorIdIsEmittedTwice() {
        String html = page(LocalDate.of(2026, 9, 1), null, null);

        for (String id : matches(ANCHOR_ID, html)) {
            assertThat(html.split(Pattern.quote("id=\"" + id + "\""), -1).length - 1)
                    .as("duplicate id %s — a jump would land on whichever the browser picks first", id)
                    .isEqualTo(1);
        }
    }
}
