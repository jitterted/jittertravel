package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PrivateEventPlanning;
import dev.ted.jittertravel.domain.InvalidEnteredLocation;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.LocationField;
import dev.ted.jittertravel.domain.LocationRole;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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

@Tag("spring")
@WebMvcTest(PlanPrivateEventController.class)
@WithMockUser(roles = "OWNER")
class PlanPrivateEventWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    PrivateEventPlanning privateEventPlanning;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(Instant.parse("2026-06-01T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    @Test
    void planPrivateEventFormRendersSuccessfully() {
        assertThat(mockMvc.get().uri("/plan-private-event"))
                .hasStatusOk();
    }

    @Test
    void planPrivateEventPostRedirectsToCalendar() {
        assertThat(mockMvc.post().uri("/plan-private-event")
                .with(csrf())
                .param("privateEventId", "550e8400-e29b-41d4-a716-446655440000")
                .param("title", "Dinner with the Smiths")
                .param("venueName", "Alo")
                .param("street", "163 Spadina Ave")
                .param("city", "Toronto")
                .param("region", "ON")
                .param("country", "Canada")
                .param("postalCode", "M5V 2L6")
                .param("date", "2026-07-15")
                .param("startTime", "19:00")
                .param("endTime", "22:00"))
                .hasStatus3xxRedirection();
    }

    /**
     * The zone picker is the only escape hatch when a venue's location can't be resolved, so the
     * form has to actually render it — and a Thymeleaf mistake in that block only surfaces at
     * render time, never at compile time.
     */
    @Test
    void formOffersTheZonePickerDefaultingToDeriveFromLocation() {
        assertThat(mockMvc.get().uri("/plan-private-event"))
                .hasStatusOk()
                .bodyText()
                .contains("Derive from location (default)")
                .contains("US Central")
                .contains("Western Europe");
    }

    /**
     * The rejection path: an unresolvable venue must come back as a field error on the form the
     * traveler is looking at, not a 500 — otherwise the picker they need is unreachable.
     */
    @Test
    void anUnresolvableVenueRerendersTheFormWithAZoneFieldError() {
        willThrow(new ZoneResolutionException("Springfield, Freedonia"))
                .given(privateEventPlanning).planPrivateEvent(any(), any());

        assertThat(mockMvc.post().uri("/plan-private-event")
                .with(csrf())
                .param("privateEventId", "550e8400-e29b-41d4-a716-446655440000")
                .param("title", "Some Dinner")
                .param("venueName", "Some Venue")
                .param("street", "1 Example Street")
                .param("city", "Springfield")
                .param("region", "")
                .param("country", "Freedonia")
                .param("postalCode", "")
                .param("date", "2026-07-15")
                .param("startTime", "19:00")
                .param("endTime", "22:00"))
                .hasStatusOk()
                .bodyText()
                .as("the form comes back with the picker and an explanation, not a redirect")
                .contains("Could not determine the time zone from the location");
    }

    /** The render check for the Country and Region inputs: each one's error span exists. */
    @Test
    void aRefusedVenueCountryAndStateRenderUnderTheirInputs() {
        willThrow(new InvalidEnteredLocation(List.of(
                new InvalidLocationEntry(LocationRole.VENUE, LocationField.COUNTRY, "Unknown country"),
                new InvalidLocationEntry(LocationRole.VENUE, LocationField.REGION,
                                         "State required for United States"))))
                .given(privateEventPlanning).planPrivateEvent(any(), any());

        MvcTestResult result = mockMvc.post().uri("/plan-private-event")
                .with(csrf())
                .param("privateEventId", "550e8400-e29b-41d4-a716-446655440000")
                .param("title", "Some Dinner")
                .param("venueName", "Some Venue")
                .param("street", "")
                .param("city", "Centennial")
                .param("region", "")
                .param("country", "US")
                .param("postalCode", "")
                .param("date", "2026-07-15")
                .param("startTime", "19:00")
                .param("endTime", "22:00")
                .exchange();

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("planPrivateEvent")
                .hasFieldErrorCode("country", "invalidLocation")
                .hasFieldErrorCode("region", "invalidLocation");
        assertThat(result)
                .bodyText()
                .contains("<span class=\"error\">Unknown country</span>")
                .contains("<span class=\"error\">State required for United States</span>");
    }
}
