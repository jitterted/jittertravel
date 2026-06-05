package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferenceCalendarProjector;
import dev.ted.jittertravel.application.FlightCalendarProjector;
import dev.ted.jittertravel.application.HotelCalendarProjector;
import dev.ted.jittertravel.application.TrainCalendarProjector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(CalendarController.class)
class CalendarWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    ConferenceCalendarProjector conferenceCalendarProjector;

    @MockitoBean
    FlightCalendarProjector flightCalendarProjector;

    @MockitoBean
    TrainCalendarProjector trainCalendarProjector;

    @MockitoBean
    HotelCalendarProjector hotelCalendarProjector;

    @Test
    void calendarPageRendersOk() {
        given(conferenceCalendarProjector.entries()).willReturn(List.of());
        given(flightCalendarProjector.entries()).willReturn(List.of());
        given(trainCalendarProjector.entries()).willReturn(List.of());
        given(hotelCalendarProjector.entries()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk();
    }
}
