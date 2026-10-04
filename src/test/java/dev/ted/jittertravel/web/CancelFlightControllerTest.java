package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelFlight;
import dev.ted.jittertravel.application.FlightDetailsView;
import dev.ted.jittertravel.application.FlightDetailsViewProjector;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightNotFound;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
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

@Tag("spring")
@WebMvcTest(CancelFlightController.class)
@Import(WebTodayTestConfig.class)
@WithMockUser(roles = "OWNER")
class CancelFlightControllerTest {

    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    CancelFlight cancelFlight;

    @MockitoBean
    FlightDetailsViewProjector detailsProjector;

    @Test
    void confirmationPageIdentifiesTheFlight() {
        UUID flightId = UUID.randomUUID();
        given(detailsProjector.findById(FlightId.of(flightId)))
                .willReturn(Optional.of(viewFor(flightId)));

        assertThat(mockMvc.get().uri("/booked-flights/{id}/cancel", flightId))
                .hasStatusOk()
                .bodyText()
                .contains("<span>SFO</span>")
                .contains("<span>ORD</span>")
                .contains("<span>United Airlines</span>")
                .contains("<span>UA2091</span>")
                .contains("<span>Sun, Oct 18, 2026, 6:10 AM</span>");
    }

    @Test
    void confirmationPagePostsBackToItsOwnCancelPath() {
        UUID flightId = UUID.randomUUID();
        given(detailsProjector.findById(FlightId.of(flightId)))
                .willReturn(Optional.of(viewFor(flightId)));

        assertThat(mockMvc.get().uri("/booked-flights/{id}/cancel", flightId))
                .hasStatusOk()
                .bodyText()
                .contains("action=\"/booked-flights/" + flightId + "/cancel\"");
    }

    @Test
    void confirmingCancelsTheFlightAndReturnsToTheList() {
        UUID flightId = UUID.randomUUID();
        given(detailsProjector.findById(FlightId.of(flightId)))
                .willReturn(Optional.of(viewFor(flightId)));

        assertThat(mockMvc.post().uri("/booked-flights/{id}/cancel", flightId)
                           .param("reason", "Rebooked on UA58")
                           .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");

        then(cancelFlight).should()
                .cancelFlight(any(UUID.class),
                        eq(new CancelFlightRequest(flightId, "Rebooked on UA58")),
                        eq(WebTodayTestConfig.FIXED_INSTANT));
    }

    @Test
    void anOmittedReasonIsRecordedAsEmptyRatherThanNull() {
        UUID flightId = UUID.randomUUID();
        given(detailsProjector.findById(FlightId.of(flightId)))
                .willReturn(Optional.of(viewFor(flightId)));

        assertThat(mockMvc.post().uri("/booked-flights/{id}/cancel", flightId).with(csrf()))
                .hasStatus3xxRedirection();

        then(cancelFlight).should()
                .cancelFlight(any(UUID.class), eq(new CancelFlightRequest(flightId, "")), any(Instant.class));
    }

    @Test
    void aStaleLinkForAnAlreadyCancelledFlightNavigatesToTheListWritingNothing() {
        UUID flightId = UUID.randomUUID();
        given(detailsProjector.findById(FlightId.of(flightId))).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/booked-flights/{id}/cancel", flightId))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");

        assertThat(mockMvc.post().uri("/booked-flights/{id}/cancel", flightId).with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");

        then(cancelFlight).shouldHaveNoInteractions();
    }

    @Test
    void aMalformedIdNavigatesToTheListRatherThanFailing() {
        assertThat(mockMvc.get().uri("/booked-flights/not-a-uuid/cancel"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");

        then(cancelFlight).shouldHaveNoInteractions();
    }

    @Test
    void cancelledInAnotherTabBetweenLookupAndWriteIsNotAnError() {
        UUID flightId = UUID.randomUUID();
        given(detailsProjector.findById(FlightId.of(flightId)))
                .willReturn(Optional.of(viewFor(flightId)));
        willThrow(new FlightNotFound("gone"))
                .given(cancelFlight).cancelFlight(any(UUID.class), any(CancelFlightRequest.class), any(Instant.class));

        assertThat(mockMvc.post().uri("/booked-flights/{id}/cancel", flightId).with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");
    }

    @Test
    void readOnlyModeSendsTheReaderToThePageThatExplainsIt() {
        UUID flightId = UUID.randomUUID();
        given(detailsProjector.findById(FlightId.of(flightId)))
                .willReturn(Optional.of(viewFor(flightId)));
        willThrow(new ReadOnlyModeException("read-only"))
                .given(cancelFlight).cancelFlight(any(UUID.class), any(CancelFlightRequest.class), any(Instant.class));

        assertThat(mockMvc.post().uri("/booked-flights/{id}/cancel", flightId).with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/read-only");
    }

    /**
     * Red because nothing puts the flight back from inside the app; no typed word because appending
     * a cancellation destroys nothing.
     */
    @Test
    void theConfirmationIsRedAndTakesNoTypedWord() {
        UUID flightId = UUID.randomUUID();
        given(detailsProjector.findById(FlightId.of(flightId)))
                .willReturn(Optional.of(viewFor(flightId)));

        assertThat(mockMvc.get().uri("/booked-flights/{id}/cancel", flightId))
                .hasStatusOk()
                .bodyText()
                .contains("<button type=\"submit\" class=\"danger\">Cancel this flight</button>")
                .contains("background: #b00;")
                .doesNotContain("placeholder=\"CANCEL\"");
    }

    // -------------------------------------------------------------------------
    // Returning to the report a fix was launched from, as Cancel Train does
    // -------------------------------------------------------------------------

    @Test
    void cancellingFromTheProblemListReturnsToTheProblemList() {
        UUID flightId = UUID.randomUUID();
        given(detailsProjector.findById(FlightId.of(flightId)))
                .willReturn(Optional.of(viewFor(flightId)));

        assertThat(mockMvc.post().uri("/booked-flights/{id}/cancel", flightId)
                           .param("from", "list")
                           .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/schedule-problems?view=list");
    }

    @Test
    void aHandEditedOriginCannotRedirectOffTheApp() {
        UUID flightId = UUID.randomUUID();
        given(detailsProjector.findById(FlightId.of(flightId)))
                .willReturn(Optional.of(viewFor(flightId)));

        assertThat(mockMvc.post().uri("/booked-flights/{id}/cancel", flightId)
                           .param("from", "https://evil.example.com")
                           .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/schedule-problems?view=calendar");
    }

    @Test
    void aStaleLinkIgnoresTheOriginBecauseNothingWasFixed() {
        UUID flightId = UUID.randomUUID();
        given(detailsProjector.findById(FlightId.of(flightId))).willReturn(Optional.empty());

        assertThat(mockMvc.post().uri("/booked-flights/{id}/cancel", flightId)
                           .param("from", "list")
                           .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");
    }

    @Test
    void theConfirmationFormCarriesTheOriginThroughThePost() {
        UUID flightId = UUID.randomUUID();
        given(detailsProjector.findById(FlightId.of(flightId)))
                .willReturn(Optional.of(viewFor(flightId)));

        assertThat(mockMvc.get().uri("/booked-flights/{id}/cancel?from=list", flightId))
                .hasStatusOk()
                .bodyText()
                .contains("<input type=\"hidden\" name=\"from\" value=\"list\"");
    }

    @Test
    void anOrdinaryVisitRendersNoOriginInput() {
        UUID flightId = UUID.randomUUID();
        given(detailsProjector.findById(FlightId.of(flightId)))
                .willReturn(Optional.of(viewFor(flightId)));

        assertThat(mockMvc.get().uri("/booked-flights/{id}/cancel", flightId))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("name=\"from\"");
    }

    private static FlightDetailsView viewFor(UUID flightId) {
        return new FlightDetailsView(
                FlightId.of(flightId),
                "United Airlines",
                "UA2091",
                AirportCode.of("SFO"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, 6, 10), LOS_ANGELES),
                AirportCode.of("ORD"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, 12, 45), ZoneId.of("America/Chicago")));
    }
}
