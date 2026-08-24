package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelPrivateEvent;
import dev.ted.jittertravel.application.PrivateEventDetailsView;
import dev.ted.jittertravel.application.PrivateEventDetailsViewProjector;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventNotFound;
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

@WebMvcTest(CancelPrivateEventController.class)
@WithMockUser(roles = "OWNER")
class CancelPrivateEventControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    CancelPrivateEvent cancelPrivateEvent;

    @MockitoBean
    PrivateEventDetailsViewProjector detailsProjector;

    private static PrivateEventDetailsView viewFor(UUID privateEventId) {
        return viewFor(privateEventId, "Chez Moi");
    }

    private static PrivateEventDetailsView viewFor(UUID privateEventId, String venueName) {
        return new PrivateEventDetailsView(
                PrivateEventId.of(privateEventId),
                "Dinner with the Smiths",
                venueName,
                "London",
                "GB",
                LocalDateTime.of(2026, 6, 1, 19, 0),
                LocalDateTime.of(2026, 6, 1, 22, 0));
    }

    @Test
    void getRendersTheConfirmationPageNamingTheEveningBeingCancelled() {
        UUID privateEventId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(privateEventId)));

        assertThat(mockMvc.get().uri("/planned-private-events/" + privateEventId + "/cancel"))
                .hasStatusOk()
                .bodyText()
                .contains("Dinner with the Smiths")
                .contains("Chez Moi")
                .contains("London, GB")
                .contains("Mon, Jun 1, 2026")
                .contains("7:00 PM")
                .contains("10:00 PM");
    }

    /**
     * Backing out returns to the list this page is reached from, not to the itinerary — the same
     * shape {@code decline-conference.html} has always had. Nothing pinned this link before, which
     * is exactly how it could be retargeted with nobody noticing.
     */
    @Test
    void keepLinkGoesBackToThePlannedPrivateEventsList() {
        UUID privateEventId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(privateEventId)));

        assertThat(mockMvc.get().uri("/planned-private-events/" + privateEventId + "/cancel"))
                .hasStatusOk()
                .bodyText()
                .contains("<a class=\"keep-link\" href=\"/planned-private-events\">"
                          + "Keep it — back to the list</a>")
                .doesNotContain("Keep it — back to the itinerary");
    }

    /** A venue is optional on a private event; an empty line would say less in more space. */
    @Test
    void getOmitsTheVenueLineWhenNoneWasRecorded() {
        UUID privateEventId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(privateEventId, "")));

        assertThat(mockMvc.get().uri("/planned-private-events/" + privateEventId + "/cancel"))
                .hasStatusOk()
                .bodyText()
                .contains("Dinner with the Smiths")
                .doesNotContain("Chez Moi");
    }

    @Test
    void getOffersAnOptionalReasonToRecord() {
        UUID privateEventId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(privateEventId)));

        assertThat(mockMvc.get().uri("/planned-private-events/" + privateEventId + "/cancel"))
                .hasStatusOk()
                .bodyText()
                .contains("Reason (optional)");
    }

    @Test
    void theConfirmationIsAPlainOneWithNoTypedWordToCopy() {
        // Amber, not red: planning the evening again puts it back, so the typed-word gate that
        // guards irreversible actions (CLAUDE.md) does not apply here.
        UUID privateEventId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(privateEventId)));

        assertThat(mockMvc.get().uri("/planned-private-events/" + privateEventId + "/cancel"))
                .bodyText()
                .contains("Cancel this event")
                .doesNotContain("DELETE");
    }

    @Test
    void getOnAnAlreadyCancelledEventRedirectsToTheItinerary() {
        given(detailsProjector.findById(any())).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/planned-private-events/" + UUID.randomUUID() + "/cancel"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/itinerary");
    }

    @Test
    void cancellingLandsBackOnTheItineraryDayTheEventLeft() {
        UUID privateEventId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(privateEventId)));

        assertThat(mockMvc.post().uri("/planned-private-events/" + privateEventId + "/cancel")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/itinerary?date=2026-06-01");
    }

    @Test
    void requestCarriesThePathIdAndTheTypedReason() {
        UUID privateEventId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(privateEventId)));

        mockMvc.post().uri("/planned-private-events/" + privateEventId + "/cancel")
                .param("reason", "Rescheduled to Friday")
                .with(csrf())
                .exchange();

        then(cancelPrivateEvent).should().cancelPrivateEvent(any(),
                eq(new CancelPrivateEventRequest(privateEventId, "Rescheduled to Friday")));
    }

    @Test
    void anOmittedReasonArrivesAsTheEmptyStringRatherThanNull() {
        UUID privateEventId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(privateEventId)));

        mockMvc.post().uri("/planned-private-events/" + privateEventId + "/cancel")
                .with(csrf())
                .exchange();

        then(cancelPrivateEvent).should().cancelPrivateEvent(any(),
                eq(new CancelPrivateEventRequest(privateEventId, "")));
    }

    @Test
    void alreadyGoneEventRedirectsToTheItineraryInsteadOfThrowing() {
        UUID privateEventId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(privateEventId)));
        willThrow(new PrivateEventNotFound("No private event found to cancel"))
                .given(cancelPrivateEvent).cancelPrivateEvent(any(), any());

        assertThat(mockMvc.post().uri("/planned-private-events/" + privateEventId + "/cancel")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/itinerary");
    }

    @Test
    void malformedPrivateEventIdRedirectsInsteadOfThrowing() {
        assertThat(mockMvc.post().uri("/planned-private-events/not-a-uuid/cancel")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/itinerary");

        then(cancelPrivateEvent).shouldHaveNoInteractions();
    }
}
