package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class BookFlightWebIntegrationTest extends AbstractTestcontainerIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private EventStore eventStore;

    @Test
    void bookFlightFlowSavesEventAndRedirectsToBookedFlights() {
        LocalDateTime departure = LocalDateTime.now().plusDays(2);
        LocalDateTime arrival = departure.plusHours(5);

        assertThat(mockMvc.post().uri("/book-flight")
                .param("flightId", UUID.randomUUID().toString())
                .param("airline", "United")
                .param("flightNumber", "UA100")
                .param("departureAirport", "sfo")
                .param("departureDateTime", departure.toString())
                .param("arrivalAirport", "JFK")
                .param("arrivalDateTime", arrival.toString()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");

        // Filter by this test's specific flight number so the assertion is
        // robust against other integration tests sharing the in-memory EventStore.
        FlightBooked booked = eventStore.findAll()
                .map(StoredEvent::payload)
                .filter(p -> p instanceof FlightBooked)
                .map(p -> (FlightBooked) p)
                .filter(fb -> "UA100".equals(fb.flightNumber()))
                .reduce((first, second) -> second) // last-write-wins if duplicates
                .orElseThrow();
        assertThat(booked.airline()).isEqualTo("United");
        assertThat(booked.flightNumber()).isEqualTo("UA100");
        assertThat(booked.departureAirport().code()).isEqualTo("SFO");
        assertThat(booked.arrivalAirport().code()).isEqualTo("JFK");
    }

    @Test
    void getBookFlightFormReturnsPageWithDefaults() {
        assertThat(mockMvc.get().uri("/book-flight"))
                .hasStatusOk()
                .bodyText()
                .contains("Book Flight", "Airline", "Flight Number", "Departure Airport", "Arrival Airport");
    }
}
