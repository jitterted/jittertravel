package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.TimelineCommand;
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
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(PendingCommandsController.class)
@WithMockUser
class PendingCommandsControllerTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    PostgresPersister persister;

    @Test
    void listMapsToOkWithHtmlContentType() {
        org.mockito.BDDMockito.given(persister.findPendingCommands()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/admin/pending-commands"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void listRendersPayloadAndActionButtons() {
        UUID commandId = UUID.randomUUID();
        org.mockito.BDDMockito.given(persister.findPendingCommands()).willReturn(List.of(
                new TimelineCommand(
                        commandId,
                        OffsetDateTime.now(),
                        "dev.ted.jittertravel.web.BookFlightRequest",
                        "{\n  \"airline\" : \"Acme Air\"\n}",
                        "PENDING")
        ));

        assertThat(mockMvc.get().uri("/admin/pending-commands"))
                .hasStatusOk()
                .bodyText()
                .contains("Acme Air")
                .contains("Abandon Command")
                .contains("Keep as Pending");
    }

    @Test
    void abandonMarksCommandAndRedirects() {
        UUID commandId = UUID.randomUUID();

        assertThat(mockMvc.post().uri("/admin/pending-commands/{id}/abandon", commandId)
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasHeader("Location", "/admin/pending-commands");

        verify(persister).abandonCommand(commandId);
    }

    @Test
    void keepRedirectsWithoutChangingStatus() {
        UUID commandId = UUID.randomUUID();

        assertThat(mockMvc.post().uri("/admin/pending-commands/{id}/keep", commandId)
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasHeader("Location", "/admin/pending-commands");

        verify(persister, org.mockito.Mockito.never()).abandonCommand(commandId);
    }
}
