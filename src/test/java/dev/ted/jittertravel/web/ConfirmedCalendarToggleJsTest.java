package dev.ted.jittertravel.web;

import com.microsoft.playwright.Locator;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JS-behavior tests for the calendar "Show/Hide past weeks" toggle (the inline
 * {@code TOGGLE_SCRIPT} in {@link ConfirmedCalendarRenderer}). These cover the
 * integration between the global toggle and per-week clicks — behavior that only
 * exists once a browser runs the script, so no renderer or @WebMvcTest could reach it.
 *
 * <p>Most cases use a public-user render so day labels are spans rather than {@code <a>}
 * links; that lets a click land anywhere on a collapsed week without the script's
 * "let day links navigate" guard swallowing it. The OWNER cases below cover the
 * logged-in render where day labels <em>are</em> links. The toggle JS is identical
 * either way.
 */
class ConfirmedCalendarToggleJsTest extends JsBehaviorTest {

    // Monday. Weeks whose Saturday falls before this week's Sunday (Jun 14) collapse.
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    /**
     * One conference in a past week (Jun 8 → collapsed week of Jun 7–13) and one in the
     * current week (Jun 16). The past entry makes the renderer emit the global toggle,
     * and the rendered grid ends up with exactly two collapsed weeks.
     */
    private static List<CalendarEntry> twoConferences() {
        CalendarEntry pastConference = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 8, 9, 0),
                LocalDateTime.of(2026, 6, 8, 17, 0),
                "Past Conference", List.of(), null, null, "");
        CalendarEntry upcomingConference = new CalendarEntry(
                EntryKind.CONFERENCE,
                LocalDateTime.of(2026, 6, 16, 9, 0),
                LocalDateTime.of(2026, 6, 16, 17, 0),
                "Upcoming Conference", List.of(), null, null, "");
        return List.of(pastConference, upcomingConference);
    }

    /** Public render: day labels are spans, so a collapsed week is clickable anywhere. */
    private String publicCalendarHtml() {
        return ConfirmedCalendarRenderer.render(twoConferences(), TODAY, true);
    }

    /** OWNER render: day labels are {@code <a>} links to the day's itinerary. */
    private String ownerCalendarHtml() {
        return ConfirmedCalendarRenderer.render(twoConferences(), TODAY, false);
    }

    private Locator toggleAll() {
        return page.locator("#toggle-all-weeks");
    }

    private Locator collapsedWeeks() {
        return page.locator(".calendar-week--collapsed");
    }

    private boolean isExpanded(Locator week) {
        return week.getAttribute("class").contains("is-expanded");
    }

    @Test
    void globalToggleExpandsThenCollapsesAllPastWeeksAndRelabels() {
        loadRendered(publicCalendarHtml());
        Locator weeks = collapsedWeeks();
        assertThat(weeks.count())
                .as("the two past weeks (Jun 7-13 has the entry, May 31-Jun 6 is empty) both collapse")
                .isEqualTo(2);
        assertThat(toggleAll().textContent()).isEqualTo("Show past weeks");

        toggleAll().click();

        assertThat(isExpanded(weeks.nth(0)))
                .as("first past week expanded by global toggle")
                .isTrue();
        assertThat(isExpanded(weeks.nth(1)))
                .as("second past week expanded by global toggle")
                .isTrue();
        assertThat(toggleAll().textContent()).isEqualTo("Hide past weeks");

        toggleAll().click();

        assertThat(isExpanded(weeks.nth(0)))
                .as("first past week collapsed again by global toggle")
                .isFalse();
        assertThat(isExpanded(weeks.nth(1)))
                .as("second past week collapsed again by global toggle")
                .isFalse();
        assertThat(toggleAll().textContent()).isEqualTo("Show past weeks");
    }

    @Test
    void collapsingOneWeekAfterGlobalExpandResyncsTheGlobalLabel() {
        // The regression: after "Hide past weeks" expands everything, collapsing a single
        // week individually must flip the global label back to "Show past weeks".
        loadRendered(publicCalendarHtml());
        Locator weeks = collapsedWeeks();

        toggleAll().click();
        assertThat(toggleAll().textContent()).isEqualTo("Hide past weeks");

        weeks.nth(0).click();

        assertThat(isExpanded(weeks.nth(0)))
                .as("individually clicked week collapsed")
                .isFalse();
        assertThat(isExpanded(weeks.nth(1)))
                .as("other week left expanded")
                .isTrue();
        assertThat(toggleAll().textContent())
                .as("a collapsed week remains, so the global control offers to show all again")
                .isEqualTo("Show past weeks");
    }

    @Test
    void expandingEveryWeekIndividuallyFlipsGlobalLabelToHide() {
        // The mirror case: the global label only becomes "Hide past weeks" once the LAST
        // collapsed week is expanded by individual clicks.
        loadRendered(publicCalendarHtml());
        Locator weeks = collapsedWeeks();

        weeks.nth(0).click();
        assertThat(toggleAll().textContent())
                .as("one week still collapsed, so label stays 'Show'")
                .isEqualTo("Show past weeks");

        weeks.nth(1).click();
        assertThat(toggleAll().textContent())
                .as("all weeks now expanded individually, so label becomes 'Hide'")
                .isEqualTo("Hide past weeks");
    }

    // --- OWNER (logged-in) render: day labels are <a> links --------------------------

    @Test
    void ownerViewTogglesAPastWeekViaItsCountBadgeAndStaysInSyncWithGlobal() {
        // In the OWNER render each day cell is an <a> link, so the day-count badge (a span,
        // visible only while a week is collapsed) is the non-link affordance for expanding.
        loadRendered(ownerCalendarHtml());
        // The only collapsed week carrying an entry — and therefore a badge — is Jun 7-13.
        Locator entryWeek = page.locator(".calendar-week--collapsed:has(.day-badge)");
        assertThat(toggleAll().textContent()).isEqualTo("Show past weeks");

        entryWeek.locator(".day-badge").first().click();

        assertThat(isExpanded(entryWeek))
                .as("clicking the count badge expanded the past week even though day cells are links")
                .isTrue();
        assertThat(toggleAll().textContent())
                .as("one past week is still collapsed, so the global label stays 'Show'")
                .isEqualTo("Show past weeks");

        toggleAll().click();

        assertThat(toggleAll().textContent())
                .as("global toggle expands the remaining week, so label flips to 'Hide'")
                .isEqualTo("Hide past weeks");
    }

    @Test
    void ownerViewClickingADayLinkNavigatesInsteadOfTogglingTheWeek() {
        // The script's "let day links navigate" guard: clicking the day's <a> must NOT
        // toggle the week. Cancel the link's default navigation in a capture listener so the
        // page stays put — the click still bubbles to the week handler, where the guard runs.
        loadRendered(ownerCalendarHtml());
        page.evaluate("document.addEventListener('click', e => e.preventDefault(), true)");
        Locator entryWeek = page.locator(".calendar-week--collapsed:has(.day-badge)");

        entryWeek.locator("a.day-number").first().click();

        assertThat(isExpanded(entryWeek))
                .as("clicking a day link must not toggle the week (it navigates instead)")
                .isFalse();
        assertThat(toggleAll().textContent())
                .as("the day-link click left the past week collapsed, so the label is unchanged")
                .isEqualTo("Show past weeks");
    }
}