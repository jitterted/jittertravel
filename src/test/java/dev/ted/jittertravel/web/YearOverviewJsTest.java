package dev.ted.jittertravel.web;

import com.microsoft.playwright.Locator;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryDetails;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JS-behavior tests for the year overview panel — the parts that only exist once a browser runs the
 * script, so no renderer or {@code @WebMvcTest} reaches them.
 * <p>
 * Outside-click, Escape and one-open-at-a-time come from {@code DisclosureMenu.SCRIPT}, which
 * {@code CalendarDayMenuJsTest} already covers; what is under test here is what the panel adds on
 * top — closing on a chosen month, focus returning to the trigger, and the "you are here" marking,
 * which is recomputed on every open and never stored.
 */
class YearOverviewJsTest extends JsBehaviorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    /**
     * A conference far enough out to stretch the range across several months, so the panel holds
     * enough minis for "which one is marked?" to be a real question.
     */
    private String ownerCalendarHtml() {
        CalendarEntry farOut = new CalendarEntry(
                LocalDate.of(2026, 12, 8).atTime(9, 0),
                LocalDate.of(2026, 12, 11).atTime(17, 0),
                "A conference", List.of(),
                new EntryDetails.Conference(null, false, null));
        return CalendarRenderer.render(List.of(farOut), TODAY, false, true);
    }

    private Locator panel() {
        return page.locator(".year-overview");
    }

    private Locator trigger() {
        return page.locator(".year-overview-trigger");
    }

    private boolean isOpen() {
        return (Boolean) panel().evaluate("panel => panel.open");
    }

    /**
     * Opens the panel <em>without</em> clicking, for the tests that care where the page is scrolled
     * to.
     * <p>
     * <strong>Why not a click:</strong> this tier loads markup with {@code setContent} and runs no
     * server, so the linked {@code /site.css} never arrives — and {@code position: sticky} on
     * {@code .view-nav} lives there. The bar therefore scrolls away in these tests, and Playwright
     * scrolls the page back to the top to bring the trigger into view before clicking it, resetting
     * the very scroll position under test. Setting {@code open} fires the same {@code toggle} event
     * the click would, which is the thing actually being tested.
     */
    private void openWithoutScrolling() {
        panel().evaluate("panel => panel.open = true");
    }

    private void closeWithoutScrolling() {
        panel().evaluate("panel => panel.open = false");
    }

    /** Puts the month's anchor at its resting place, exactly as choosing that month would. */
    private void scrollTo(String monthAnchorId) {
        page.locator("#" + monthAnchorId).evaluate("cell => cell.scrollIntoView()");
    }

    @Test
    void theTriggerOpensAndClosesThePanel() {
        loadRendered(ownerCalendarHtml());

        assertThat(isOpen()).isFalse();
        trigger().click();
        assertThat(isOpen()).isTrue();
        trigger().click();
        assertThat(isOpen()).isFalse();
    }

    @Test
    void escapeClosesThePanelAndReturnsFocusToTheTrigger() {
        loadRendered(ownerCalendarHtml());
        trigger().click();

        page.keyboard().press("Escape");

        assertThat(isOpen()).isFalse();
        assertThat((Boolean) page.evaluate(
                "() => document.activeElement === document.querySelector('.year-overview-trigger')"))
                .as("focus must come back to the control that opened the panel")
                .isTrue();
    }

    @Test
    void clickingOutsideClosesThePanel() {
        loadRendered(ownerCalendarHtml());
        trigger().click();

        page.locator(".calendar-container").click(
                new Locator.ClickOptions().setPosition(5, 5));

        assertThat(isOpen()).isFalse();
    }

    @Test
    void theCloseButtonClosesThePanel() {
        loadRendered(ownerCalendarHtml());
        trigger().click();

        page.locator(".yo-close").click();

        assertThat(isOpen()).isFalse();
    }

    @Test
    void choosingAMonthClosesThePanelAndScrollsTheCalendarToIt() {
        loadRendered(ownerCalendarHtml());
        double before = ((Number) page.evaluate("() => window.scrollY")).doubleValue();

        trigger().click();
        page.locator("a[href='#m-2026-12']").click();

        assertThat(isOpen())
                .as("the panel gets out of the way once it has done its job")
                .isFalse();
        assertThat(((Number) page.evaluate("() => window.scrollY")).doubleValue())
                .as("the page moved to December rather than staying put")
                .isGreaterThan(before);
    }

    @Test
    void theJumpedToWeekAcknowledgesTheJump() {
        loadRendered(ownerCalendarHtml());
        trigger().click();
        page.locator("a[href='#m-2026-12']").click();

        // Scrolling a long page to a place that looks like every other place is disorienting.
        assertThat(page.locator(".calendar-week.is-jump-target").count()).isEqualTo(1);
    }

    /**
     * <strong>D7, and the case that fails if "you are here" is ever quietly reduced to
     * "today".</strong> A map that does not show where you are standing is a menu.
     */
    @Test
    void openingThePanelMarksTheMonthThePageIsActuallyShowing() {
        loadRendered(ownerCalendarHtml());

        // A month in the middle of the range — not today's, and not the first.
        scrollTo("m-2026-11");
        openWithoutScrolling();

        assertThat(page.locator(".yo-month.is-current").count()).isEqualTo(1);
        assertThat(page.locator(".yo-month.is-current").getAttribute("data-month"))
                .isEqualTo("2026-11");
    }

    @Test
    void itIsRecomputedOnEveryOpenRatherThanRemembered() {
        loadRendered(ownerCalendarHtml());

        scrollTo("m-2026-11");
        openWithoutScrolling();
        assertThat(page.locator(".yo-month.is-current").getAttribute("data-month")).isEqualTo("2026-11");
        closeWithoutScrolling();

        // Scroll somewhere else with the panel CLOSED: a stored position would go stale here, and
        // the panel would open on November while the page sat in December.
        scrollTo("m-2026-12");
        openWithoutScrolling();

        assertThat(page.locator(".yo-month.is-current").getAttribute("data-month")).isEqualTo("2026-12");
    }

    /**
     * The fallback. At scroll 0 no anchor is above the sticky offset yet — the ordinary landing
     * state — and marking nothing there is the "map with no you-are-here" failure the whole
     * decision exists to avoid.
     */
    @Test
    void atTheTopOfThePageTheFirstMonthIsMarkedRatherThanNone() {
        loadRendered(ownerCalendarHtml());
        page.evaluate("() => window.scrollTo(0, 0)");

        openWithoutScrolling();

        assertThat(page.locator(".yo-month.is-current").count()).isEqualTo(1);
        assertThat(page.locator(".yo-month.is-current").getAttribute("data-month"))
                .as("the grid starts in the week containing Aug 30, so August is the first mini")
                .isEqualTo("2026-08");
    }

    /**
     * The other end of the same rule. The last month or two can never reach their resting place —
     * there is not enough document below them to scroll — so a plain "last anchor above the offset"
     * would name an earlier month while Ted looks at December, at the one end of the range he
     * scrolled all the way to on purpose.
     */
    @Test
    void scrolledToTheBottomTheLastMonthIsMarkedEvenThoughItCannotReachItsRestingPlace() {
        loadRendered(ownerCalendarHtml());
        page.evaluate("() => window.scrollTo(0, document.documentElement.scrollHeight)");

        openWithoutScrolling();

        assertThat(page.locator(".yo-month.is-current").getAttribute("data-month"))
                .isEqualTo("2026-12");
    }

    @Test
    void aDayMenuLeftOpenDoesNotSurviveOpeningThePanel() {
        loadRendered(ownerCalendarHtml());

        // Both are .disclosure-menu, so DisclosureMenu's one-open-at-a-time applies across them —
        // which is also why the panel's z-index above the day menus is belt-and-braces.
        page.locator(".calendar-week .disclosure-menu > summary").first().click();
        trigger().click();

        assertThat(page.locator(".calendar-week .disclosure-menu[open]").count()).isZero();
        assertThat(isOpen()).isTrue();
    }
}
