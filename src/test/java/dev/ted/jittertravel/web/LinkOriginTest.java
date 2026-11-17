package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LinkOriginTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 10);
    private static final WayBack LIST = new WayBack("/booked-hotels", "the list");

    @ParameterizedTest
    @CsvSource({
            "problems-list,     /schedule-problems?view=list,     schedule problems",
            "problems-calendar, /schedule-problems?view=calendar, schedule problems",
            "itinerary,         /itinerary?date=2026-06-10,        the itinerary",
            "calendar,          /calendar?day=2026-06-10,          the calendar",
    })
    void eachOriginLeadsBackToItsSurfaceOnTheDayTheLinkWasClickedOn(String from, String href, String place) {
        assertThat(LinkOrigin.wayBack(from, "2026-06-10", LIST))
                .as("way back for from=" + from)
                .isEqualTo(new WayBack(href, place));
    }

    @ParameterizedTest
    @CsvSource({
            "itinerary, /itinerary",
            "calendar,  /calendar",
    })
    void withoutADayTheSurfaceOpensWhereItUsuallyDoes(String from, String href) {
        assertThat(LinkOrigin.wayBack(from, null, LIST).href())
                .as("no day for from=" + from)
                .isEqualTo(href);
    }

    @ParameterizedTest
    @ValueSource(strings = {"yesterday", "2026-13-01", "/evil.example", "2026-06-10/../../admin"})
    void aDayThatIsNotADateIsDroppedRatherThanEchoed(String day) {
        assertThat(LinkOrigin.wayBack("calendar", day, LIST).href())
                .as("garbage day " + day)
                .isEqualTo("/calendar");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void noOriginFallsBackToThePagesOwnWayOut(String from) {
        assertThat(LinkOrigin.wayBack(from, "2026-06-10", LIST))
                .as("no origin")
                .isEqualTo(LIST);
    }

    @Test
    void anUnknownOriginResolvesThroughTheEnumAndNeverBecomesAPath() {
        assertThat(LinkOrigin.wayBack("//evil.example", "2026-06-10", LIST).href())
                .as("unknown from is not echoed")
                .isEqualTo("/schedule-problems?view=calendar");
    }

    /** A fix link with no {@code from} still gets a Back link: the view the report opens in. */
    @Test
    void anAbsentOriginDefaultsToTheProblemCalendarForTheBanner() {
        assertThat(LinkOrigin.fromParam(null))
                .as("absent from, for the banner's Back link")
                .isEqualTo(LinkOrigin.PROBLEM_CALENDAR);
    }

    @Test
    void theOldProblemListValueNoLongerNamesTheList() {
        assertThat(LinkOrigin.fromParam("list"))
                .as("'list' was renamed to 'problems-list' and now falls back")
                .isEqualTo(LinkOrigin.PROBLEM_CALENDAR);
    }

    @Test
    void aLinkCarriesItsOriginAndTheDayItWasDrawnOn() {
        assertThat(LinkOrigin.CALENDAR.linkFrom("/booked-hotels/abc/cancel", DAY))
                .as("calendar bin href")
                .isEqualTo("/booked-hotels/abc/cancel?from=calendar&day=2026-06-10");
        assertThat(LinkOrigin.ITINERARY.linkFrom("/ground-transfers/xyz/cancel", DAY))
                .as("itinerary bin href")
                .isEqualTo("/ground-transfers/xyz/cancel?from=itinerary&day=2026-06-10");
    }

    /** The day menu's links already carry {@code ?date=} to seed the form, and keep it. */
    @Test
    void aLinkWithAQueryAlreadyKeepsItAndAddsTheOrigin() {
        assertThat(LinkOrigin.CALENDAR.linkFrom("/plan-private-event?date=2026-06-10", DAY))
                .as("calendar day-menu href")
                .isEqualTo("/plan-private-event?date=2026-06-10&from=calendar&day=2026-06-10");
    }

    @ParameterizedTest
    @CsvSource({
            "problems-list, 2026-06-10, /schedule-problems?view=list",
            "itinerary,     2026-06-10, /itinerary?date=2026-06-10",
            "calendar,      2026-06-10, /calendar?day=2026-06-10",
            "calendar,      garbage,    /calendar",
            "calendar,      ,           /calendar",
            "'',            2026-06-10, /planned-private-events",
            ",              2026-06-10, /planned-private-events",
    })
    void aSuccessfulFormReturnsToTheDayItWasStartedFromOrElseToItsList(String from, String day, String href) {
        assertThat(LinkOrigin.returnTo(from, day, "/planned-private-events"))
                .as("return for from=" + from + ", day=" + day)
                .isEqualTo(href);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-06-10, 2026-06-10",
            "garbage,",
            ",",
    })
    void aCarriedDayIsPassedOnOnlyWhenItIsADate(String raw, String expected) {
        assertThat(LinkOrigin.validDay(raw))
                .as("validated day for " + raw)
                .isEqualTo(expected);
    }
}
