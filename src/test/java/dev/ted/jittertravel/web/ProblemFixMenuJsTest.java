package dev.ted.jittertravel.web;

import com.microsoft.playwright.Locator;
import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JS-behavior tests for the fix menus on {@code /schedule-problems}: they open on tap, close on an
 * outside click or Escape, and opening one closes the others.
 * <p>
 * Menus are now the exception on both views — the dropdown rule keeps them for more than three
 * choices, or where space is constrained — so these fixtures are the two cases that still have
 * one: a hotel booked four ways on the list, and a travel gap's three answers on a band.
 * <p>
 * The markup is the <strong>real renderer's output</strong>, loaded straight into a browser with no
 * server, Spring, DB or auth — so only the shared {@link DisclosureMenu} script is under test. Its
 * other user, the calendar day menu, has its own tier test; this one exists because the two must
 * not drift, and the way to prove they have not is to exercise the same script from both.
 */
class ProblemFixMenuJsTest extends JsBehaviorTest {

    @Test
    void aFixMenuStartsClosedAndOpensWhenItsControlIsTapped() {
        loadRendered(listWith(duplicateHotel("Hamburg"), duplicateHotel("Soltau")));

        Locator menu = page.locator(".disclosure-menu").first();
        assertThat(menu.getAttribute("open"))
                .as("a menu that starts open would cover the card below it")
                .isNull();

        menu.locator("summary").click();

        assertThat(menu.getAttribute("open")).isNotNull();
    }

    @Test
    void clickingOutsideClosesAnOpenMenu() {
        loadRendered(listWith(duplicateHotel("Hamburg")));
        Locator menu = page.locator(".disclosure-menu").first();
        menu.locator("summary").click();

        page.locator("h1").click();

        assertThat(menu.getAttribute("open")).isNull();
    }

    @Test
    void escapeClosesAnOpenMenu() {
        loadRendered(listWith(duplicateHotel("Hamburg")));
        Locator menu = page.locator(".disclosure-menu").first();
        menu.locator("summary").click();

        menu.locator("summary").press("Escape");

        assertThat(menu.getAttribute("open")).isNull();
    }

    /** Two open menus overlap each other on a column of cards, and both have to be dismissed. */
    @Test
    void openingOneMenuClosesTheOther() {
        loadRendered(listWith(duplicateHotel("Hamburg"), duplicateHotel("Soltau")));
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
                List.of(missingTravel("London")), List.of(), LocalDate.of(2026, 7, 15)));

        Locator menu = page.locator(".disclosure-menu").first();
        menu.locator("summary").click();
        assertThat(menu.getAttribute("open")).isNotNull();

        menu.locator("summary").press("Escape");
        assertThat(menu.getAttribute("open")).isNull();
    }

    private static String listWith(ScheduleProblem... problems) {
        return ScheduleProblemsRenderer.render(List.of(problems));
    }

    /**
     * The one problem the <em>list</em> page still gives a menu: four hotels booked over the same
     * nights is four cancel links, and four is more than the three the dropdown rule allows as
     * plain links. Every other card on that page now lists its fixes.
     */
    private static ScheduleProblem duplicateHotel(String city) {
        return new ScheduleProblem.DuplicateHotel(
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 17),
                List.of(stay("Reichshof", city), stay("Park Hotel", city),
                        stay("Vier Jahreszeiten", city), stay("Hafen", city)));
    }

    private static ScheduleProblem.DuplicateStay stay(String hotelName, String city) {
        return new ScheduleProblem.DuplicateStay(
                HotelBookingId.random(), hotelName, city, BookingIntent.FINAL);
    }

    /**
     * A travel gap has three answers, which the space-constrained calendar band still packs into a
     * menu — the list card spells the same three out as links.
     */
    private static ScheduleProblem missingTravel(String fromCity) {
        return new ScheduleProblem.MissingTravel(
                fromCity, zoned(2026, 7, 15, 11, 30), "Lone Tree", zoned(2026, 7, 16, 9, 0));
    }

    private static ZonedTimestamp zoned(int year, int month, int day, int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(year, month, day, hour, minute),
                ZoneId.of("America/Denver"));
    }
}
