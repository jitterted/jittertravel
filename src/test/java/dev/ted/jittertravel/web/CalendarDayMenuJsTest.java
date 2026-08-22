package dev.ted.jittertravel.web;

import com.microsoft.playwright.Locator;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryDetails;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JS-behavior tests for the owner future-day disclosure menu on the calendar (the inline
 * {@code DAY_MENU_SCRIPT} in {@link CalendarRenderer}). The menu is a native
 * {@code <details>}, which on its own never dismisses — the script adds the three popup
 * behaviors it lacks: only one open at a time, close on outside-click, close on Escape.
 * These only exist once a browser runs the script, so no renderer or @WebMvcTest reaches them.
 *
 * <p>Rendered as OWNER on a range of future days, so each future day cell is a
 * {@code <details class="disclosure-menu">}.
 */
class CalendarDayMenuJsTest extends JsBehaviorTest {

    // Day menus render only on days strictly after "today"; the default range runs two weeks
    // past today, so there are ample future day cells (hence day menus) to open.
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    private static List<CalendarEntry> oneUpcomingConference() {
        CalendarEntry upcoming = new CalendarEntry(
                LocalDateTime.of(2026, 6, 20, 9, 0),
                LocalDateTime.of(2026, 6, 20, 17, 0),
                "Upcoming Conference", List.of(),
                new EntryDetails.Conference(null));
        return List.of(upcoming);
    }

    /** OWNER render: future day cells are {@code <details class="disclosure-menu">} disclosures. */
    private String ownerCalendarHtml() {
        return CalendarRenderer.render(oneUpcomingConference(), TODAY, false, true);
    }

    private Locator dayMenus() {
        return page.locator(".disclosure-menu");
    }

    private boolean isOpen(Locator menu) {
        return (Boolean) menu.evaluate("el => el.open");
    }

    private void openMenu(Locator menu) {
        menu.locator("summary").click();
    }

    @Test
    void clickingOutsideAnOpenDayMenuClosesIt() {
        loadRendered(ownerCalendarHtml());
        Locator menu = dayMenus().first();

        openMenu(menu);
        assertThat(isOpen(menu))
                .as("clicking the day number opens its menu")
                .isTrue();

        // Cancel navigation in a capture listener so clicking a link elsewhere leaves the page
        // put; the document-level outside-click listener still runs (preventDefault doesn't stop
        // propagation). The day-number summary is not a link, so opening above was unaffected.
        page.evaluate("document.addEventListener('click', e => e.preventDefault(), true)");
        page.locator("nav a").first().click();

        assertThat(isOpen(menu))
                .as("clicking outside the menu dismisses it")
                .isFalse();
    }

    @Test
    void pressingEscapeClosesAnOpenDayMenu() {
        loadRendered(ownerCalendarHtml());
        Locator menu = dayMenus().first();

        openMenu(menu);
        assertThat(isOpen(menu))
                .as("menu open before Escape")
                .isTrue();

        // Press Escape with the open menu's summary focused; the keydown bubbles to the
        // document listener. Pressing via the summary keeps focus deterministic.
        menu.locator("summary").press("Escape");

        assertThat(isOpen(menu))
                .as("Escape dismisses the open menu")
                .isFalse();
    }

    @Test
    void openingASecondDayMenuClosesTheFirstInsteadOfStacking() {
        loadRendered(ownerCalendarHtml());
        Locator first = dayMenus().nth(0);
        Locator second = dayMenus().nth(1);

        openMenu(first);
        openMenu(second);

        assertThat(isOpen(first))
                .as("opening a second day menu closes the first — no stacked popups")
                .isFalse();
        assertThat(isOpen(second))
                .as("the just-opened menu stays open")
                .isTrue();
    }
}
