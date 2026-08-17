package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryProjector;
import dev.ted.jittertravel.application.ViewerZonePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(ItineraryController.class)
@Import({ViewerZonePolicy.class, WebTodayTestConfig.class})
@WithMockUser(roles = "FAMILY")
class ItineraryControllerTest {

    // WebTodayTestConfig pins the clock to 2026-06-25T12:00Z; with no viewerZone cookie the
    // fallback zone (America/Los_Angeles) makes "today" = 2026-06-25.
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    ItineraryProjector projector;

    @Test
    void itineraryUrlMapsToOkWithHtmlContentType() {
        given(projector.firstDateOnOrAfter(TODAY)).willReturn(TODAY);
        given(projector.entriesForDate(TODAY)).willReturn(List.of());
        given(projector.entriesForDate(TODAY.plusDays(1))).willReturn(List.of());
        given(projector.entriesForDate(TODAY.plusDays(2))).willReturn(List.of());

        assertThat(mockMvc.get().uri("/itinerary"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void itineraryWithDateParamUsesProvidedDate() {
        LocalDate requested = LocalDate.of(2026, 6, 1);
        given(projector.entriesForDate(requested)).willReturn(List.of());
        given(projector.entriesForDate(requested.plusDays(1))).willReturn(List.of());
        given(projector.entriesForDate(requested.plusDays(2))).willReturn(List.of());

        assertThat(mockMvc.get().uri("/itinerary?date=2026-06-01"))
                .hasStatusOk();
    }
}
