package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeFlight;
import dev.ted.jittertravel.application.FlightDetailsView;
import dev.ted.jittertravel.application.FlightDetailsViewProjector;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightNotFound;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.AeroDataBoxClient;
import dev.ted.jittertravel.infrastructure.FlightLookupCandidates;
import dev.ted.jittertravel.infrastructure.FlightLookupResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(ChangeFlightController.class)
@WithMockUser(roles = "OWNER")
class ChangeFlightWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    ChangeFlight changeFlight;

    @MockitoBean
    FlightDetailsViewProjector detailsProjector;

    @MockitoBean
    AeroDataBoxClient aeroDataBoxClient;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(changeFlight.isReadOnly()).willReturn(false);
        given(clock.instant()).willReturn(Instant.parse("2026-06-01T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    @Test
    void getWithKnownFlightIdRendersChangeForm() {
        String flightId = UUID.randomUUID().toString();
        FlightDetailsView view = new FlightDetailsView(
                FlightId.of(UUID.fromString(flightId)),
                "United", "UA100",
                AirportCode.of("SFO"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 9, 0), java.time.ZoneId.of("UTC")),
                AirportCode.of("JFK"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 14, 0), java.time.ZoneId.of("UTC")));
        given(detailsProjector.findById(any())).willReturn(Optional.of(view));

        assertThat(mockMvc.get().uri("/booked-flights/" + flightId))
                .hasStatusOk();
    }

    @Test
    void getSeedsTheLookupBoxFromTheBookingSoItCanBeRefetchedWithoutRetyping() {
        // The departure day is the one at the departure airport, which is what AeroDataBox's
        // dateLocalRole=Departure expects — here 2026-07-01 in Los Angeles, not 07-02 in UTC.
        String flightId = UUID.randomUUID().toString();
        FlightDetailsView view = new FlightDetailsView(
                FlightId.of(UUID.fromString(flightId)),
                "United", "UA100",
                AirportCode.of("SFO"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 22, 0),
                                         ZoneId.of("America/Los_Angeles")),
                AirportCode.of("JFK"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 2, 6, 30),
                                         ZoneId.of("America/New_York")));
        given(detailsProjector.findById(any())).willReturn(Optional.of(view));

        assertThat(mockMvc.get().uri("/booked-flights/" + flightId))
                .hasStatusOk()
                .bodyText()
                .contains("""
                          name="lookupFlightNumber" value="UA100\"""")
                .contains("""
                          name="lookupDepartureDate" value="2026-07-01\"""");
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
    void postOnUnknownFlightIdReRendersFormWithError() {
        willThrow(new FlightNotFound("Flight not found")).given(changeFlight).changeFlight(any(), any(), any());

        // The flight vanished between GET and POST; the error must render on the form, never be
        // handed to the view-only /booked-flights list, which silently drops flash messages.
        assertThat(mockMvc.post().uri("/booked-flights/" + UUID.randomUUID())
                .with(csrf())
                .param("airline", "X")
                .param("flightNumber", "X1")
                .param("departureAirport", "SFO")
                .param("departureDateTime", "2026-07-01T09:00")
                .param("arrivalAirport", "LAX")
                .param("arrivalDateTime", "2026-07-01T12:00"))
                .hasStatusOk()
                .bodyText()
                .contains("Flight not found");
    }

    @Test
    void lookupReturningSeveralSegmentsOffersTheLegsInsteadOfGuessing() {
        given(aeroDataBoxClient.lookup("UA1604", LocalDate.of(2026, 9, 21)))
                .willReturn(new FlightLookupCandidates(List.of(
                        leg("RDU", 8, 15, "DEN", 9, 55),
                        leg("DEN", 11, 20, "RNO", 12, 35))));

        assertThat(mockMvc.post().uri("/booked-flights/" + UUID.randomUUID() + "/lookup")
                .with(csrf())
                .param("lookupFlightNumber", "UA1604")
                .param("lookupDepartureDate", "2026-09-21"))
                .hasStatusOk()
                .bodyText()
                .contains("Which flight?")
                .contains("Leg 1", "Leg 2", "Whole trip")
                // The bound field stays empty until a leg is picked; it is matched by its id,
                // which the chooser's hidden inputs lack.
                .contains(boundField("departureAirport", ""));
    }

    @Test
    void pickingALegFillsTheFormAndKeepsTheFlightIdFromThePath() {
        String flightId = UUID.randomUUID().toString();

        assertThat(mockMvc.post().uri("/booked-flights/" + flightId + "/lookup/select")
                .with(csrf())
                // A tampered flightId in the body must lose to the path.
                .param("flightId", "not-the-path-id")
                .param("airline", "United Airlines")
                .param("flightNumber", "UA1604")
                .param("departureAirport", "DEN")
                .param("departureDateTime", "2026-09-21T11:20")
                .param("arrivalAirport", "RNO")
                .param("arrivalDateTime", "2026-09-21T12:35")
                .param("lookupFlightNumber", "UA1604")
                .param("lookupDepartureDate", "2026-09-21"))
                .hasStatusOk()
                .bodyText()
                .contains(boundField("departureAirport", "DEN"))
                .contains(boundField("flightId", flightId))
                .doesNotContain("not-the-path-id");
    }

    /**
     * The markup Thymeleaf's {@code th:field} emits for a form-bound input. The id is what
     * separates a bound field from the chooser's same-named hidden inputs.
     */
    private static String boundField(String name, String value) {
        return "id=\"%s\" name=\"%s\" value=\"%s\"".formatted(name, name, value);
    }

    private static FlightLookupResult leg(String from, int departHour, int departMinute,
                                          String to, int arriveHour, int arriveMinute) {
        return new FlightLookupResult(
                "United Airlines", "UA1604",
                from, LocalDateTime.of(2026, 9, 21, departHour, departMinute), "America/New_York",
                to, LocalDateTime.of(2026, 9, 21, arriveHour, arriveMinute), "America/Denver");
    }
}
