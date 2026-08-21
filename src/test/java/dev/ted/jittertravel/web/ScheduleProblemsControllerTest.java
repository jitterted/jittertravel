package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.application.ViewerTodayZone;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Plain JUnit test of the controller method: no Spring context, no MockMvc. Covers the
 * controller's own behavior — resolving {@code ?view=} to a renderer, and "today" from the clock
 * plus the viewer's zone cookie.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleProblemsControllerTest {

    /** Early UTC on 16 July; still 15 July in Los Angeles, which the zone cases turn on. */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-16T04:00:00Z");

    @Mock
    ScheduleGapProjector scheduleGapProjector;

    @Test
    void noViewParamRendersTheCalendarView() {
        ResponseEntity<String> response = controller().scheduleProblems(request(), null);

        assertThat(response.getBody())
                .contains("<a href=\"/schedule-problems?view=calendar\" class=\"active\">Calendar</a>")
                .contains("class=\"pc-container\"")
                .doesNotContain("class=\"problem-card problem-card--missing-hotel\"");
    }

    @Test
    void unrecognizedViewParamFallsBackToTheCalendarView() {
        ResponseEntity<String> response = controller().scheduleProblems(request(), "gantt");

        assertThat(response.getBody())
                .contains("<a href=\"/schedule-problems?view=calendar\" class=\"active\">Calendar</a>")
                .doesNotContain("class=\"problem-card problem-card--missing-hotel\"");
    }

    @Test
    void listViewParamRendersTheListView() {
        ResponseEntity<String> response = controller().scheduleProblems(request(), "list");

        assertThat(response.getBody())
                .contains("<a href=\"/schedule-problems?view=list\" class=\"active\">List</a>")
                .contains("class=\"problem-card problem-card--missing-hotel\"")
                .doesNotContain("class=\"pc-container\"");
    }

    @Test
    void calendarViewParamRendersTheCalendarView() {
        ResponseEntity<String> response = controller().scheduleProblems(request(), "calendar");

        assertThat(response.getBody())
                .contains("<a href=\"/schedule-problems?view=calendar\" class=\"active\">Calendar</a>")
                .contains("class=\"pc-container\"")
                .contains("<div class=\"pc-band-title\">No hotel — London</div>")
                .doesNotContain("class=\"problem-card problem-card--missing-hotel\"");
    }

    @Test
    void viewParamIsCaseInsensitive() {
        ResponseEntity<String> response = controller().scheduleProblems(request(), "Calendar");

        assertThat(response.getBody()).contains("class=\"pc-container\"");
    }

    @Test
    void todayComesFromTheViewerZoneCookieNotTheClocksOwnZone() {
        // 2026-07-16T04:00Z is 15 July in Los Angeles: today's column is the 15th, and the 16th
        // is still ahead. Reading the instant in UTC instead would mark the 15th as past.
        MockHttpServletRequest request = request();
        request.setCookies(new Cookie(ViewerTodayZone.COOKIE_NAME, "America/Los_Angeles"));

        ResponseEntity<String> response = controller().scheduleProblems(request, "calendar");

        assertThat(response.getBody())
                .contains("<div class=\"pc-day-cell is-today\"><span class=\"pc-day-number\">15</span></div>")
                .contains("<div class=\"pc-day-cell\"><span class=\"pc-day-number\">16</span></div>");
    }

    @Test
    void anAbsentCookieFallsBackToTheConfiguredHomeZone() {
        // No cookie: the fallback zone (Europe/London) reads the same instant as 16 July.
        ResponseEntity<String> response = controller().scheduleProblems(request(), "calendar");

        assertThat(response.getBody())
                .contains("<div class=\"pc-day-cell is-today\"><span class=\"pc-day-number\">16</span></div>");
    }

    @Test
    void bothViewsAreServedAsUtf8Html() {
        ResponseEntity<String> response = controller().scheduleProblems(request(), "calendar");

        assertThat(response.getHeaders().getContentType())
                .hasToString("text/html;charset=UTF-8");
    }

    private ScheduleProblemsController controller() {
        given(scheduleGapProjector.problems(FIXED_INSTANT)).willReturn(List.of(
                new ScheduleProblem.MissingHotel("London", LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 17), "")));
        return new ScheduleProblemsController(
                scheduleGapProjector,
                Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")),
                new ViewerTodayZone(ZoneId.of("Europe/London")));
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/schedule-problems");
    }
}
