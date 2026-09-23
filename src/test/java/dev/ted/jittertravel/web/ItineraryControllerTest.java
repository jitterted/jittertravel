package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryProjector;
import dev.ted.jittertravel.application.OngoingStay;
import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.ScheduleProblem;
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
import java.util.Optional;

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

    @MockitoBean
    ScheduleGapProjector gapProjector;

    @Test
    void itineraryUrlMapsToOkWithHtmlContentType() {
        assertThat(mockMvc.get().uri("/itinerary"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void withNoDateTheItineraryStartsOnTodayEvenWhenTodayHasNoEntries() {
        given(projector.firstEntryDateOnOrAfter(TODAY)).willReturn(Optional.of(TODAY.plusDays(1)));

        assertThat(mockMvc.get().uri("/itinerary"))
                .hasStatusOk()
                .bodyText()
                .contains("<a href=\"/itinerary?date=2026-06-24\">&larr; Previous</a>");
    }

    @Test
    void nextEntryLinkSearchesFromTheDayAfterTheThreeOnScreen() {
        given(projector.firstEntryDateOnOrAfter(TODAY.plusDays(3))).willReturn(Optional.of(LocalDate.of(2026, 7, 14)));

        assertThat(mockMvc.get().uri("/itinerary"))
                .hasStatusOk()
                .bodyText()
                .contains("<a class=\"next-entry-link\" href=\"/itinerary?date=2026-07-14\">Next entry &rArr;</a>");
    }

    @Test
    void eventlessDayRendersTheOngoingStayTheProjectorReportsForThatDay() {
        LocalDate requested = LocalDate.of(2026, 6, 1);
        given(projector.entriesForDate(requested)).willReturn(List.of());
        given(projector.ongoingStayOn(requested))
                .willReturn(Optional.of(new OngoingStay("Grand Hotel Frankfurt", "Frankfurt", "DE")));

        assertThat(mockMvc.get().uri("/itinerary?date=2026-06-01"))
                .hasStatusOk()
                .bodyText()
                .contains("<span>In Frankfurt, DE</span>")
                .contains("<div class=\"whereabouts-detail\">Grand Hotel Frankfurt</div>");
    }

    @Test
    void aDayTheGapProjectorCallsHomeRendersTheHomeRow() {
        LocalDate requested = LocalDate.of(2026, 6, 1);
        given(projector.entriesForDate(requested)).willReturn(List.of());
        given(gapProjector.atHomeOn(requested)).willReturn(true);

        assertThat(mockMvc.get().uri("/itinerary?date=2026-06-01"))
                .hasStatusOk()
                .bodyText()
                .contains("<span>You&rsquo;re Home</span>");
    }

    @Test
    void aDayTheGapProjectorCallsBedlessRendersWhereHeIsAndTheBookingLink() {
        LocalDate requested = LocalDate.of(2026, 6, 1);
        given(projector.entriesForDate(requested)).willReturn(List.of());
        given(gapProjector.missingHotelOn(requested)).willReturn(Optional.of(
                new ScheduleProblem.MissingHotel("Denver", requested, requested.plusDays(4), "ExploreDDD")));

        assertThat(mockMvc.get().uri("/itinerary?date=2026-06-01"))
                .hasStatusOk()
                .bodyText()
                .contains("<span>In Denver</span>")
                .contains("<div class=\"whereabouts-detail\">No hotel booked</div>");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void theOwnerGetsTheBookHotelLinkOnABedlessDay() {
        LocalDate requested = LocalDate.of(2026, 6, 1);
        given(projector.entriesForDate(requested)).willReturn(List.of());
        given(gapProjector.missingHotelOn(requested)).willReturn(Optional.of(
                new ScheduleProblem.MissingHotel("Denver", requested, requested.plusDays(4), "ExploreDDD")));

        assertThat(mockMvc.get().uri("/itinerary?date=2026-06-01"))
                .hasStatusOk()
                .bodyText()
                .contains("<a href=\"/book-hotel?city=Denver&amp;checkIn=2026-06-01&amp;checkOut=2026-06-05"
                          + "&amp;problem=hotel%7CDenver%7C2026-06-01%7C2026-06-05&amp;from=itinerary\">");
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
