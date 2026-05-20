package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class ChangeFlightWebIntegrationTest extends AbstractTestcontainerIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private EventStore eventStore;

    @Test
    void bookThenChangeFlightEmitsFlightChangedAndUpdatesList() {
        String flightId = UUID.randomUUID().toString();
        String originalFlightNumber = "UAX" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ENGLISH);
        String newFlightNumber = "LHX" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ENGLISH);
        LocalDateTime departure = LocalDateTime.now().plusDays(7);
        LocalDateTime arrival = departure.plusHours(5);
        LocalDateTime newDeparture = departure.plusDays(1);
        LocalDateTime newArrival = arrival.plusDays(1);

        // 1. Book a flight
        assertThat(mockMvc.post().uri("/book-flight")
                .param("flightId", flightId)
                .param("airline", "United Original")
                .param("flightNumber", originalFlightNumber)
                .param("departureAirport", "SFO")
                .param("departureDateTime", departure.toString())
                .param("arrivalAirport", "JFK")
                .param("arrivalDateTime", arrival.toString()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");

        // 2. GET the edit form pre-filled
        assertThat(mockMvc.get().uri("/booked-flights/" + flightId))
                .hasStatusOk()
                .bodyText()
                .contains("Change Flight", originalFlightNumber, "United Original", "SFO", "JFK");

        // 3. Submit a change
        assertThat(mockMvc.post().uri("/booked-flights/" + flightId)
                .param("flightId", flightId)
                .param("airline", "Lufthansa Updated")
                .param("flightNumber", newFlightNumber)
                .param("departureAirport", "SFO")
                .param("departureDateTime", newDeparture.toString())
                .param("arrivalAirport", "MUC")
                .param("arrivalDateTime", newArrival.toString()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");

        // 4. FlightChanged event recorded with the new values.
        // Filter by our unique new flight number to be robust against the shared EventStore.
        FlightChanged changed = eventStore.findAll()
                .map(se -> se.payload())
                .filter(p -> p instanceof FlightChanged)
                .map(p -> (FlightChanged) p)
                .filter(fc -> newFlightNumber.equals(fc.flightNumber()))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(changed.flightId().id()).isEqualTo(UUID.fromString(flightId));
        assertThat(changed.airline()).isEqualTo("Lufthansa Updated");
        assertThat(changed.arrivalAirport().code()).isEqualTo("MUC");

        // 5. List view reflects the change.
        assertThat(mockMvc.get().uri("/booked-flights"))
                .hasStatusOk()
                .bodyText()
                .contains("Lufthansa Updated", newFlightNumber, "SFO\u2192MUC");
    }

    @Test
    void getOnUnknownFlightIdRedirectsToBookedFlights() {
        String unknown = UUID.randomUUID().toString();
        assertThat(mockMvc.get().uri("/booked-flights/" + unknown))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");
    }

    @Test
    void postOnUnknownFlightIdRedirectsToBookedFlightsWithoutEmittingAnEvent() {
        String unknown = UUID.randomUUID().toString();
        LocalDateTime departure = LocalDateTime.now().plusDays(2);
        LocalDateTime arrival = departure.plusHours(3);

        long beforeChangedCount = countFlightChangedFor(unknown);

        assertThat(mockMvc.post().uri("/booked-flights/" + unknown)
                .param("flightId", unknown)
                .param("airline", "X")
                .param("flightNumber", "X1")
                .param("departureAirport", "SFO")
                .param("departureDateTime", departure.toString())
                .param("arrivalAirport", "LAX")
                .param("arrivalDateTime", arrival.toString()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");

        long afterChangedCount = countFlightChangedFor(unknown);
        assertThat(afterChangedCount).isEqualTo(beforeChangedCount);
    }

    private long countFlightChangedFor(String flightId) {
        UUID id = UUID.fromString(flightId);
        return eventStore.findAll()
                .map(StoredEvent::payload)
                .filter(p -> p instanceof FlightChanged)
                .map(p -> (FlightChanged) p)
                .filter(fc -> fc.flightId().id().equals(id))
                .count();
    }
}
