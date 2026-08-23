package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.SessionizePrefillService;
import dev.ted.jittertravel.infrastructure.SessionizePrefillService.SessionizePrefill;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Mapping, status and JSON shape only — the reading of Sessionize itself is unit-tested in
 * {@code SessionizePrefillServiceTest}.
 */
@WebMvcTest(SessionizePrefillController.class)
@WithMockUser(roles = "OWNER")
class SessionizePrefillControllerTest {

    private static final String PASTED = "https://sessionize.com/jfokus-2027/";

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    SessionizePrefillService prefillService;

    @Test
    void aReadableConferencePageComesBackAsTheFormsOwnFieldNames() {
        when(prefillService.prefill(PASTED)).thenReturn(Optional.of(new SessionizePrefill(
                "Jfokus 2027",
                "https://www.jfokus.se/",
                "2027-02-08T09:00",
                "2027-02-10T17:00",
                "2026-10-01T08:30",
                PASTED,
                "Stockholm Waterfront Congress Centre",
                "Stockholm",
                "Sweden",
                "Europe/Stockholm")));

        // Every key here is a form property name the widget writes by — venue-prefixed, and
        // venueState rather than venueRegion. They are the interface between the two halves.
        assertThat(mockMvc.get().uri("/api/sessionize-prefill?url={url}", PASTED))
                .hasStatusOk()
                .bodyJson()
                .isEqualTo("""
                           {"name": "Jfokus 2027",
                            "infoUrl": "https://www.jfokus.se/",
                            "startDate": "2027-02-08T09:00",
                            "endDate": "2027-02-10T17:00",
                            "cfpClosesOn": "2026-10-01T08:30",
                            "cfpSubmissionUrl": "https://sessionize.com/jfokus-2027/",
                            "venueName": "Stockholm Waterfront Congress Centre",
                            "venueCity": "Stockholm",
                            "venueCountry": "Sweden",
                            "deadlineZone": "Europe/Stockholm"}""");
    }

    @Test
    void aPageNothingCouldBeReadFromIs422() {
        // The widget distinguishes this from a 404/405 and says "we couldn't read that page"
        // rather than "the lookup is broken".
        when(prefillService.prefill(anyString())).thenReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/api/sessionize-prefill?url={url}", "https://example.com/"))
                .hasStatus(422);
    }

    @Test
    void aRequestWithNoUrlIsABadRequestRatherThanA500() {
        assertThat(mockMvc.get().uri("/api/sessionize-prefill"))
                .hasStatus(400);
    }
}
