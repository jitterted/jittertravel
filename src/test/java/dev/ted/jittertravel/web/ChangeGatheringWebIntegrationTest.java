package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeGathering;
import dev.ted.jittertravel.application.GatheringDetailsView;
import dev.ted.jittertravel.application.GatheringDetailsViewProjector;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.GatheringDateNotInFuture;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringNotFound;
import dev.ted.jittertravel.domain.ZonedTimestamp;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(ChangeGatheringController.class)
@WithMockUser(roles = "OWNER")
class ChangeGatheringWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    ChangeGathering changeGathering;

    @MockitoBean
    GatheringDetailsViewProjector detailsProjector;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(Instant.parse("2026-06-01T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    @Test
    void getWithKnownGatheringIdRendersChangeForm() {
        String gatheringId = UUID.randomUUID().toString();
        GatheringDetailsView view = new GatheringDetailsView(
                GatheringId.of(UUID.fromString(gatheringId)),
                "London Java Community",
                "Skills Matter",
                new Address("1 Example St", "London", "", "EC1A 1BB", "GB", null),
                ZonedTimestamp.fromLocal(LocalDate.of(2026, 7, 15).atTime(18, 0), ZoneId.of("Europe/London")),
                ZonedTimestamp.fromLocal(LocalDate.of(2026, 7, 15).atTime(21, 0), ZoneId.of("Europe/London")),
                true,
                "https://meetup.com/ljc/events/123");
        given(detailsProjector.findById(any())).willReturn(Optional.of(view));

        assertThat(mockMvc.get().uri("/planned-gatherings/" + gatheringId))
                .hasStatusOk();
    }

    @Test
    void getOnUnknownGatheringIdRedirectsToPlannedGatherings() {
        given(detailsProjector.findById(any())).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/planned-gatherings/" + UUID.randomUUID()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/planned-gatherings");
    }

    @Test
    void getOnMalformedGatheringIdRedirectsToPlannedGatherings() {
        assertThat(mockMvc.get().uri("/planned-gatherings/not-a-uuid"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/planned-gatherings");
    }

    @Test
    void postWithKnownGatheringIdRedirectsToPlannedGatherings() {
        assertThat(mockMvc.post().uri("/planned-gatherings/" + UUID.randomUUID())
                .with(csrf())
                .param("title", "London Java Community — December")
                .param("venueName", "Federation House")
                .param("street", "2 New St")
                .param("city", "Manchester")
                .param("region", "")
                .param("country", "GB")
                .param("postalCode", "M1 1AA")
                .param("date", "2026-07-15")
                .param("startTime", "18:00")
                .param("endTime", "21:00")
                .param("speaking", "true")
                .param("infoUrl", ""))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/planned-gatherings");
    }

    @Test
    void postOnUnknownGatheringIdRedirectsToPlannedGatherings() {
        willThrow(new GatheringNotFound("No gathering exists with that gatheringId"))
                .given(changeGathering).changeGathering(any(), any(), any());

        assertThat(mockMvc.post().uri("/planned-gatherings/" + UUID.randomUUID())
                .with(csrf())
                .param("title", "Whatever")
                .param("city", "London")
                .param("date", "2026-07-15")
                .param("startTime", "18:00")
                .param("endTime", "21:00"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/planned-gatherings");
    }

    @Test
    void postWithPastDateRendersFormAgain() {
        willThrow(new GatheringDateNotInFuture("Gathering date must be in the future"))
                .given(changeGathering).changeGathering(any(), any(), any());

        assertThat(mockMvc.post().uri("/planned-gatherings/" + UUID.randomUUID())
                .with(csrf())
                .param("title", "Whatever")
                .param("city", "London")
                .param("date", "2020-01-01")
                .param("startTime", "18:00")
                .param("endTime", "21:00"))
                .hasStatusOk();
    }
}
