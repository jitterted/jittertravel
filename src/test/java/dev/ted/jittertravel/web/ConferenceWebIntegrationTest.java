package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferencePlanning;
import dev.ted.jittertravel.application.TentativeConferenceProjector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(PlanConferenceController.class)
class ConferenceWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    ConferencePlanning conferencePlanning;

    @MockitoBean
    TentativeConferenceProjector projector;

    @Test
    void planConferencePostRedirectsToTentativeConferences() {
        given(conferencePlanning.isReadOnly()).willReturn(false);

        assertThat(mockMvc.post().uri("/plan-conference")
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
                .hasRedirectedUrl("/tentative-conferences");
    }

    @Test
    void tentativeConferencesPageRendersOk() {
        given(projector.views()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/tentative-conferences"))
                .hasStatusOk();
    }
}
