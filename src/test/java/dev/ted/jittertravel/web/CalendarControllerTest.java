package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarAggregator;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.ConferenceCalendarProjector;
import dev.ted.jittertravel.application.EntryDetails;
import dev.ted.jittertravel.application.FlightCalendarProjector;
import dev.ted.jittertravel.application.GatheringCalendarProjector;
import dev.ted.jittertravel.application.GroundTransferCalendarProjector;
import dev.ted.jittertravel.application.HotelCalendarProjector;
import dev.ted.jittertravel.application.PrivateEventCalendarProjector;
import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.SubtitleLine;
import dev.ted.jittertravel.application.TrainCalendarProjector;
import dev.ted.jittertravel.application.ViewerZonePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.ted.jittertravel.application.ViewerTodayZone;
import jakarta.servlet.http.Cookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Plain JUnit test of the controller method: no Spring context, no MockMvc. Covers the
 * controller's own behavior — parsing {@code ?from=}/{@code ?to=} and handing them to the
 * renderer — by asserting on which entries survive into the rendered HTML.
 */
@ExtendWith(MockitoExtension.class)
class CalendarControllerTest {

    @Mock ConferenceCalendarProjector conferenceProjector;
    @Mock FlightCalendarProjector flightProjector;
    @Mock TrainCalendarProjector trainProjector;
    @Mock HotelCalendarProjector hotelProjector;
    @Mock GatheringCalendarProjector gatheringProjector;
    @Mock PrivateEventCalendarProjector privateEventProjector;
    @Mock GroundTransferCalendarProjector groundTransferProjector;
    @Mock ScheduleGapProjector scheduleGapProjector;

    @Test
    void awayDaysFromTheScheduleReachTheRenderedDayLabelCells() {
        // The controller is the only thing that knows where the away band comes from: every
        // renderer test below it can be green while the calendar renders no band at all.
        CalendarController controller = controllerWith(
                Set.of(LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 8)),
                conference("InsideRange", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8)));

        ResponseEntity<String> response = controller.getCalendar(publicRequest(), "2026-07-01", "2026-07-31", null);

        assertThat(response.getBody())
                .contains("<div class=\"day-label-cell month-tint-odd is-away\"><span class=\"day-number\">7</span>")
                .contains("<div class=\"day-label-cell month-tint-odd is-away\"><span class=\"day-number\">8</span>")
                .contains("<div class=\"day-label-cell month-tint-odd\"><span class=\"day-number\">9</span>");
    }

    @Test
    void isoDateRangeParamsLimitRenderedEntriesToThatRange() {
        CalendarController controller = controllerWith(
                conference("WayBeforeRange", LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 8)),
                conference("JustBeforeRange", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17)),
                conference("InsideRangeEarly", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8)),
                conference("InsideRangeLate", LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 29)),
                conference("JustAfterRange", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)),
                conference("WayAfterRange", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7)));

        ResponseEntity<String> response = controller.getCalendar(publicRequest(), "2026-07-01", "2026-07-31", null);

        assertThat(response.getBody())
                .contains("InsideRangeEarly")
                .contains("InsideRangeLate")
                .doesNotContain("WayBeforeRange")
                .doesNotContain("JustBeforeRange")
                .doesNotContain("JustAfterRange")
                .doesNotContain("WayAfterRange");
    }

    @Test
    void basicIsoDateRangeParamsLimitRenderedEntriesToThatRange() {
        CalendarController controller = controllerWith(
                conference("BeforeRange", LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 6)),
                conference("InsideRange", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8)),
                conference("AfterRange", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)));

        ResponseEntity<String> response = controller.getCalendar(publicRequest(), "20260701", "20260731", null);

        assertThat(response.getBody())
                .contains("InsideRange")
                .doesNotContain("BeforeRange")
                .doesNotContain("AfterRange");
    }

    @Test
    void reversedRangeParamsAreNormalizedAndStillLimitEntriesToThatRange() {
        CalendarController controller = controllerWith(
                conference("BeforeRange", LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 6)),
                conference("InsideRange", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8)),
                conference("AfterRange", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)));

        ResponseEntity<String> response = controller.getCalendar(publicRequest(), "2026-07-31", "2026-07-01", null);

        assertThat(response.getBody())
                .contains("InsideRange")
                .doesNotContain("BeforeRange")
                .doesNotContain("AfterRange");
    }

    @Test
    void missingRangeParamsDefaultToWeekBeforeTodayThroughLastEntry() {
        // today = 2026-06-11, so the default window starts 2026-06-04: the pre-today entry drops
        // out of the default view (reachable via ?from=), later entries render through the last.
        CalendarController controller = controllerWith(
                conference("BeforeWindow", LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 6)),
                conference("MiddleEntry", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8)),
                conference("LatestEntry", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)));

        ResponseEntity<String> response = controller.getCalendar(publicRequest(), null, null, null);

        assertThat(response.getBody())
                .doesNotContain("BeforeWindow")
                .contains("MiddleEntry")
                .contains("LatestEntry");
    }

    @Test
    void unparseableRangeParamsAreIgnoredAndDefaultWindowApplies() {
        // Unparseable params behave as if absent: the default window (from one week before today)
        // applies, so the pre-window entry is dropped while a later one still renders.
        CalendarController controller = controllerWith(
                conference("BeforeWindow", LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 6)),
                conference("LatestEntry", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)));

        ResponseEntity<String> response = controller.getCalendar(publicRequest(), "not-a-date", "07/31/2026", null);

        assertThat(response.getBody())
                .doesNotContain("BeforeWindow")
                .contains("LatestEntry");
    }

    @Test
    void onlyFromParamGivenLimitsRenderedEntriesToOnOrAfterThatDate() {
        CalendarController controller = controllerWith(
                conference("BeforeFrom", LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 6)),
                conference("AfterFrom", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8)));

        ResponseEntity<String> response = controller.getCalendar(publicRequest(), "2026-07-01", null, null);

        assertThat(response.getBody())
                .contains("AfterFrom")
                .doesNotContain("BeforeFrom");
    }

    @Test
    void onlyToParamGivenLimitsRenderedEntriesToOnOrBeforeThatDate() {
        CalendarController controller = controllerWith(
                conference("BeforeTo", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8)),
                conference("AfterTo", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)));

        ResponseEntity<String> response = controller.getCalendar(publicRequest(), null, "2026-07-31", null);

        assertThat(response.getBody())
                .contains("BeforeTo")
                .doesNotContain("AfterTo");
    }

    @Test
    void viewerZoneCookieDeterminesWhichDayIsMarkedToday() {
        // 2026-08-17T01:00Z is still Sunday 2026-08-16 (18:00) in America/Los_Angeles — exactly
        // the reported bug: the server's UTC clock has already ticked to Monday. With no cookie,
        // the fallback zone keeps today on Sunday; a UTC viewerZone cookie moves it to Monday.
        Clock nearMidnightUtc = Clock.fixed(Instant.parse("2026-08-17T01:00:00Z"), ZoneId.of("UTC"));
        CalendarController controller = controllerWith(nearMidnightUtc);

        String pacific = controller.getCalendar(familyRequest(null), null, null, null).getBody();
        String utc = controller.getCalendar(familyRequest("UTC"), null, null, null).getBody();

        assertThat(pacific)
                .as("No cookie: the America/Los_Angeles fallback keeps today on Sunday 2026-08-16")
                .contains("is-today\"><a href=\"/itinerary?date=2026-08-16\"");
        assertThat(utc)
                .as("A UTC viewerZone cookie shifts today to Monday 2026-08-17")
                .contains("is-today\"><a href=\"/itinerary?date=2026-08-17\"");
    }

    private CalendarController controllerWith(CalendarEntry... entries) {
        return controllerWith(FIXED_CLOCK, entries);
    }

    private CalendarController controllerWith(Set<LocalDate> awayDays, CalendarEntry... entries) {
        given(scheduleGapProjector.awayDays()).willReturn(awayDays);
        return controllerWith(FIXED_CLOCK, entries);
    }

    private CalendarController controllerWith(Clock clock, CalendarEntry... entries) {
        given(conferenceProjector.entries()).willReturn(List.of(entries));
        given(flightProjector.entries()).willReturn(List.of());
        given(trainProjector.entries()).willReturn(List.of());
        given(hotelProjector.entries()).willReturn(List.of());
        given(gatheringProjector.entries()).willReturn(List.of());
        given(privateEventProjector.entries()).willReturn(List.of());
        given(groundTransferProjector.entries()).willReturn(List.of());
        return new CalendarController(
                new CalendarAggregator(conferenceProjector, flightProjector, trainProjector,
                                       hotelProjector, gatheringProjector, privateEventProjector,
                                       groundTransferProjector),
                scheduleGapProjector,
                new ViewerZonePolicy(), clock, new ViewerTodayZone(FALLBACK_ZONE));
    }

    private static MockHttpServletRequest familyRequest(String viewerZoneCookie) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteUser("family");
        request.addUserRole("FAMILY");
        if (viewerZoneCookie != null) {
            request.setCookies(new Cookie(ViewerTodayZone.COOKIE_NAME, viewerZoneCookie));
        }
        return request;
    }

    // No viewerZone cookie on publicRequest(), so "today" resolves in the fallback zone: midday
    // UTC on 2026-06-11 is still 2026-06-11 in America/Los_Angeles, making today = 2026-06-11 and
    // the default calendar start (one week before today) = 2026-06-04.
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("America/Los_Angeles");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-11T12:00:00Z"), ZoneId.of("UTC"));

    private static MockHttpServletRequest publicRequest() {
        return new MockHttpServletRequest();
    }

    private static CalendarEntry conference(String title, LocalDate start, LocalDate end) {
        return new CalendarEntry(
                start.atTime(9, 0),
                end.atTime(17, 0),
                title, List.of(new SubtitleLine.Text("subtitle for " + title)),
                title + " cont'd", List.of(new SubtitleLine.Text("continued subtitle for " + title)),
                new EntryDetails.Conference(null)
        );
    }
}
