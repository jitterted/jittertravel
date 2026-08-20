package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GroundTransferEndpointChoices;
import dev.ted.jittertravel.application.GroundTransferEndpointOptions;
import dev.ted.jittertravel.application.GroundTransferPlanning;
import dev.ted.jittertravel.application.SameTransferEndpoints;
import dev.ted.jittertravel.application.TransferEndpointOption;
import dev.ted.jittertravel.application.UnknownTransferEndpoint;
import dev.ted.jittertravel.application.ZoneResolutionException;
import dev.ted.jittertravel.domain.InvalidGroundTransferTimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * The Thymeleaf half: the template actually renders, the option lists reach the two selects, and
 * <strong>every</strong> rejection comes back on the page hosting the form rather than as a 500 or
 * a redirect to somewhere that cannot show it.
 */
@WebMvcTest(PlanGroundTransferController.class)
@WithMockUser(roles = "OWNER")
class PlanGroundTransferWebIntegrationTest {

    private static final String TRANSFER_ID = "770e8400-e29b-41d4-a716-446655440000";
    private static final String HOTEL_TOKEN = "hotel:99999999-9999-9999-9999-999999999999";

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    GroundTransferPlanning groundTransferPlanning;

    @MockitoBean
    GroundTransferEndpointOptions endpointOptions;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(Instant.parse("2026-09-01T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
        given(endpointOptions.choicesAt(any())).willReturn(new GroundTransferEndpointChoices(
                List.of(new TransferEndpointOption("airport:DEN",
                        "DEN — Denver · arrive Mon Sep 14, 11:30 AM (UA 59)",
                        "2026-09-14", "11:30")),
                List.of(new TransferEndpointOption("airport:SFO",
                        "SFO — San Francisco · depart Fri Sep 18, 2:00 PM (UA 60)",
                        "2026-09-18", "14:00")),
                List.of(new TransferEndpointOption(HOTEL_TOKEN, "Marriott Lone Tree — Lone Tree"))));
    }

    @Test
    void planGroundTransferFormRendersSuccessfully() {
        assertThat(mockMvc.get().uri("/plan-ground-transfer"))
                .hasStatusOk();
    }

    /**
     * The endpoint selects are the whole form — Ted never types an address (D3) — so a Thymeleaf
     * mistake in the optgroup blocks would leave him with nothing to pick, and that only surfaces
     * at render time.
     */
    @Test
    void theFromSelectOffersArrivalsAndTheToSelectOffersDepartures() {
        assertThat(mockMvc.get().uri("/plan-ground-transfer"))
                .hasStatusOk()
                .bodyText()
                .contains("<optgroup label=\"Flight arrivals\">")
                .contains("<optgroup label=\"Flight departures\">")
                .contains("<optgroup label=\"Hotels\">")
                .contains("<option value=\"" + HOTEL_TOKEN + "\">Marriott Lone Tree — Lone Tree</option>");
    }

    /**
     * The data attributes are the contract between the option list and the inline prefill script:
     * without them a flight leg silently fills nothing in, and Ted is back to remembering the time
     * or opening another tab. The script's *behavior* is covered in the js tier.
     */
    @Test
    void eachFlightLegOptionCarriesTheDateAndTimeThePrefillScriptReads() {
        assertThat(mockMvc.get().uri("/plan-ground-transfer"))
                .hasStatusOk()
                .bodyText()
                .contains("data-date=\"2026-09-14\"")
                .contains("data-time=\"11:30\"")
                .contains("data-date=\"2026-09-18\"")
                .contains("data-time=\"14:00\"")
                .as("the leg is identified by its time and flight, not just its airport")
                .contains("DEN — Denver · arrive Mon Sep 14, 11:30 AM (UA 59)");
    }

    @Test
    void aHotelOptionCarriesNoTimesToPrefill() {
        assertThat(mockMvc.get().uri("/plan-ground-transfer"))
                .hasStatusOk()
                .bodyText()
                .as("a check-in time is not when a taxi runs")
                .contains("<option value=\"" + HOTEL_TOKEN + "\">Marriott Lone Tree — Lone Tree</option>");
    }

    @Test
    void withNothingBookedTheFormSaysSoRatherThanOfferingEmptySelects() {
        given(endpointOptions.choicesAt(any()))
                .willReturn(new GroundTransferEndpointChoices(List.of(), List.of(), List.of()));

        assertThat(mockMvc.get().uri("/plan-ground-transfer"))
                .hasStatusOk()
                .bodyText()
                .contains("Nothing to travel between yet")
                .doesNotContain("<optgroup label=\"Flight arrivals\">")
                .doesNotContain("<optgroup label=\"Flight departures\">");
    }

    @Test
    void planGroundTransferPostRedirectsToCalendar() {
        assertThat(post("airport:DEN", HOTEL_TOKEN, "12:00", "12:45"))
                .hasStatus3xxRedirection();
    }

    @Test
    void anEndpointThatNoLongerResolvesRerendersTheFormWithTheReason() {
        willThrow(new UnknownTransferEndpoint(
                "That hotel booking is no longer available — it may have been cancelled"))
                .given(groundTransferPlanning).planGroundTransfer(any());

        assertThat(post("airport:DEN", HOTEL_TOKEN, "12:00", "12:45"))
                .hasStatusOk()
                .bodyText()
                .as("the form comes back with the reason, not a redirect to a page that cannot show it")
                .contains("That hotel booking is no longer available");
    }

    @Test
    void identicalEndpointsRerenderTheFormWithTheReason() {
        willThrow(new SameTransferEndpoints("A transfer needs two different places"))
                .given(groundTransferPlanning).planGroundTransfer(any());

        assertThat(post("airport:DEN", "airport:DEN", "12:00", "12:45"))
                .hasStatusOk()
                .bodyText()
                .contains("A transfer needs two different places");
    }

    @Test
    void anUnresolvableZoneRerendersTheFormWithTheReason() {
        willThrow(new ZoneResolutionException("Springfield, Freedonia"))
                .given(groundTransferPlanning).planGroundTransfer(any());

        assertThat(post("airport:DEN", HOTEL_TOKEN, "12:00", "12:45"))
                .hasStatusOk()
                .bodyText()
                .contains("Could not determine the time zone of one of those places");
    }

    @Test
    void anInvertedTimeRangeRerendersTheFormWithTheReason() {
        willThrow(new InvalidGroundTransferTimeRange("Arrival time must be after departure time"))
                .given(groundTransferPlanning).planGroundTransfer(any());

        assertThat(post("airport:DEN", HOTEL_TOKEN, "12:45", "12:00"))
                .hasStatusOk()
                .bodyText()
                .contains("Arrival time must be after departure time");
    }

    private MvcTestResult post(
            String origin, String destination, String departureTime, String arrivalTime) {
        return mockMvc.post().uri("/plan-ground-transfer")
                .with(csrf())
                .param("groundTransferId", TRANSFER_ID)
                .param("origin", origin)
                .param("destination", destination)
                .param("date", "2026-09-14")
                .param("departureTime", departureTime)
                .param("arrivalTime", arrivalTime)
                .exchange();
    }
}
