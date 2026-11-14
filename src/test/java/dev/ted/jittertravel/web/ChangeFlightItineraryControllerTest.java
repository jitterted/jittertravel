package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedItinerariesProjector;
import dev.ted.jittertravel.application.BookedItineraryView;
import dev.ted.jittertravel.application.FlightDetailsView;
import dev.ted.jittertravel.application.FlightDetailsViewProjector;
import dev.ted.jittertravel.application.FlightItineraryChange;
import dev.ted.jittertravel.application.ItineraryChangeEvaluation;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.ChangeFlightItineraryCommand;
import dev.ted.jittertravel.domain.ChangeFlightItineraryContext;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.ItineraryChangeKind;
import dev.ted.jittertravel.domain.ItineraryChangePlan;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.domain.ScheduledLegs;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * The change page renders and routes. What each refusal says is {@link ItineraryChangePreviewTest}'s;
 * that the rules hold is {@code FlightItineraryChangeTest}'s and the command's. This proves a message
 * can be seen where it is fixed, which only a real render can.
 */
@Tag("spring")
@WebMvcTest(ChangeFlightItineraryController.class)
@Import(WebTodayTestConfig.class)
@WithMockUser(roles = "OWNER")
class ChangeFlightItineraryControllerTest {

    private static final FlightItineraryId HE80PP = FlightItineraryId.of(UUID.fromString("44444444-4444-4444-4444-444444444444"));
    private static final String PATH = "/booked-itineraries/" + HE80PP.id() + "/change";
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final FlightId BER_FRA = FlightId.of(UUID.randomUUID());
    private static final FlightId FRA_SFO = FlightId.of(UUID.randomUUID());

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    FlightItineraryChange flightItineraryChange;

    @MockitoBean
    BookedItinerariesProjector itinerariesProjector;

    @MockitoBean
    FlightDetailsViewProjector detailsProjector;

    @BeforeEach
    void he80ppIsBooked() {
        given(itinerariesProjector.findLive(HE80PP)).willReturn(Optional.of(
                new BookedItineraryView(HE80PP, "United Airlines", "HE80PP", List.of(BER_FRA, FRA_SFO), false)));
        given(detailsProjector.findById(BER_FRA)).willReturn(Optional.of(new FlightDetailsView(BER_FRA,
                "United Airlines", "UA8786", AirportCode.of("BER"), at(10, 40), AirportCode.of("FRA"), at(11, 50))));
        given(detailsProjector.findById(FRA_SFO)).willReturn(Optional.of(new FlightDetailsView(FRA_SFO,
                "United Airlines", "UA59", AirportCode.of("FRA"), at(13, 30), AirportCode.of("SFO"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 26, 16, 5), ZoneId.of("America/Los_Angeles")))));
    }

    @Test
    void theFormSaysWhichItineraryItIsAndAsksWhoChangedItWithNoDefault() {
        assertThat(mockMvc.get().uri(PATH))
                .hasStatusOk()
                .bodyText()
                .contains("<title>Change Itinerary</title>")
                .contains("<span class=\"code\">HE80PP</span>")
                .contains("<span>United Airlines · 2 legs, Nov 26 to Nov 26</span>")
                .contains("<td>UA8786</td>")
                .contains("<td>BER → FRA</td>")
                .contains("<input type=\"radio\" value=\"SCHEDULE_CHANGE\" id=\"kind1\" name=\"kind\"/>")
                .contains("<input type=\"radio\" value=\"REBOOKING\" id=\"kind2\" name=\"kind\"/>")
                .doesNotContain("checked")
                .doesNotContain("required")
                .doesNotContain("value=\"apply\"");
    }

    @Test
    void anItineraryThatIsNotLiveGoesBackToTheFlightsList() {
        assertThat(mockMvc.get().uri("/booked-itineraries/" + UUID.randomUUID() + "/change"))
                .hasRedirectedUrl("/booked-flights");
        assertThat(mockMvc.get().uri("/booked-itineraries/not-a-uuid/change"))
                .hasRedirectedUrl("/booked-flights");
    }

    /** D4: the Book page's hand-off fills the form and judges nothing, so no error greets Ted. */
    @Test
    void openingWithAnEmailFillsTheBoxAndEvaluatesNothing() {
        assertThat(mockMvc.post().uri(PATH)
                           .param("action", "open")
                           .param("pasted", "Confirmation Number: HE80PP")
                           .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains(">Confirmation Number: HE80PP</textarea>")
                .doesNotContain(ItineraryChangePreview.PICK_WHO);
        then(flightItineraryChange).should(never())
                .evaluate(any(), any(), anyString(), any(), any(), any());
        then(flightItineraryChange).should(never())
                .apply(any(), any(), any(), any(), anyString(), any(), any(), any());
    }

    @Test
    void aPreviewWithoutWhoChangedItShowsTheErrorUnderItsFieldsetAndTheDiff() {
        given(flightItineraryChange.evaluate(eq(HE80PP), isNull(), anyString(), any(), any(), any()))
                .willReturn(dropsBerFra(null));

        assertThat(mockMvc.post().uri(PATH)
                           .param("action", "preview")
                           .param("pasted", "Confirmation Number: HE80PP")
                           .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"error\">Pick who changed it</span>\n        </fieldset>")
                .contains("<div class=\"error error-summary\">1 problem to fix below.</div>")
                .contains("<tr class=\"leg-dropped\">")
                .doesNotContain("value=\"apply\"");
    }

    @Test
    void anEmailForAnotherCodeIsReportedUnderTheBox() {
        given(flightItineraryChange.evaluate(eq(HE80PP), eq(ItineraryChangeKind.REBOOKING), anyString(), any(), any(), any()))
                .willReturn(new ItineraryChangeEvaluation(List.of("This email is for MD7LKB, not HE80PP"), List.of(),
                        ItineraryChangeKind.REBOOKING, null, null, null));

        assertThat(mockMvc.post().uri(PATH)
                           .param("action", "preview")
                           .param("kind", "REBOOKING")
                           .param("pasted", "Confirmation Number: MD7LKB")
                           .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"error\">This email is for MD7LKB, not HE80PP</span>")
                .contains("<input type=\"radio\" value=\"REBOOKING\" id=\"kind2\" name=\"kind\" checked=\"checked\"/>");
    }

    @Test
    void aCleanRebookingPreviewNamesWhatItRecordsOnItsButton() {
        given(flightItineraryChange.evaluate(eq(HE80PP), eq(ItineraryChangeKind.REBOOKING), anyString(), any(), any(), any()))
                .willReturn(dropsBerFra(ItineraryChangeKind.REBOOKING));

        assertThat(mockMvc.post().uri(PATH)
                           .param("action", "preview")
                           .param("kind", "REBOOKING")
                           .param("pasted", "Confirmation Number: HE80PP")
                           .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"leg-status\">Dropped</span>")
                .contains("Recorded as your rebooking: 1 leg dropped, 1 unchanged.")
                .contains("<button type=\"submit\" class=\"primary\" name=\"action\" value=\"apply\">Record rebooking</button>")
                .doesNotContain("<div class=\"error error-summary\">");
    }

    @Test
    void applyingGoesToTheFlightsListCarryingTheOutcomeKeyNotTheSentence() {
        given(flightItineraryChange.apply(any(), any(), eq(HE80PP), eq(ItineraryChangeKind.REBOOKING), anyString(),
                any(), any(), eq(WebTodayTestConfig.FIXED_INSTANT)))
                .willReturn(dropsBerFra(ItineraryChangeKind.REBOOKING));

        assertThat(mockMvc.post().uri(PATH)
                           .param("action", "apply")
                           .param("kind", "REBOOKING")
                           .param("pasted", "Confirmation Number: HE80PP")
                           .with(csrf()))
                .hasRedirectedUrl("/booked-flights?changed=" + HE80PP.id() + "&how=rebooked");
    }

    @Test
    void applyingWithoutWhoChangedItStaysAndSaysSo() {
        given(flightItineraryChange.apply(any(), any(), eq(HE80PP), isNull(), anyString(), any(), any(), any()))
                .willReturn(dropsBerFra(null));

        assertThat(mockMvc.post().uri(PATH)
                           .param("action", "apply")
                           .param("pasted", "Confirmation Number: HE80PP")
                           .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"error\">Pick who changed it</span>");
    }

    @Test
    void aValueNoRadioSendsIsTreatedAsNoPick() {
        given(flightItineraryChange.evaluate(eq(HE80PP), isNull(), anyString(), any(), any(), any()))
                .willReturn(dropsBerFra(null));

        assertThat(mockMvc.post().uri(PATH)
                           .param("action", "preview")
                           .param("kind", "AIRLINE")
                           .param("pasted", "Confirmation Number: HE80PP")
                           .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"error\">Pick who changed it</span>");
    }

    @Test
    void readOnlyModeSendsTheReaderToThePageThatExplainsIt() {
        given(flightItineraryChange.apply(any(), any(), any(), any(), anyString(), any(), any(), any()))
                .willThrow(new ReadOnlyModeException("read-only"));

        assertThat(mockMvc.post().uri(PATH)
                           .param("action", "apply")
                           .param("kind", "REBOOKING")
                           .param("pasted", "x")
                           .with(csrf()))
                .hasRedirectedUrl("/read-only");
    }

    @Test
    void readOnlyModeSendsTheFormPageToThePageThatExplainsIt() {
        given(flightItineraryChange.isReadOnly()).willReturn(true);

        assertThat(mockMvc.get().uri(PATH))
                .hasRedirectedUrl("/read-only");
        then(flightItineraryChange).should(never())
                .evaluate(any(), any(), anyString(), any(), any(), any());
    }

    /** HE80PP's real rebooking: BER->FRA goes, FRA->SFO stays. */
    private static ItineraryChangeEvaluation dropsBerFra(ItineraryChangeKind kind) {
        ItineraryLeg berFra = new ItineraryLeg(BER_FRA, "United Airlines", "UA8786",
                AirportCode.of("BER"), at(10, 40), AirportCode.of("FRA"), at(11, 50));
        ItineraryLeg fraSfo = new ItineraryLeg(FRA_SFO, "United Airlines", "UA59",
                AirportCode.of("FRA"), at(13, 30), AirportCode.of("SFO"), at(22, 5));
        ItineraryLeg pasted = new ItineraryLeg(FlightId.of(UUID.randomUUID()), "United Airlines", "UA59",
                AirportCode.of("FRA"), at(13, 30), AirportCode.of("SFO"), at(22, 5));
        ChangeFlightItineraryContext context = new ChangeFlightItineraryContext(true, List.of(berFra, fraSfo),
                List.of(), ScheduledLegs.none(), Instant.parse("2026-11-14T15:00:00Z"));
        ChangeFlightItineraryCommand command = kind == null ? null
                : new ChangeFlightItineraryCommand(HE80PP, kind, List.of(pasted));
        return new ItineraryChangeEvaluation(List.of(), List.of(), kind,
                ItineraryChangePlan.of(List.of(pasted), context), context, command);
    }

    private static ZonedTimestamp at(int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 26, hour, minute), BERLIN);
    }
}
