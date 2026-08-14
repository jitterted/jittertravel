package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.FlightBooking;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(BookFlightController.class)
@WithMockUser(roles = "OWNER")
class BookFlightWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    FlightBooking flightBooking;

    @MockitoBean
    AeroDataBoxClient aeroDataBoxClient;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(flightBooking.isReadOnly()).willReturn(false);
        given(clock.instant()).willReturn(Instant.parse("2026-06-01T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    @Test
    void getBookFlightFormReturnsOk() {
        assertThat(mockMvc.get().uri("/book-flight"))
                .hasStatusOk();
    }

    @Test
    void getBookFlightFormWithDateSeedsTheLookupDateInput() {
        // Arriving from the calendar's "Add flight" must not mean retyping the date to look it up.
        assertThat(mockMvc.get().uri("/book-flight?date=2026-09-21"))
                .hasStatusOk()
                .bodyText()
                .contains("""
                          name="lookupDepartureDate" value="2026-09-21\"""");
    }

    @Test
    void lookupReturningOneSegmentPrefillsTheFormDirectly() {
        given(aeroDataBoxClient.lookup("UA1604", LocalDate.of(2026, 9, 21)))
                .willReturn(new FlightLookupCandidates(List.of(leg("RDU", 8, 15, "DEN", 9, 55))));

        assertThat(mockMvc.post().uri("/book-flight/lookup")
                .with(csrf())
                .param("lookupFlightNumber", "UA1604")
                .param("lookupDepartureDate", "2026-09-21"))
                .hasStatusOk()
                .bodyText()
                .contains(boundField("departureAirport", "RDU"))
                .doesNotContain("Which flight?");
    }

    @Test
    void lookupReturningSeveralSegmentsOffersEachLegPlusTheWholeTrip() {
        given(aeroDataBoxClient.lookup("UA1604", LocalDate.of(2026, 9, 21)))
                .willReturn(new FlightLookupCandidates(List.of(
                        leg("RDU", 8, 15, "DEN", 9, 55),
                        leg("DEN", 11, 20, "RNO", 12, 35))));

        assertThat(mockMvc.post().uri("/book-flight/lookup")
                .with(csrf())
                .param("lookupFlightNumber", "UA1604")
                .param("lookupDepartureDate", "2026-09-21"))
                .hasStatusOk()
                .bodyText()
                .contains("Which flight?")
                .contains("Leg 1", "Leg 2", "Whole trip")
                // Nothing is guessed into the form: the bound field stays empty until a leg is
                // picked. (It is matched by its id, which the chooser's hidden inputs lack —
                // those legitimately carry name="departureAirport" value="RDU".)
                .contains(boundField("departureAirport", ""));
    }

    @Test
    void pickingALegFillsTheFormWithoutASecondApiCall() {
        assertThat(mockMvc.post().uri("/book-flight/lookup/select")
                .with(csrf())
                .param("flightId", "550e8400-e29b-41d4-a716-446655440000")
                .param("airline", "United Airlines")
                .param("flightNumber", "UA1604")
                .param("departureAirport", "DEN")
                .param("departureDateTime", "2026-09-21T11:20")
                .param("departureZone", "America/Denver")
                .param("arrivalAirport", "RNO")
                .param("arrivalDateTime", "2026-09-21T12:35")
                .param("arrivalZone", "America/Los_Angeles")
                .param("lookupFlightNumber", "UA1604")
                .param("lookupDepartureDate", "2026-09-21"))
                .hasStatusOk()
                .bodyText()
                .contains(boundField("departureAirport", "DEN"))
                .contains(boundField("arrivalAirport", "RNO"))
                .contains(boundField("departureDateTime", "2026-09-21T11:20"));

        verifyNoInteractions(aeroDataBoxClient);
    }

    @Test
    void bookFlightPostRedirectsToBookedFlights() {
        assertThat(mockMvc.post().uri("/book-flight")
                .with(csrf())
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
