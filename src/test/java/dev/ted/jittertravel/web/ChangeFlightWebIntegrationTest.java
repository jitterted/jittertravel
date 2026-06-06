package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeFlight;
import dev.ted.jittertravel.application.FlightDetailsView;
import dev.ted.jittertravel.application.FlightDetailsViewProjector;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightNotFound;
import dev.ted.jittertravel.infrastructure.AeroDataBoxClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(ChangeFlightController.class)
@WithMockUser
class ChangeFlightWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    ChangeFlight changeFlight;

    @MockitoBean
    FlightDetailsViewProjector detailsProjector;

    @MockitoBean
    AeroDataBoxClient aeroDataBoxClient;

    @BeforeEach
    void setUp() {
        given(changeFlight.isReadOnly()).willReturn(false);
    }

    @Test
    void getWithKnownFlightIdRendersChangeForm() {
        String flightId = UUID.randomUUID().toString();
        FlightDetailsView view = new FlightDetailsView(
                FlightId.of(UUID.fromString(flightId)),
                "United", "UA100",
                AirportCode.of("SFO"), LocalDateTime.of(2026, 7, 1, 9, 0),
                AirportCode.of("JFK"), LocalDateTime.of(2026, 7, 1, 14, 0));
        given(detailsProjector.findById(any())).willReturn(Optional.of(view));

        assertThat(mockMvc.get().uri("/booked-flights/" + flightId))
                .hasStatusOk();
    }

    @Test
    void getOnUnknownFlightIdRedirectsToBookedFlights() {
        given(detailsProjector.findById(any())).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/booked-flights/" + UUID.randomUUID()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");
    }

    @Test
    void postWithKnownFlightIdRedirectsToBookedFlights() {
        String flightId = UUID.randomUUID().toString();

        assertThat(mockMvc.post().uri("/booked-flights/" + flightId)
                .with(csrf())
                .param("airline", "Lufthansa")
                .param("flightNumber", "LH400")
                .param("departureAirport", "SFO")
                .param("departureDateTime", "2026-07-01T09:00")
                .param("arrivalAirport", "MUC")
                .param("arrivalDateTime", "2026-07-02T06:00"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");
    }

    @Test
    void postOnUnknownFlightIdRedirectsToBookedFlights() {
        willThrow(new FlightNotFound("Flight not found")).given(changeFlight).changeFlight(any());

        assertThat(mockMvc.post().uri("/booked-flights/" + UUID.randomUUID())
                .with(csrf())
                .param("airline", "X")
                .param("flightNumber", "X1")
                .param("departureAirport", "SFO")
                .param("departureDateTime", "2026-07-01T09:00")
                .param("arrivalAirport", "LAX")
                .param("arrivalDateTime", "2026-07-01T12:00"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");
    }
}
