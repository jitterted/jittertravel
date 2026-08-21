package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelGroundTransfer;
import dev.ted.jittertravel.application.GroundTransferDetailsView;
import dev.ted.jittertravel.application.GroundTransferDetailsViewProjector;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferNotFound;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(CancelGroundTransferController.class)
@WithMockUser(roles = "OWNER")
class CancelGroundTransferControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    CancelGroundTransfer cancelGroundTransfer;

    @MockitoBean
    GroundTransferDetailsViewProjector detailsProjector;

    private static GroundTransferDetailsView viewFor(UUID transferId) {
        return new GroundTransferDetailsView(
                GroundTransferId.of(transferId),
                "DEN",
                "Marriott Lone Tree",
                LocalDateTime.of(2026, 9, 14, 12, 0),
                LocalDateTime.of(2026, 9, 14, 12, 45));
    }

    @Test
    void getRendersTheConfirmationPageNamingBothEndsAndTheTimes() {
        UUID transferId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(transferId)));

        assertThat(mockMvc.get().uri("/ground-transfers/" + transferId + "/cancel"))
                .hasStatusOk()
                .bodyText()
                .contains("DEN → Marriott Lone Tree")
                .contains("Mon, Sep 14, 2026")
                .contains("12:00 PM")
                .contains("12:45 PM");
    }

    @Test
    void theConfirmationIsAPlainOneWithNoTypedWordToCopy() {
        // Amber, not red: entering the transfer again puts it back, so the typed-word gate that
        // guards irreversible actions (CLAUDE.md) does not apply here.
        UUID transferId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(transferId)));

        assertThat(mockMvc.get().uri("/ground-transfers/" + transferId + "/cancel"))
                .bodyText()
                .contains("Cancel this transfer")
                .doesNotContain("DELETE");
    }

    @Test
    void getOnAnAlreadyCancelledTransferRedirectsToTheItinerary() {
        given(detailsProjector.findById(any())).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/ground-transfers/" + UUID.randomUUID() + "/cancel"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/itinerary");
    }

    @Test
    void cancellingLandsBackOnTheItineraryDayTheTransferLeft() {
        UUID transferId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(transferId)));

        assertThat(mockMvc.post().uri("/ground-transfers/" + transferId + "/cancel")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/itinerary?date=2026-09-14");
    }

    @Test
    void requestCarriesThePathId() {
        UUID transferId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(transferId)));

        mockMvc.post().uri("/ground-transfers/" + transferId + "/cancel")
                .with(csrf())
                .exchange();

        then(cancelGroundTransfer).should().cancelGroundTransfer(any(),
                eq(new CancelGroundTransferRequest(transferId)));
    }

    @Test
    void alreadyGoneTransferRedirectsToTheItineraryInsteadOfThrowing() {
        UUID transferId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(transferId)));
        willThrow(new GroundTransferNotFound("No ground transfer found to cancel"))
                .given(cancelGroundTransfer).cancelGroundTransfer(any(), any());

        assertThat(mockMvc.post().uri("/ground-transfers/" + transferId + "/cancel")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/itinerary");
    }

    @Test
    void malformedTransferIdRedirectsInsteadOfThrowing() {
        assertThat(mockMvc.post().uri("/ground-transfers/not-a-uuid/cancel")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/itinerary");

        then(cancelGroundTransfer).shouldHaveNoInteractions();
    }
}
