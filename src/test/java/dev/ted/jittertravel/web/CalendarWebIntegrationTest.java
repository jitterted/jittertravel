package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarAggregator;
import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.ViewerZonePolicy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import dev.ted.jittertravel.application.PublicCalendarProjector;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@Tag("spring")
@WebMvcTest(CalendarController.class)
@Import({ViewerZonePolicy.class, WebTodayTestConfig.class})
@WithMockUser(roles = "FAMILY")
class CalendarWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    CalendarAggregator calendarAggregator;

    @MockitoBean
    PublicCalendarProjector publicCalendarProjector;

    @MockitoBean
    ScheduleGapProjector scheduleGapProjector;

    @Test
    void calendarPageRendersOk() {
        given(calendarAggregator.allEntries()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk();
    }

    /**
     * {@code day=} widens the default range to take in the week before a past day, so the
     * {@code #w-} jump the conferences page sends has a row to land on — and marks that day's
     * column to flash. Today is pinned at 2026-06-25, so the default range starts in the week of
     * 2026-06-14.
     */
    @Test
    void aPastDayIsDrawnWithItsWeekOfLeadInAndMarked() {
        given(calendarAggregator.allEntries()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/calendar?day=2026-06-07"))
                .hasStatusOk()
                .bodyText()
                .contains("<div id=\"w-2026-05-31\" class=\"calendar-week")
                .contains("month-tint-even is-past is-arrival\"");
    }

    /** It only widens: a day whose lead-in is already in the default range leaves it where it was. */
    @Test
    void aDayInsideTheDefaultRangeDoesNotMoveItsStart() {
        given(calendarAggregator.allEntries()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/calendar?day=2026-07-01"))
                .hasStatusOk()
                .bodyText()
                .contains("<div id=\"w-2026-06-14\" class=\"calendar-week")
                .doesNotContain("<div id=\"w-2026-06-07\" class=\"calendar-week");
    }

    @Test
    void calendarPageWithDashedDateRangeParamsRendersOk() {
        given(calendarAggregator.allEntries()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/calendar?from=2026-07-01&to=2026-08-31"))
                .hasStatusOk();
    }

    @Test
    void calendarPageWithBasicIsoDateRangeParamsRendersOk() {
        given(calendarAggregator.allEntries()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/calendar?from=20260701&to=20260831"))
                .hasStatusOk();
    }

    @Test
    void calendarPageWithInvalidDateParamsFallsBackToDefaultRendering() {
        given(calendarAggregator.allEntries()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/calendar?from=notadate&to=20260825x"))
                .hasStatusOk();
    }
}
