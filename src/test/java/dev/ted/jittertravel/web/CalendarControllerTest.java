package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarAggregator;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.ConferenceCalendarProjector;
import dev.ted.jittertravel.application.EntryKind;
import dev.ted.jittertravel.application.FlightCalendarProjector;
import dev.ted.jittertravel.application.GatheringCalendarProjector;
import dev.ted.jittertravel.application.HotelCalendarProjector;
import dev.ted.jittertravel.application.SubtitleLine;
import dev.ted.jittertravel.application.TrainCalendarProjector;
import dev.ted.jittertravel.application.ViewerZonePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDate;
import java.util.List;

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
    void missingRangeParamsRenderAllEntries() {
        CalendarController controller = controllerWith(
                conference("EarliestEntry", LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 6)),
                conference("MiddleEntry", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8)),
                conference("LatestEntry", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)));

        ResponseEntity<String> response = controller.getCalendar(publicRequest(), null, null, null);

        assertThat(response.getBody())
                .contains("EarliestEntry")
                .contains("MiddleEntry")
                .contains("LatestEntry");
    }

    @Test
    void unparseableRangeParamsAreIgnoredAndAllEntriesRender() {
        CalendarController controller = controllerWith(
                conference("EarliestEntry", LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 6)),
                conference("LatestEntry", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)));

        ResponseEntity<String> response = controller.getCalendar(publicRequest(), "not-a-date", "07/31/2026", null);

        assertThat(response.getBody())
                .contains("EarliestEntry")
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

    private CalendarController controllerWith(CalendarEntry... entries) {
        given(conferenceProjector.entries()).willReturn(List.of(entries));
        given(flightProjector.entries()).willReturn(List.of());
        given(trainProjector.entries()).willReturn(List.of());
        given(hotelProjector.entries()).willReturn(List.of());
        given(gatheringProjector.entries()).willReturn(List.of());
        return new CalendarController(
                new CalendarAggregator(conferenceProjector, flightProjector, trainProjector,
                                       hotelProjector, gatheringProjector),
                new ViewerZonePolicy());
    }

    private static MockHttpServletRequest publicRequest() {
        return new MockHttpServletRequest();
    }

    private static CalendarEntry conference(String title, LocalDate start, LocalDate end) {
        return new CalendarEntry(
                EntryKind.CONFERENCE,
                start.atTime(9, 0),
                end.atTime(17, 0),
                title, List.of(new SubtitleLine.Text("subtitle for " + title)),
                title + " cont'd", List.of(new SubtitleLine.Text("continued subtitle for " + title)),
                null
        );
    }
}
