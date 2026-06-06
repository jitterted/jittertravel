package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GatheringPlanning;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(ClearConflictController.class)
@WithMockUser
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
}
