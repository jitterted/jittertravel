package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelTrain;
import dev.ted.jittertravel.application.TrainDetailsView;
import dev.ted.jittertravel.application.TrainDetailsViewProjector;
import dev.ted.jittertravel.domain.TrainNotFound;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(CancelTrainController.class)
@WithMockUser(roles = "OWNER")
class CancelTrainControllerTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    CancelTrain cancelTrain;

    @MockitoBean
    TrainDetailsViewProjector detailsProjector;

    @Test
    void confirmationPageNamesBothEndsOfTheTrip() {
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId)))
                .willReturn(Optional.of(viewFor(tripId)));

        assertThat(mockMvc.get().uri("/booked-trains/{id}/cancel", tripId))
                .hasStatusOk()
                .bodyText()
                .contains("Hamburg")
                .contains("Berlin")
                .contains("ICE 597")
                .contains("Cancel this trip");
    }

    @Test
    void confirmationPagePostsBackToItsOwnCancelPath() {
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId)))
                .willReturn(Optional.of(viewFor(tripId)));

        assertThat(mockMvc.get().uri("/booked-trains/{id}/cancel", tripId))
                .hasStatusOk()
                .bodyText()
                .contains("action=\"/booked-trains/" + tripId + "/cancel\"");
    }

    @Test
    void aTripWithNoServiceIdRendersNoServiceLine() {
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId)))
                .willReturn(Optional.of(viewFor(tripId, "")));

        assertThat(mockMvc.get().uri("/booked-trains/{id}/cancel", tripId))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("ICE 597");
    }

    @Test
    void confirmingCancelsTheTripAndReturnsToTheList() {
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId)))
                .willReturn(Optional.of(viewFor(tripId)));

        assertThat(mockMvc.post().uri("/booked-trains/{id}/cancel", tripId)
                           .param("reason", "Rebooked for the 17th")
                           .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");

        then(cancelTrain).should()
                .cancelTrain(any(UUID.class),
                        eq(new CancelTrainRequest(tripId, "Rebooked for the 17th")));
    }

    @Test
    void anOmittedReasonIsRecordedAsEmptyRatherThanNull() {
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId)))
                .willReturn(Optional.of(viewFor(tripId)));

        assertThat(mockMvc.post().uri("/booked-trains/{id}/cancel", tripId).with(csrf()))
                .hasStatus3xxRedirection();

        then(cancelTrain).should()
                .cancelTrain(any(UUID.class), eq(new CancelTrainRequest(tripId, "")));
    }

    @Test
    void aStaleLinkForAnAlreadyCancelledTripNavigatesToTheListWritingNothing() {
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId))).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/booked-trains/{id}/cancel", tripId))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");

        assertThat(mockMvc.post().uri("/booked-trains/{id}/cancel", tripId).with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");

        then(cancelTrain).shouldHaveNoInteractions();
    }

    @Test
    void aMalformedIdNavigatesToTheListRatherThanFailing() {
        assertThat(mockMvc.get().uri("/booked-trains/not-a-uuid/cancel"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");

        then(cancelTrain).shouldHaveNoInteractions();
    }

    @Test
    void cancelledInAnotherTabBetweenLookupAndWriteIsNotAnError() {
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId)))
                .willReturn(Optional.of(viewFor(tripId)));
        willThrow(new TrainNotFound("gone"))
                .given(cancelTrain).cancelTrain(any(UUID.class), any(CancelTrainRequest.class));

        assertThat(mockMvc.post().uri("/booked-trains/{id}/cancel", tripId).with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");
    }

    // -------------------------------------------------------------------------
    // Returning to the report a fix was launched from (Ted, 2026-09-06)
    // -------------------------------------------------------------------------

    @Test
    void cancellingFromTheProblemListReturnsToTheProblemList() {
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId)))
                .willReturn(Optional.of(viewFor(tripId)));

        assertThat(mockMvc.post().uri("/booked-trains/{id}/cancel", tripId)
                           .param("from", "list")
                           .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/schedule-problems?view=list");
    }

    @Test
    void cancellingFromTheProblemCalendarReturnsToTheProblemCalendar() {
        // The view matters: landing in the list after clicking a calendar band reads as the app
        // losing your place, which is the whole reason FixOrigin distinguishes them.
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId)))
                .willReturn(Optional.of(viewFor(tripId)));

        assertThat(mockMvc.post().uri("/booked-trains/{id}/cancel", tripId)
                           .param("from", "calendar")
                           .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/schedule-problems?view=calendar");
    }

    @Test
    void cancellingWithoutAnOriginStillLandsOnTheTrainsList() {
        // Absent is not "calendar": an ordinary cancel from /booked-trains must not be sent to a
        // report it never came from.
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId)))
                .willReturn(Optional.of(viewFor(tripId)));

        assertThat(mockMvc.post().uri("/booked-trains/{id}/cancel", tripId).with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");
    }

    @Test
    void aHandEditedOriginCannotRedirectOffTheApp() {
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId)))
                .willReturn(Optional.of(viewFor(tripId)));

        assertThat(mockMvc.post().uri("/booked-trains/{id}/cancel", tripId)
                           .param("from", "https://evil.example.com")
                           .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/schedule-problems?view=calendar");
    }

    @Test
    void aStaleLinkIgnoresTheOriginBecauseNothingWasFixed() {
        // Only the success path returns to the report. A miss has fixed nothing, so sending Ted
        // back to the report would claim it had.
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId))).willReturn(Optional.empty());

        assertThat(mockMvc.post().uri("/booked-trains/{id}/cancel", tripId)
                           .param("from", "list")
                           .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");
    }

    @Test
    void theConfirmationFormCarriesTheOriginThroughThePost() {
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId)))
                .willReturn(Optional.of(viewFor(tripId)));

        assertThat(mockMvc.get().uri("/booked-trains/{id}/cancel?from=list", tripId))
                .hasStatusOk()
                .bodyText()
                .contains("<input type=\"hidden\" name=\"from\" value=\"list\"");
    }

    @Test
    void anOrdinaryVisitRendersNoOriginInput() {
        UUID tripId = UUID.randomUUID();
        given(detailsProjector.findById(TrainTripId.of(tripId)))
                .willReturn(Optional.of(viewFor(tripId)));

        assertThat(mockMvc.get().uri("/booked-trains/{id}/cancel", tripId))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("name=\"from\"");
    }

    private static TrainDetailsView viewFor(UUID tripId) {
        return viewFor(tripId, "ICE 597");
    }

    private static TrainDetailsView viewFor(UUID tripId, String serviceId) {
        return new TrainDetailsView(
                TrainTripId.of(tripId),
                new TrainStationAddress("Hamburg Hbf", "Hamburg", "Germany", ""),
                at(9, 0),
                new TrainStationAddress("Berlin Hbf", "Berlin", "Germany", ""),
                at(11, 0),
                serviceId);
    }

    private static ZonedTimestamp at(int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, hour, minute), BERLIN);
    }
}
