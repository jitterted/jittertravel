package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GatheringPlanning;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(PlanGatheringController.class)
@WithMockUser(roles = "OWNER")
class PlanGatheringWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    GatheringPlanning gatheringPlanning;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(Instant.parse("2026-06-01T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    @Test
    void planGatheringFormRendersSuccessfully() {
        assertThat(mockMvc.get().uri("/plan-gathering"))
                .hasStatusOk();
    }

    @Test
    void planGatheringPostRedirectsToPlannedGatherings() {
        assertThat(mockMvc.post().uri("/plan-gathering")
                .with(csrf())
                .param("gatheringId", "550e8400-e29b-41d4-a716-446655440000")
                .param("title", "London Java Community — November Meetup")
                .param("venueName", "Skills Matter")
                .param("street", "1 Example Street")
                .param("city", "London")
                .param("region", "")
                .param("country", "GB")
                .param("postalCode", "EC1A 1BB")
                .param("date", "2026-07-15")
                .param("startTime", "18:00")
                .param("endTime", "21:00")
                .param("speaking", "true")
                .param("infoUrl", ""))
                .hasStatus3xxRedirection();
    }
}
