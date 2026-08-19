package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferencePlanning;
import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.application.ZoneResolutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(PlanConferenceController.class)
@WithMockUser(roles = "OWNER")
class ConferenceWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    ConferencePlanning conferencePlanning;

    @MockitoBean
    ConferenceProjector projector;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(Instant.parse("2026-06-01T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    @Test
    void planConferencePostRedirectsToConferences() {
        given(conferencePlanning.isReadOnly()).willReturn(false);

        assertThat(mockMvc.post().uri("/plan-conference")
                .with(csrf())
                .param("conferenceId", "550e8400-e29b-41d4-a716-446655440000")
                .param("name", "Event Sourcing Conference")
                .param("startDate", "2026-07-01T09:00")
                .param("endDate", "2026-07-03T17:00")
                .param("venueName", "ES Venue")
                .param("venueStreet", "ES Street")
                .param("venueCity", "ES City")
                .param("venueCountry", "ES Country")
                .param("venuePostalCode", "ES-00000"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    /**
     * The zone picker is the only escape hatch when a venue's location can't be resolved, so the
     * form has to actually render it — and a Thymeleaf mistake in that block only surfaces at
     * render time, never at compile time.
     */
    @Test
    void formOffersTheZonePickerDefaultingToDeriveFromLocation() {
        given(conferencePlanning.isReadOnly()).willReturn(false);

        assertThat(mockMvc.get().uri("/plan-conference"))
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
        given(conferencePlanning.isReadOnly()).willReturn(false);
        willThrow(new ZoneResolutionException("Springfield, Freedonia"))
                .given(conferencePlanning).planConference(any(), any());

        assertThat(mockMvc.post().uri("/plan-conference")
                .with(csrf())
                .param("conferenceId", "550e8400-e29b-41d4-a716-446655440000")
                .param("name", "JitterConf")
                .param("startDate", "2026-07-01T09:00")
                .param("endDate", "2026-07-03T17:00")
                .param("venueName", "Some Venue")
                .param("venueStreet", "1 Example Street")
                .param("venueCity", "Springfield")
                .param("venueCountry", "Freedonia")
                .param("venuePostalCode", ""))
                .hasStatusOk()
                .bodyText()
                .as("the form comes back with the picker and an explanation, not a redirect")
                .contains("Could not determine the time zone from the location");
    }

    /**
     * The format radios drive the whole speaking pipeline (whether there's a CFP, what a rejection
     * means), and the {@code th:each} over the enum only fails at render time — so the form has to
     * actually emit all three choices.
     */
    @Test
    void formOffersAllThreeConferenceFormatRadios() {
        given(conferencePlanning.isReadOnly()).willReturn(false);

        assertThat(mockMvc.get().uri("/plan-conference"))
                .hasStatusOk()
                .bodyText()
                .contains("Call for Papers")
                .contains("Acceptance Required")
                .contains("Open Space")
                .contains("value=\"OPEN_SPACE\"");
    }

    @Test
    void conferencesPageRendersOk() {
        given(projector.views(any(), any())).willReturn(List.of());

        assertThat(mockMvc.get().uri("/conferences"))
                .hasStatusOk();
    }
}
