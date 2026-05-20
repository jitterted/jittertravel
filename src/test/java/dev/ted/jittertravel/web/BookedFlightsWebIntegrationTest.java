package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class BookedFlightsWebIntegrationTest extends AbstractTestcontainerIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Test
    void bookedFlightsPageShowsAFlightBookedThroughTheBookingForm() {
        // Book a flight with a unique flight number so we can find it
        // among any events left by other tests sharing the in-memory EventStore.
        String flightNumber = "UAX" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ENGLISH);
        LocalDateTime departure = LocalDateTime.of(
                LocalDateTime.now().toLocalDate().plusYears(1), java.time.LocalTime.of(13, 55));
        LocalDateTime arrival = departure.plusHours(5);

        assertThat(mockMvc.post().uri("/book-flight")
                .param("flightId", UUID.randomUUID().toString())
                .param("airline", "United Test Air")
                .param("flightNumber", flightNumber)
                .param("departureAirport", "SFO")
                .param("departureDateTime", departure.toString())
                .param("arrivalAirport", "FRA")
                .param("arrivalDateTime", arrival.toString()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");

        String expectedDeparture = departure.format(
                DateTimeFormatter.ofPattern("M-dd-uuu h:mma", Locale.ENGLISH));

        assertThat(mockMvc.get().uri("/booked-flights"))
                .hasStatusOk()
                .bodyText()
                .contains("Booked Flights",
                          "Route",
                          "United Test Air",
                          flightNumber,
                          "SFO\u2192FRA",
                          expectedDeparture);
    }

    @Test
    void bookedFlightsPageRendersOk() {
        assertThat(mockMvc.get().uri("/booked-flights"))
                .hasStatusOk()
                .bodyText()
                .contains("Booked Flights");
    }
}
