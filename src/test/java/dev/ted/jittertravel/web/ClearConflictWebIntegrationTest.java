package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GatheringPlanning;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(ClearConflictController.class)
@WithMockUser(roles = "OWNER")
class ClearConflictWebIntegrationTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    GatheringPlanning gatheringPlanning;

    @Test
    void getFormRendersOk() {
        assertThat(mockMvc.get().uri("/clear-conflict")
                .param("gatheringId", UUID.randomUUID().toString())
                .param("conferenceId", UUID.randomUUID().toString())
                .param("gatheringName", "BRU JUG")
                .param("gatheringCity", "Brussels")
                .param("conferenceName", "JavaOne")
                .param("conferenceCity", "Amsterdam")
                .param("date", "2026-09-16"))
                .hasStatusOk();
    }

    @Test
    void postRedirectsToScheduleProblems() {
        assertThat(mockMvc.post().uri("/clear-conflict")
                .with(csrf())
                .param("gatheringId", UUID.randomUUID().toString())
                .param("conferenceId", UUID.randomUUID().toString())
                .param("reason", "attending virtually"))
                .hasStatus3xxRedirection()
                .hasHeader("Location", "/schedule-problems");
    }

    @Test
    void malformedIdRerendersFormWithErrorRatherThanFailing() {
        assertThat(mockMvc.post().uri("/clear-conflict")
                .with(csrf())
                .param("gatheringId", "not-a-uuid")
                .param("conferenceId", UUID.randomUUID().toString())
                .param("gatheringName", "BRU JUG")
                .param("gatheringCity", "Brussels")
                .param("conferenceName", "JavaOne")
                .param("conferenceCity", "Amsterdam")
                .param("date", "2026-09-16")
                .param("reason", "attending virtually"))
                .hasStatusOk()
                .bodyText()
                .contains("This conflict could not be identified")
                // The visible summary survives the re-render, so the page still says which
                // conflict it is. Match the rendered <strong> markup, not just the bare text:
                // the ids also ride as hidden inputs (value="BRU JUG"), so a plain substring
                // check would pass even if the visible summary rendered blank.
                .contains("<strong>BRU JUG</strong>")
                .contains("<strong>JavaOne</strong>");
    }

    @Test
    void serviceFailureRerendersFormWithErrorRatherThanFailing() {
        doThrow(new IllegalStateException("database unreachable"))
                .when(gatheringPlanning)
                .clearConflict(any(), any(), any(), any());

        assertThat(mockMvc.post().uri("/clear-conflict")
                .with(csrf())
                .param("gatheringId", UUID.randomUUID().toString())
                .param("conferenceId", UUID.randomUUID().toString())
                .param("gatheringName", "BRU JUG")
                .param("gatheringCity", "Brussels")
                .param("conferenceName", "JavaOne")
                .param("conferenceCity", "Amsterdam")
                .param("date", "2026-09-16"))
                .hasStatusOk()
                .bodyText()
                .contains("Could not clear this conflict: database unreachable");
    }

    @Test
    void readOnlyModeRedirectsToTheReadOnlyPage() {
        doThrow(new ReadOnlyModeException("read-only"))
                .when(gatheringPlanning)
                .clearConflict(any(), any(), any(), any());

        assertThat(mockMvc.post().uri("/clear-conflict")
                .with(csrf())
                .param("gatheringId", UUID.randomUUID().toString())
                .param("conferenceId", UUID.randomUUID().toString()))
                .hasStatus3xxRedirection()
                .hasHeader("Location", "/read-only");
    }
}
