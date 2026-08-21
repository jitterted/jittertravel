package dev.ted.jittertravel.web;

import com.microsoft.playwright.Locator;
import dev.ted.jittertravel.application.ScheduleProblem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JS-behavior tests for the fix menus on {@code /schedule-problems}: they open on tap, close on an
 * outside click or Escape, and opening one closes the others.
 * <p>
 * The markup is the <strong>real renderer's output</strong>, loaded straight into a browser with no
 * server, Spring, DB or auth — so only the shared {@link DisclosureMenu} script is under test. Its
 * other user, the calendar day menu, has its own tier test; this one exists because the two must
 * not drift, and the way to prove they have not is to exercise the same script from both.
 */
class ProblemFixMenuJsTest extends JsBehaviorTest {

    @Test
    void aFixMenuStartsClosedAndOpensWhenItsControlIsTapped() {
        loadRendered(listWith(missingHotel("London"), missingHotel("Berlin")));

        Locator menu = page.locator(".disclosure-menu").first();
        assertThat(menu.getAttribute("open"))
                .as("a menu that starts open would cover the card below it")
                .isNull();

        menu.locator("summary").click();

        assertThat(menu.getAttribute("open")).isNotNull();
    }

    @Test
    void clickingOutsideClosesAnOpenMenu() {
        loadRendered(listWith(missingHotel("London")));
        Locator menu = page.locator(".disclosure-menu").first();
        menu.locator("summary").click();

        page.locator("h1").click();

        assertThat(menu.getAttribute("open")).isNull();
    }

    @Test
    void escapeClosesAnOpenMenu() {
        loadRendered(listWith(missingHotel("London")));
        Locator menu = page.locator(".disclosure-menu").first();
        menu.locator("summary").click();

        menu.locator("summary").press("Escape");

        assertThat(menu.getAttribute("open")).isNull();
    }

    /** Two open menus overlap each other on a column of cards, and both have to be dismissed. */
    @Test
    void openingOneMenuClosesTheOther() {
        loadRendered(listWith(missingHotel("London"), missingHotel("Berlin")));
        Locator first = page.locator(".disclosure-menu").nth(0);
        Locator second = page.locator(".disclosure-menu").nth(1);
        first.locator("summary").click();

        second.locator("summary").click();

        assertThat(first.getAttribute("open"))
                .as("opening the second must close the first")
                .isNull();
        assertThat(second.getAttribute("open")).isNotNull();
    }

    /** The same script, driving the calendar view's band menus — the two must not drift apart. */
    @Test
    void aBandMenuOnTheCalendarViewBehavesTheSameWay() {
        loadRendered(ProblemCalendarRenderer.render(
                List.of(missingHotel("London")), List.of(), LocalDate.of(2026, 7, 15)));

        Locator menu = page.locator(".disclosure-menu").first();
        menu.locator("summary").click();
        assertThat(menu.getAttribute("open")).isNotNull();

        menu.locator("summary").press("Escape");
        assertThat(menu.getAttribute("open")).isNull();
    }

    private static String listWith(ScheduleProblem... problems) {
        return ScheduleProblemsRenderer.render(List.of(problems));
    }

    private static ScheduleProblem missingHotel(String city) {
        return new ScheduleProblem.MissingHotel(
                city, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 18), "");
    }
}
