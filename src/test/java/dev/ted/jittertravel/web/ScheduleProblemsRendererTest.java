package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleProblemsRendererTest {

    @Test
    void noProblemsRendersCleanMessage() {
        String html = ScheduleProblemsRenderer.render(List.of());

        assertThat(html).contains("No problems found");
    }

    @Test
    void pageCarriesTheSelectorWithListActive() {
        String html = ScheduleProblemsRenderer.render(List.of());

        assertThat(html)
                .contains("<div class=\"view-toggle\">")
                .contains("<a href=\"/schedule-problems?view=list\" class=\"active\">List</a>")
                .contains("<a href=\"/schedule-problems?view=calendar\">Calendar</a>");
    }

    @Test
    void missingTravelShowsCitiesAndTimes() {
        ScheduleProblem problem = new ScheduleProblem.MissingTravel(
                "London",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 14, 30), ZoneId.of("Europe/London")),
                "Berlin",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 3, 9, 0), ZoneId.of("Europe/Berlin"))
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("London → Berlin")
                .contains("Arrive <time")
                .contains(">Jul 1, 2:30 PM</time> — next leg departs <time")
                .contains(">Jul 3, 9:00 AM</time>");
    }

    @Test
    void missingTravelTimesRenderAsTimeElementsCarryingTheUtcInstant() {
        // Each end is in its own zone: the text is that end's wall-clock, the datetime attribute
        // its instant (London BST 14:30 -> 13:30Z, Berlin CEST 09:00 -> 07:00Z).
        ScheduleProblem problem = new ScheduleProblem.MissingTravel(
                "London",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 14, 30), ZoneId.of("Europe/London")),
                "Berlin",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 3, 9, 0), ZoneId.of("Europe/Berlin"))
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("<time datetime=\"2026-07-01T13:30:00Z\" data-fmt=\"MMM d, h:mm a\">"
                          + "Jul 1, 2:30 PM</time>")
                .contains("<time datetime=\"2026-07-03T07:00:00Z\" data-fmt=\"MMM d, h:mm a\">"
                          + "Jul 3, 9:00 AM</time>");
    }

    @Test
    void missingHotelShowsCityAndDates() {
        ScheduleProblem problem = new ScheduleProblem.MissingHotel(
                "Berlin",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 5),
                "JavaOne"
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("Berlin — for JavaOne")
                .contains("checking in on Wed, Jul 1")
                .contains("check out on Sun, Jul 5");
    }

    @Test
    void missingHotelWithNoConferenceNameOmitsConferencePart() {
        ScheduleProblem problem = new ScheduleProblem.MissingHotel(
                "Paris",
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 14),
                ""
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("Paris")
                .doesNotContain("— for");
    }

    @Test
    void schedulingConflictShowsGatheringNamesAndTimes() {
        ScheduleProblem problem = new ScheduleProblem.SchedulingConflict(
                gathering("Mob Session", "London", LocalDateTime.of(2026, 7, 15, 10, 0),
                          LocalDateTime.of(2026, 7, 15, 12, 0), "Europe/London"),
                gathering("Team Lunch", "London", LocalDateTime.of(2026, 7, 15, 11, 30),
                          LocalDateTime.of(2026, 7, 15, 13, 0), "Europe/London")
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("Mob Session conflicts with Team Lunch")
                .contains("Wed, Jul 15")
                .contains("10:00 AM")
                .contains("12:00 PM")
                .contains("overlaps")
                .contains("11:30 AM")
                .contains("1:00 PM");
    }

    @Test
    void crossZoneSchedulingConflictShowsEachGatheringsOwnDateAndCity() {
        // A San Francisco evening overlaps a Tokyo morning that falls on the *next* local day.
        // Reporting one shared date would put B's times under A's date — times that never
        // happened on that day at either venue.
        ScheduleProblem problem = new ScheduleProblem.SchedulingConflict(
                gathering("SF Java", "San Francisco", LocalDateTime.of(2026, 10, 3, 18, 0),
                          LocalDateTime.of(2026, 10, 3, 21, 0), "America/Los_Angeles"),
                gathering("Tokyo JUG", "Tokyo", LocalDateTime.of(2026, 10, 4, 9, 0),
                          LocalDateTime.of(2026, 10, 4, 12, 0), "Asia/Tokyo")
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("SF Java conflicts with Tokyo JUG")
                .contains("<time datetime=\"2026-10-04T01:00:00Z\" data-fmt=\"EEE, MMM d, h:mm a\">"
                          + "Sat, Oct 3, 6:00 PM</time>")
                .contains("<time datetime=\"2026-10-04T04:00:00Z\" data-fmt=\"h:mm a\">9:00 PM</time>")
                .contains("(San Francisco)")
                .contains("<time datetime=\"2026-10-04T00:00:00Z\" data-fmt=\"EEE, MMM d, h:mm a\">"
                          + "Sun, Oct 4, 9:00 AM</time>")
                .contains("<time datetime=\"2026-10-04T03:00:00Z\" data-fmt=\"h:mm a\">12:00 PM</time>")
                .contains("(Tokyo)");
    }

    @Test
    void differentCityConflictShowsNamesAndClearLink() {
        GatheringId gatheringId = GatheringId.random();
        ConferenceId conferenceId = ConferenceId.random();
        ScheduleProblem problem = new ScheduleProblem.DifferentCityConflict(
                "BRU JUG", "Brussels",
                "JavaOne", "Amsterdam",
                LocalDate.of(2026, 9, 16),
                gatheringId, conferenceId
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("BRU JUG")
                .contains("Brussels")
                .contains("JavaOne")
                .contains("Amsterdam")
                .contains("Sep 16")
                .contains("/clear-conflict")
                .contains(gatheringId.id().toString())
                .contains(conferenceId.id().toString());
    }

    // --- Fix menus (slice 5) ---

    /**
     * A missing hotel has exactly one answer, so the slot holds that link and not a menu wrapping
     * it: a one-item menu is a door in front of a door. The card keeps the slot and the chip, so
     * nothing moves within the column — every card in it is a missing hotel.
     */
    @Test
    void aMissingHotelCardOffersItsOneFixAsADirectLink() {
        String html = ScheduleProblemsRenderer.render(List.of(new ScheduleProblem.MissingHotel(
                "Johannesberg", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), "JCON")));

        assertThat(html)
                // The whole anchor, with & escaped as the markup really has it.
                .contains("<a href=\"/book-hotel?city=Johannesberg&amp;checkIn=2026-09-10&amp;checkOut=2026-09-14\" "
                          + "class=\"fix-summary\">Book hotel &rarr;</a>")
                .doesNotContain("<details class=\"disclosure-menu\">");
    }

    @Test
    void aDifferentCityConflictCardAlsoOffersItsOneFixAsADirectLink() {
        GatheringId gatheringId = GatheringId.random();
        ConferenceId conferenceId = ConferenceId.random();

        String html = ScheduleProblemsRenderer.render(List.of(new ScheduleProblem.DifferentCityConflict(
                "BRU JUG", "Brussels", "JavaOne", "Amsterdam",
                LocalDate.of(2026, 9, 16), gatheringId, conferenceId)));

        assertThat(html)
                .contains("class=\"fix-summary\">Clear this conflict &rarr;</a>")
                .doesNotContain("<details class=\"disclosure-menu\">");
    }

    @Test
    void aTravelCardOffersFlightTrainAndGroundTransferInThatOrder() {
        String html = ScheduleProblemsRenderer.render(List.of(new ScheduleProblem.MissingTravel(
                "Denver", zonedDenver(2026, 9, 14, 11, 30),
                "Lone Tree", zonedDenver(2026, 9, 15, 9, 0))));

        // Three answers is not more than three, so they are links rather than a menu.
        assertThat(html)
                .contains("<a href=\"/book-flight?fromCity=Denver&amp;toCity=Lone+Tree&amp;date=2026-09-15\" "
                          + "class=\"fix-summary\">Book flight &rarr;</a>")
                .contains("<a href=\"/book-train?fromCity=Denver&amp;toCity=Lone+Tree&amp;date=2026-09-15\" "
                          + "class=\"fix-summary\">Book train &rarr;</a>")
                .contains("<a href=\"/plan-ground-transfer?date=2026-09-15\" "
                          + "class=\"fix-summary\">Ground transfer &rarr;</a>")
                .doesNotContain("<details class=\"disclosure-menu\">");
        assertThat(html.indexOf("Book flight"))
                .as("flight is the common case in Ted's data, so it is offered first")
                .isLessThan(html.indexOf("Book train"));
        assertThat(html.indexOf("Book train")).isLessThan(html.indexOf("Ground transfer"));
    }

    /**
     * F6: the clash has no id on either side to link to, so the card keeps the slot and states the
     * reason rather than silently dropping the control — which would leave that card with no
     * vocabulary at all.
     */
    @Test
    void aSchedulingConflictCardShowsTheFixControlGreyedWithItsReasonAndNoLink() {
        String html = ScheduleProblemsRenderer.render(List.of(new ScheduleProblem.SchedulingConflict(
                new ScheduleProblem.ConflictingGathering("Aachen JUG", "Aachen",
                        zonedDenver(2026, 9, 8, 19, 0), zonedDenver(2026, 9, 8, 22, 0)),
                new ScheduleProblem.ConflictingGathering("Cologne JUG", "Cologne",
                        zonedDenver(2026, 9, 8, 20, 0), zonedDenver(2026, 9, 8, 23, 0)))));

        assertThat(html)
                .contains("<span class=\"fix-summary fix-summary--disabled\" "
                          + "title=\"Editing a gathering from here arrives with cause-linking\">Fix</span>")
                .as("a greyed control is a span, never a disabled anchor")
                .doesNotContain("<details class=\"disclosure-menu\">");
    }

    @Test
    void aDuplicateHotelCardOffersOneCancelLinkPerStay() {
        HotelBookingId first = HotelBookingId.random();
        HotelBookingId second = HotelBookingId.random();

        String html = ScheduleProblemsRenderer.render(List.of(new ScheduleProblem.DuplicateHotel(
                LocalDate.of(2026, 8, 26), LocalDate.of(2026, 8, 28),
                List.of(new ScheduleProblem.DuplicateStay(first, "Reichshof", "Hamburg", BookingIntent.FINAL),
                        new ScheduleProblem.DuplicateStay(second, "Park Hotel", "Soltau", BookingIntent.TENTATIVE)))));

        assertThat(html)
                .contains("<a href=\"/booked-hotels/" + first.id() + "/cancel\" "
                          + "class=\"fix-summary\">Cancel &quot;Reichshof&quot; &rarr;</a>")
                .contains("<a href=\"/booked-hotels/" + second.id() + "/cancel\" "
                          + "class=\"fix-summary\">Cancel &quot;Park Hotel&quot; &rarr;</a>")
                .as("two stays is not more than three, so no menu")
                .doesNotContain("<details class=\"disclosure-menu\">");
    }

    @Test
    void theMenuDismissalScriptShipsWithThePage() {
        // A travel gap, because that is a card that actually has a menu to dismiss.
        String html = ScheduleProblemsRenderer.render(List.of(new ScheduleProblem.MissingTravel(
                "Denver", zonedDenver(2026, 9, 14, 11, 30),
                "Lone Tree", zonedDenver(2026, 9, 15, 9, 0))));

        assertThat(html)
                .as("without it the menus open and never close")
                .contains("closeDisclosureMenus");
    }

    private static ZonedTimestamp zonedDenver(int year, int month, int day, int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(year, month, day, hour, minute),
                ZoneId.of("America/Denver"));
    }

    @Test
    void emptyTravelColumnShowsNone() {
        ScheduleProblem hotelOnly = new ScheduleProblem.MissingHotel(
                "Paris", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5), "");

        String html = ScheduleProblemsRenderer.render(List.of(hotelOnly));

        assertThat(html).contains("None");
    }

    @Test
    void aGapOutOfHomeNamesOneDateRatherThanTheSameOneTwice() {
        // Its window is a single moment, so "Arrive Nov 11 — next leg departs Nov 11" would be
        // true and useless. See ScheduleTimeline.gapLeaving.
        ZonedTimestamp needed = ZonedTimestamp.fromLocal(
                LocalDateTime.of(2026, 11, 11, 9, 0), ZoneId.of("Europe/Amsterdam"));
        ScheduleProblem gap = new ScheduleProblem.MissingTravel(
                "San Francisco", needed, "Ede", needed);

        String html = ScheduleProblemsRenderer.render(List.of(gap));

        assertThat(html)
                .contains("<div class=\"problem-title\">San Francisco → Ede</div>")
                .contains("Nothing booked — needed by")
                .doesNotContain("next leg departs");
    }

    @Test
    void duplicateHotelsNameEveryStayWithItsCityAndBookingIntent() {
        // The question this row raises is "which one do I cancel?", and the tentative one is
        // usually the answer — so the intent is on the page, not just in the event.
        ScheduleProblem duplicate = new ScheduleProblem.DuplicateHotel(
                LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 13),
                List.of(new ScheduleProblem.DuplicateStay(
                                HotelBookingId.random(), "Oak House", "Toronto", BookingIntent.FINAL),
                        new ScheduleProblem.DuplicateStay(
                                HotelBookingId.random(), "Doubletree by Hilton", "Toronto", BookingIntent.TENTATIVE)));

        String html = ScheduleProblemsRenderer.render(List.of(duplicate));

        assertThat(html)
                .contains("<p class=\"column-heading column-heading--duplicate\">Duplicate Hotels</p>")
                .contains("<div class=\"problem-title\">2 hotels booked for the same nights</div>")
                .contains("Nights of Sat, Aug 8 through Thu, Aug 13 — check out Fri, Aug 14")
                .contains("Oak House, Toronto (final)")
                .contains("Doubletree by Hilton, Toronto (tentative)");
    }

    @Test
    void aPageWithOnlyDuplicatesStillReportsThemRatherThanClaimingNoProblems() {
        // The empty check used to ask the four columns it knew about, so a page whose only problem
        // was a new kind would have said the schedule looked complete.
        ScheduleProblem duplicate = new ScheduleProblem.DuplicateHotel(
                LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 8),
                List.of(new ScheduleProblem.DuplicateStay(
                                HotelBookingId.random(), "Oak House", "Toronto", BookingIntent.FINAL),
                        new ScheduleProblem.DuplicateStay(
                                HotelBookingId.random(), "Doubletree by Hilton", "Toronto", BookingIntent.FINAL)));

        String html = ScheduleProblemsRenderer.render(List.of(duplicate));

        assertThat(html)
                .doesNotContain("No problems found")
                .contains("Duplicate Hotels");
    }

    private static ScheduleProblem.ConflictingGathering gathering(
            String name, String city, LocalDateTime start, LocalDateTime end, String zone) {
        ZoneId zoneId = ZoneId.of(zone);
        return new ScheduleProblem.ConflictingGathering(
                name, city,
                ZonedTimestamp.fromLocal(start, zoneId),
                ZonedTimestamp.fromLocal(end, zoneId));
    }
}
