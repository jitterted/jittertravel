package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarAggregator;
import dev.ted.jittertravel.application.ViewerZonePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(CalendarController.class)
@Import({ViewerZonePolicy.class, WebTodayTestConfig.class})
@WithMockUser(roles = "FAMILY")
class CalendarWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    CalendarAggregator calendarAggregator;

    @Test
    void calendarPageRendersOk() {
        given(calendarAggregator.allEntries()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk();
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
