package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangePrivateEventMatchingLocation;
import dev.ted.jittertravel.application.PrivateEventMatchingLocationView;
import dev.ted.jittertravel.application.PrivateEventMatchingLocationViewProjector;
import dev.ted.jittertravel.domain.InvalidMatchingLocation;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventNotFound;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Thymeleaf endpoint, so it needs a {@code @WebMvcTest}: a template error only surfaces at render
 * time and a renderer unit test would never see it.
 */
@WebMvcTest(ChangePrivateEventMatchingLocationController.class)
@WithMockUser(roles = "OWNER")
class ChangePrivateEventMatchingLocationControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    ChangePrivateEventMatchingLocation changeMatchingLocation;

    @MockitoBean
    PrivateEventMatchingLocationViewProjector viewProjector;

    private static PrivateEventMatchingLocationView viewFor(UUID privateEventId,
                                                            String locationForMatching) {
        return new PrivateEventMatchingLocationView(
                PrivateEventId.of(privateEventId),
                "Dinner with the Smiths",
                "Chez Moi",
                "Centennial",
                "US",
                locationForMatching,
                LocalDateTime.of(2026, 10, 1, 19, 0),
                LocalDateTime.of(2026, 10, 1, 22, 0));
    }

    @Test
    void getRendersTheFormNamingTheEveningBeingRematched() {
        UUID privateEventId = UUID.randomUUID();
        given(viewProjector.findById(any()))
                .willReturn(Optional.of(viewFor(privateEventId, "Centennial")));

        assertThat(mockMvc.get()
                .uri("/planned-private-events/" + privateEventId + "/matching-location"))
                .hasStatusOk()
                .bodyText()
                .contains("Dinner with the Smiths")
                .contains("Chez Moi")
                .contains("Centennial, US")
                .contains("Thu, Oct 1, 2026")
                .contains("7:00 PM")
                .contains("10:00 PM");
    }

    @Test
    void theInputIsPrefilledWithTheLocationAlreadyInForce() {
        // Not the venue's city: a form offering the original value would quietly undo an earlier
        // correction the moment Ted pressed Save.
        UUID privateEventId = UUID.randomUUID();
        given(viewProjector.findById(any()))
                .willReturn(Optional.of(viewFor(privateEventId, "Lone Tree")));

        assertThat(mockMvc.get()
                .uri("/planned-private-events/" + privateEventId + "/matching-location"))
                .hasStatusOk()
                .bodyText()
                .contains("value=\"Lone Tree\"");
    }

    @Test
    void anOverriddenEveningSaysWhatItIsCurrentlyMatchedAs() {
        UUID privateEventId = UUID.randomUUID();
        given(viewProjector.findById(any()))
                .willReturn(Optional.of(viewFor(privateEventId, "Lone Tree")));

        assertThat(mockMvc.get()
                .uri("/planned-private-events/" + privateEventId + "/matching-location"))
                .hasStatusOk()
                .bodyText()
                .contains("Currently matched as");
    }

    @Test
    void anEveningMatchedInItsOwnCitySaysNothingExtra() {
        // The line exists to explain a difference; with none it would restate the address above it.
        UUID privateEventId = UUID.randomUUID();
        given(viewProjector.findById(any()))
                .willReturn(Optional.of(viewFor(privateEventId, "Centennial")));

        assertThat(mockMvc.get()
                .uri("/planned-private-events/" + privateEventId + "/matching-location"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("Currently matched as");
    }

    @Test
    void getForAnUnknownEveningRedirectsToTheListRatherThanErroring() {
        given(viewProjector.findById(any())).willReturn(Optional.empty());

        assertThat(mockMvc.get()
                .uri("/planned-private-events/" + UUID.randomUUID() + "/matching-location"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/planned-private-events");
    }

    @Test
    void getForAMalformedIdRedirectsToTheListRatherThanErroring() {
        assertThat(mockMvc.get().uri("/planned-private-events/not-a-uuid/matching-location"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/planned-private-events");
    }

    @Test
    void postAppliesTheNewMatchingLocationAndReturnsToTheList() {
        UUID privateEventId = UUID.randomUUID();
        given(viewProjector.findById(any()))
                .willReturn(Optional.of(viewFor(privateEventId, "Centennial")));

        assertThat(mockMvc.post()
                .uri("/planned-private-events/" + privateEventId + "/matching-location")
                .param("locationForMatching", "Lone Tree")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/planned-private-events");

        ArgumentCaptor<ChangePrivateEventMatchingLocationRequest> request =
                ArgumentCaptor.forClass(ChangePrivateEventMatchingLocationRequest.class);
        ArgumentCaptor<UUID> evening = ArgumentCaptor.forClass(UUID.class);
        then(changeMatchingLocation).should()
                .changeMatchingLocation(any(), evening.capture(), request.capture());
        assertThat(request.getValue().locationForMatching())
                .isEqualTo("Lone Tree");
        assertThat(evening.getValue())
                .as("the id comes from the path, so a submit cannot re-target another evening")
                .isEqualTo(privateEventId);
    }

    @Test
    void aBlankLocationIsReportedUnderTheInputRatherThanRedirecting() {
        // The whole reason `required` is not used on these forms: a browser-blocked submit leaves
        // the page exactly as it was, which reads as "my fix changed nothing".
        UUID privateEventId = UUID.randomUUID();
        given(viewProjector.findById(any()))
                .willReturn(Optional.of(viewFor(privateEventId, "Centennial")));
        willThrow(new InvalidMatchingLocation("Location is required"))
                .given(changeMatchingLocation).changeMatchingLocation(any(), any(), any());

        assertThat(mockMvc.post()
                .uri("/planned-private-events/" + privateEventId + "/matching-location")
                .param("locationForMatching", "")
                .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("Location is required")
                // The page has to come back whole, or the error has nothing to sit under.
                .contains("Dinner with the Smiths");
    }

    @Test
    void theBlankErrorRendersInTheFieldsErrorSpan() {
        // A field error the form cannot show is half a fix: assert the element, not the words.
        UUID privateEventId = UUID.randomUUID();
        given(viewProjector.findById(any()))
                .willReturn(Optional.of(viewFor(privateEventId, "Centennial")));
        willThrow(new InvalidMatchingLocation("Location is required"))
                .given(changeMatchingLocation).changeMatchingLocation(any(), any(), any());

        assertThat(mockMvc.post()
                .uri("/planned-private-events/" + privateEventId + "/matching-location")
                .param("locationForMatching", "")
                .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"error\">Location is required</span>");
    }

    @Test
    void postForAnUnknownEveningRedirectsWithoutCallingTheService() {
        given(viewProjector.findById(any())).willReturn(Optional.empty());

        assertThat(mockMvc.post()
                .uri("/planned-private-events/" + UUID.randomUUID() + "/matching-location")
                .param("locationForMatching", "Lone Tree")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/planned-private-events");

        then(changeMatchingLocation).should(never()).changeMatchingLocation(any(), any(), any());
    }

    @Test
    void postForAMalformedIdRedirectsWithoutCallingTheService() {
        // The GET's twin. Same lookup, but the POST is the half that would write: a hand-edited
        // path must not reach the command with an id the projector never resolved.
        assertThat(mockMvc.post().uri("/planned-private-events/not-a-uuid/matching-location")
                .param("locationForMatching", "Lone Tree")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/planned-private-events");

        then(changeMatchingLocation).should(never()).changeMatchingLocation(any(), any(), any());
    }

    @Test
    void anEveningCancelledInAnotherTabRedirectsRatherThanErroring() {
        UUID privateEventId = UUID.randomUUID();
        given(viewProjector.findById(any()))
                .willReturn(Optional.of(viewFor(privateEventId, "Centennial")));
        willThrow(new PrivateEventNotFound("gone"))
                .given(changeMatchingLocation).changeMatchingLocation(any(), any(), any());

        assertThat(mockMvc.post()
                .uri("/planned-private-events/" + privateEventId + "/matching-location")
                .param("locationForMatching", "Lone Tree")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/planned-private-events");
    }

    @Test
    void theFormPostsToItsOwnPath() {
        UUID privateEventId = UUID.randomUUID();
        given(viewProjector.findById(any()))
                .willReturn(Optional.of(viewFor(privateEventId, "Centennial")));

        assertThat(mockMvc.get()
                .uri("/planned-private-events/" + privateEventId + "/matching-location"))
                .hasStatusOk()
                .bodyText()
                .contains("action=\"/planned-private-events/" + privateEventId
                          + "/matching-location\"");
    }
}
