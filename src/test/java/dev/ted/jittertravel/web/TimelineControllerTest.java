package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.TimelineCommand;
import dev.ted.jittertravel.infrastructure.TimelineEntry;
import dev.ted.jittertravel.infrastructure.TimelineEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

@WebMvcTest(TimelineController.class)
@WithMockUser
class TimelineControllerTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    PostgresPersister persister;

    @Test
    void commandLogUrlMapsToOkWithHtmlContentType() {
        given(persister.countCommands()).willReturn(0);
        given(persister.loadTimelinePage(anyInt(), anyInt())).willReturn(List.of());

        assertThat(mockMvc.get().uri("/admin/commandlog"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void rendersStatusBadgesForSucceededFailedAndPendingCommands() {
        given(persister.countCommands()).willReturn(3);
        given(persister.loadTimelinePage(anyInt(), anyInt())).willReturn(List.of(
                entry("SUCCEEDED", true),
                entry("FAILED_DOMAIN", false),
                entry("PENDING", false)
        ));

        assertThat(mockMvc.get().uri("/admin/commandlog"))
                .hasStatusOk()
                .bodyText()
                .contains("Failed: domain")
                .contains("Pending");
    }

    private static TimelineEntry entry(String status, boolean withEvent) {
        UUID commandId = UUID.randomUUID();
        TimelineCommand command = new TimelineCommand(
                commandId,
                OffsetDateTime.now(),
                "dev.ted.jittertravel.web.PlanTentativeConferenceRequest",
                "{}",
                status
        );
        List<TimelineEvent> events = withEvent
                ? List.of(new TimelineEvent(1L, UUID.randomUUID(), OffsetDateTime.now(),
                        "dev.ted.jittertravel.domain.ConferenceTentativelyPlanned", "{}"))
                : List.of();
        return new TimelineEntry(command, events, command.failed(), false);
    }
}
