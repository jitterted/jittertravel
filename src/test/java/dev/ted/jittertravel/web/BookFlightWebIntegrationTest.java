package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.FlightBooking;
import dev.ted.jittertravel.infrastructure.AeroDataBoxClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(BookFlightController.class)
class BookFlightWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    FlightBooking flightBooking;

    @MockitoBean
    AeroDataBoxClient aeroDataBoxClient;

    @Test
    void getBookFlightFormReturnsOk() {
        given(flightBooking.isReadOnly()).willReturn(false);

        assertThat(mockMvc.get().uri("/book-flight"))
                .hasStatusOk();
    }

    @Test
    void bookFlightPostRedirectsToBookedFlights() {
        given(flightBooking.isReadOnly()).willReturn(false);

        assertThat(mockMvc.post().uri("/book-flight")
                .param("flightId", "550e8400-e29b-41d4-a716-446655440000")
                .param("airline", "United")
                .param("flightNumber", "UA100")
                .param("departureAirport", "SFO")
                .param("departureDateTime", "2026-07-01T09:00")
                .param("arrivalAirport", "JFK")
                .param("arrivalDateTime", "2026-07-01T14:00"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");
    }
}
