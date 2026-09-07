package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <strong>A closed disclosure menu must be {@code display: none}, so find-in-page cannot expand
 * it.</strong>
 * <p>
 * This test exists because the rule it guards looks like dead CSS. A closed {@code <details>}
 * already hides its panel, so the obvious reading of
 * {@code .disclosure-menu:not([open]) .disclosure-menu-list { display: none; }} is "the browser
 * does that anyway" — and deleting it is a one-line tidy-up that no other test in the tree would
 * notice.
 * <p>
 * What it actually buys: Chrome hides a closed {@code <details>} with {@code content-visibility},
 * which leaves the panel's text findable. Find-in-page searches inside it and <em>auto-expands</em>
 * the menu holding a match. Reported by Ted on 2026-09-07 as Cmd+F on {@code /calendar} popping the
 * year overview open — its panel carries a {@code S M T W T F S} header and a {@code Sep 2026}
 * label per mini month and sits in the nav near the top of the document, so the first character
 * typed into the find bar matched inside it. Every day menu and every fix menu on
 * {@code /schedule-problems} shared the bug.
 * <p>
 * <strong>Find-in-page is browser chrome, not the DOM, so the js tier cannot drive it</strong> —
 * there is no Playwright call that opens the find bar. A string assertion is therefore the only
 * available guard, which is exactly why it is written down rather than left to the comment.
 * <p>
 * The other half of the rule — that an <em>open</em> menu is still visible — is covered by
 * {@link YearOverviewJsTest}, and by that one alone. Dropping the {@code :not([open])} so the panel
 * is hidden in both states fails exactly three of its cases on a Playwright timeout, because they
 * click the close button and a month link <em>inside</em> the panel and Playwright will not click
 * what is not displayed. {@link CalendarDayMenuJsTest} and {@link ProblemFixMenuJsTest} stay green
 * through that mutation: they assert {@code el.open} and never reach into the panel. Worth knowing
 * before trusting them as cover for a visibility change.
 */
class DisclosureMenuTest {

    private static final String CLOSED_MENU_RULE =
            ".disclosure-menu:not([open]) .disclosure-menu-list { display: none; }";

    @Test
    void closedMenuPanelIsDisplayNoneSoFindInPageCannotExpandIt() {
        assertThat(DisclosureMenu.CSS)
                .as("a closed menu's panel must be display:none, or find-in-page expands it")
                .contains(CLOSED_MENU_RULE);
    }

    /**
     * The constant is only half the claim: a page could ship the menu markup without the stylesheet
     * that hides it. The calendar is where the bug was reported, and it renders the year overview
     * and a menu on every future day.
     */
    @Test
    void theRuleReachesTheCalendarPageThatShipsTheMenus() {
        String page = CalendarRenderer.render(List.of(), LocalDate.of(2026, 9, 7), false, true);

        assertThat(page)
                .as("the calendar renders disclosure menus, so it must ship the rule that hides them")
                .contains("class=\"disclosure-menu")
                .contains(CLOSED_MENU_RULE);
    }
}
