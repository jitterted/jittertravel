package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryProjector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(ItineraryController.class)
@WithMockUser
class ItineraryControllerTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 6, 1);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_DATE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    ItineraryProjector projector;

    @MockitoBean
    Clock clock;

    @Test
    void itineraryUrlMapsToOkWithHtmlContentType() {
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
        given(clock.getZone()).willReturn(FIXED_CLOCK.getZone());
        given(projector.firstDateOnOrAfter(FIXED_DATE)).willReturn(FIXED_DATE);
        given(projector.entriesForDate(FIXED_DATE)).willReturn(List.of());
        given(projector.entriesForDate(FIXED_DATE.plusDays(1))).willReturn(List.of());
        given(projector.entriesForDate(FIXED_DATE.plusDays(2))).willReturn(List.of());

        assertThat(mockMvc.get().uri("/itinerary"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void itineraryWithDateParamUsesProvidedDate() {
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
        given(clock.getZone()).willReturn(FIXED_CLOCK.getZone());
        given(projector.entriesForDate(FIXED_DATE)).willReturn(List.of());
        given(projector.entriesForDate(FIXED_DATE.plusDays(1))).willReturn(List.of());
        given(projector.entriesForDate(FIXED_DATE.plusDays(2))).willReturn(List.of());

        assertThat(mockMvc.get().uri("/itinerary?date=2026-06-01"))
                .hasStatusOk();
    }
}
