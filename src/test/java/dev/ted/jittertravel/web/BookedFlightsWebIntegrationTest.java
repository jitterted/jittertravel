package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightsProjector;
import dev.ted.jittertravel.application.BookedFlightView;
import dev.ted.jittertravel.application.CancelledView;
import dev.ted.jittertravel.application.FlightTrip;
import dev.ted.jittertravel.application.FlightTrips;
import dev.ted.jittertravel.application.ItinerarySummary;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.domain.Journey;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@Tag("spring")
@WebMvcTest(BookedFlightsController.class)
@Import(WebTodayTestConfig.class)
@WithMockUser(roles = "FAMILY")
class BookedFlightsWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    BookedFlightsProjector projector;

    @MockitoBean
    FlightTrips flightTrips;

    @Test
    void bookedFlightsPageRendersOk() {
        given(projector.views(any(), any())).willReturn(List.of());

        assertThat(mockMvc.get().uri("/booked-flights"))
                .hasStatusOk();
    }

    @Test
    void cancelledFlightsAreHiddenUnlessAskedFor() {
        assertThat(mockMvc.get().uri("/booked-flights"))
                .hasStatusOk();

        then(projector).should()
                .views(TimeView.FUTURE, CancelledView.HIDE, WebTodayTestConfig.FIXED_INSTANT);
    }

    @Test
    void cancelledShowAsksTheProjectorForCancelledFlightsAndKeepsTheTimeFilter() {
        given(projector.cancelledCount(TimeView.ALL, WebTodayTestConfig.FIXED_INSTANT)).willReturn(3);

        assertThat(mockMvc.get().uri("/booked-flights?filter=all&cancelled=show"))
                .hasStatusOk()
                .bodyText()
                .contains("aria-pressed=\"true\"")
                .contains("<b>3</b>");

        then(projector).should()
                .views(TimeView.ALL, CancelledView.SHOW, WebTodayTestConfig.FIXED_INSTANT);
    }

    /**
     * The controller composes the listed flights with their trips at the boundary's own "now", and
     * hands both to the renderer; the band and the leg's place on the page are the proof the trips
     * arrived.
     */
    @Test
    void theListedFlightsAreHandedToTheTripComposerAtNowAndTheirTripsAreRendered() {
        FlightId flightId = FlightId.random();
        BookedFlightView flight = new BookedFlightView(flightId, "United Airlines", "UA3510",
                AirportCode.of("YOW"), AirportCode.of("ORD"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 6, 6, 0), ZoneId.of("America/Toronto")),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 6, 7, 30), ZoneId.of("America/Chicago")),
                List.of());
        List<BookedFlightView> listed = List.of(flight);
        given(projector.views(any(), any(), any())).willReturn(listed);
        ItineraryLeg leg = new ItineraryLeg(flightId, flight.airline(), flight.flightNumber(),
                flight.departureAirport(), flight.departureDateTime(), flight.arrivalAirport(), flight.arrivalDateTime());
        given(flightTrips.forList(listed, WebTodayTestConfig.FIXED_INSTANT))
                .willReturn(Map.of(flightId, new FlightTrip(new ItinerarySummary(
                        FlightItineraryId.of(UUID.randomUUID()), "K3PQ9R", Journey.of(List.of(leg)),
                        4, 0, true, false, 1), 3)));

        assertThat(mockMvc.get().uri("/booked-flights"))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"flight-band-code\">K3PQ9R</span>")
                .contains("<span class=\"flight-trip-leg\">leg 3 of 4</span>");
    }
}
