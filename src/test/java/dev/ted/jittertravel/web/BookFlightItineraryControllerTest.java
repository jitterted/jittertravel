package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.FlightItineraryBooking;
import dev.ted.jittertravel.application.ItineraryEvaluation;
import dev.ted.jittertravel.application.PastedItinerary;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.BookFlightItineraryCommand;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@Tag("spring")
@WebMvcTest(BookFlightItineraryController.class)
@Import(WebTodayTestConfig.class)
@WithMockUser(roles = "OWNER")
class BookFlightItineraryControllerTest {

    private static final String ITINERARY_ID = "99999999-9999-9999-9999-999999999999";
    private static final PastedItinerary.Leg LEG = new PastedItinerary.Leg(1, "United Airlines", "UA2091",
            AirportCode.of("SFO"), LocalDateTime.of(2026, 10, 18, 6, 10),
            AirportCode.of("ORD"), LocalDateTime.of(2026, 10, 18, 12, 45));

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    FlightItineraryBooking itineraryBooking;

    @Test
    void theFormOffersABoxAndAPreviewButNoBookingYet() {
        assertThat(mockMvc.get().uri("/book-flight/itinerary"))
                .hasStatusOk()
                .bodyText()
                .contains("<textarea class=\"paste-box\" id=\"pasted\" name=\"pasted\">")
                .contains("<button type=\"submit\" name=\"action\" value=\"preview\">Preview</button>")
                .doesNotContain("value=\"book\"")
                .as("the pasted box is never marked required: the server reports, not a browser bubble")
                .doesNotContain("required");
    }

    @Test
    void aCleanPreviewListsTheLegsAndOffersToBookThemAll() {
        given(itineraryBooking.evaluate(anyString(), any(), any(), any(), any()))
                .willReturn(clean());

        assertThat(preview())
                .hasStatusOk()
                .bodyText()
                .contains("<td>UA2091</td>")
                .contains("<td>SFO→ORD</td>")
                .contains("Sun, Oct 18, 6:10 AM")
                .contains("<strong>MD7LKB</strong>")
                .contains("<button type=\"submit\" name=\"action\" value=\"book\">Book 1 flights</button>")
                .as("the paste is kept in the box, so booking books what was previewed")
                .contains("Confirmation Number: MD7LKB</textarea>");
    }

    @Test
    void anUnreadablePasteIsReportedUnderTheBox() {
        given(itineraryBooking.evaluate(anyString(), any(), any(), any(), any()))
                .willReturn(new ItineraryEvaluation(List.of("No confirmation number found"),
                        "", List.of(), List.of(), null));

        assertThat(preview())
                .bodyText()
                .contains("<div class=\"error error-summary\">1 problem to fix below.</div>")
                .contains("<span class=\"error\">No confirmation number found</span>")
                .doesNotContain("value=\"book\"");
    }

    @Test
    void anUnknownAirportGetsAZonePickerWithItsOwnError() {
        given(itineraryBooking.evaluate(anyString(), any(), any(), any(), any()))
                .willReturn(new ItineraryEvaluation(List.of(), "MD7LKB",
                        List.of(new ItineraryEvaluation.Leg(LEG, null)), List.of(AirportCode.of("YQB")), null));

        assertThat(preview())
                .bodyText()
                .contains("<select name=\"airportZones[YQB]\">")
                .contains("<option value=\"CANADA_EASTERN\">Canada Eastern</option>")
                .contains("<span class=\"error\">Unknown airport — pick its zone</span>");
    }

    @Test
    void aPickedZoneReachesTheServiceKeyedByItsAirport() {
        given(itineraryBooking.evaluate(anyString(), any(), any(), any(), any()))
                .willReturn(clean());

        assertThat(mockMvc.post().uri("/book-flight/itinerary")
                           .param("itineraryId", ITINERARY_ID)
                           .param("pasted", "Confirmation Number: MD7LKB")
                           .param("airportZones[YQB]", "CANADA_EASTERN")
                           .param("action", "preview")
                           .with(csrf()))
                .hasStatusOk();

        then(itineraryBooking).should()
                .evaluate(eq("Confirmation Number: MD7LKB"), eq(Map.of("YQB", "CANADA_EASTERN")),
                        eq(FlightItineraryId.of(UUID.fromString(ITINERARY_ID))), any(),
                        eq(WebTodayTestConfig.FIXED_INSTANT));
    }

    @Test
    void aPickedZoneIsCarriedThroughToTheBookButtonOnceItsPickerIsGone() {
        given(itineraryBooking.evaluate(anyString(), any(), any(), any(), any()))
                .willReturn(clean());

        assertThat(previewWithPick("YQB", "CANADA_EASTERN"))
                .bodyText()
                .as("the picker is gone because the airport resolved, so the pick rides along hidden")
                .contains("<input type=\"hidden\" name=\"airportZones[YQB]\" value=\"CANADA_EASTERN\"")
                .doesNotContain("<select name=\"airportZones[YQB]\">")
                .contains("value=\"book\"");
    }

    @Test
    void anAirportStillNeedingAZoneShowsItsPickerAndNoHiddenCopyOfAnEarlierPick() {
        given(itineraryBooking.evaluate(anyString(), any(), any(), any(), any()))
                .willReturn(new ItineraryEvaluation(List.of(), "MD7LKB",
                        List.of(new ItineraryEvaluation.Leg(LEG, null)), List.of(AirportCode.of("YQB")), null));

        assertThat(previewWithPick("YQB", "NOT_A_ZONE"))
                .bodyText()
                .contains("<select name=\"airportZones[YQB]\">")
                .doesNotContain("<input type=\"hidden\" name=\"airportZones[YQB]\"");
    }

    @Test
    void aBlankPickIsNotCarriedAsAHiddenInput() {
        given(itineraryBooking.evaluate(anyString(), any(), any(), any(), any()))
                .willReturn(clean());

        assertThat(previewWithPick("YQB", ""))
                .bodyText()
                .doesNotContain("name=\"airportZones[YQB]\"");
    }

    @Test
    void aRefusedLegIsMarkedOnItsOwnRow() {
        given(itineraryBooking.evaluate(anyString(), any(), any(), any(), any()))
                .willReturn(new ItineraryEvaluation(List.of(), "MD7LKB",
                        List.of(new ItineraryEvaluation.Leg(LEG, new DepartureNotInFuture("x"))), List.of(), null));

        assertThat(preview())
                .bodyText()
                .contains("<tr class=\"refused\">")
                .contains("<span class=\"error\">Already departed</span>")
                .doesNotContain("value=\"book\"");
    }

    @Test
    void bookingACleanPasteGoesToTheFlightsList() {
        given(itineraryBooking.book(any(), anyString(), any(), any(), any(), any()))
                .willReturn(clean());

        assertThat(mockMvc.post().uri("/book-flight/itinerary")
                           .param("itineraryId", ITINERARY_ID)
                           .param("pasted", "Confirmation Number: MD7LKB")
                           .param("action", "book")
                           .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");
    }

    @Test
    void submittingAFormThatWasAlreadyBookedGoesToTheFlightsListLikeTheFirstSubmitDid() {
        given(itineraryBooking.book(any(), anyString(), any(), any(), any(), any()))
                .willReturn(new ItineraryEvaluation(List.of(), "", List.of(), List.of(), null, true, null));

        assertThat(mockMvc.post().uri("/book-flight/itinerary")
                           .param("itineraryId", ITINERARY_ID)
                           .param("pasted", "Confirmation Number: MD7LKB")
                           .param("action", "book")
                           .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");
    }

    @Test
    void bookingAPasteThatNoLongerEvaluatesCleanShowsWhyAndStays() {
        given(itineraryBooking.book(any(), anyString(), any(), any(), any(), any()))
                .willReturn(new ItineraryEvaluation(List.of(), "MD7LKB",
                        List.of(new ItineraryEvaluation.Leg(LEG, new DepartureNotInFuture("x"))), List.of(), null));

        assertThat(mockMvc.post().uri("/book-flight/itinerary")
                           .param("itineraryId", ITINERARY_ID)
                           .param("pasted", "Confirmation Number: MD7LKB")
                           .param("action", "book")
                           .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"error\">Already departed</span>");
    }

    @Test
    void aHandEditedItineraryIdIsReplacedWithAFreshOneRatherThanFailing() {
        given(itineraryBooking.evaluate(anyString(), any(), any(), any(), any()))
                .willReturn(clean());

        assertThat(mockMvc.post().uri("/book-flight/itinerary")
                           .param("itineraryId", "not-a-uuid")
                           .param("pasted", "Confirmation Number: MD7LKB")
                           .param("action", "preview")
                           .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("not-a-uuid");
        then(itineraryBooking).should()
                .evaluate(anyString(), any(), argThat(id -> id != null), any(), any());
    }

    @Test
    void aMissingItineraryIdIsReplacedWithAFreshOneRatherThanFailing() {
        given(itineraryBooking.evaluate(anyString(), any(), any(), any(), any()))
                .willReturn(clean());

        assertThat(mockMvc.post().uri("/book-flight/itinerary")
                           .param("pasted", "Confirmation Number: MD7LKB")
                           .param("action", "preview")
                           .with(csrf()))
                .hasStatusOk();
    }

    @Test
    void theFormPageMintsTheItineraryIdItWillCarry() {
        assertThat(mockMvc.get().uri("/book-flight/itinerary"))
                .hasStatusOk()
                .bodyText()
                .containsPattern("<input type=\"hidden\" id=\"itineraryId\" name=\"itineraryId\" value=\"[0-9a-f-]{36}\"");
    }

    @Test
    void readOnlyModeSendsTheFormPageToThePageThatExplainsIt() {
        given(itineraryBooking.isReadOnly()).willReturn(true);

        assertThat(mockMvc.get().uri("/book-flight/itinerary"))
                .hasRedirectedUrl("/read-only");
    }

    @Test
    void readOnlyModeSendsAPreviewToThePageThatExplainsIt() {
        given(itineraryBooking.isReadOnly()).willReturn(true);

        assertThat(preview())
                .hasRedirectedUrl("/read-only");
        then(itineraryBooking).should(never())
                .evaluate(anyString(), any(), any(), any(), any());
    }

    @Test
    void readOnlyModeSendsTheReaderToThePageThatExplainsIt() {
        given(itineraryBooking.book(any(), anyString(), any(), any(), any(), any()))
                .willThrow(new ReadOnlyModeException("read-only"));

        assertThat(mockMvc.post().uri("/book-flight/itinerary")
                           .param("itineraryId", ITINERARY_ID)
                           .param("pasted", "x")
                           .param("action", "book")
                           .with(csrf()))
                .hasRedirectedUrl("/read-only");
    }

    private MvcTestResult preview() {
        return mockMvc.post().uri("/book-flight/itinerary")
                .param("itineraryId", ITINERARY_ID)
                .param("pasted", "Confirmation Number: MD7LKB")
                .param("action", "preview")
                .with(csrf())
                .exchange();
    }

    /** D4, D11: Book never changes an itinerary; it hands the email to that itinerary's change page. */
    @Test
    void aCodeAlreadyBookedIsRefusedAndOffersToCarryTheEmailToItsChangePage() {
        given(itineraryBooking.evaluate(anyString(), any(), any(), any(), any()))
                .willReturn(onTheBooks());

        assertThat(preview())
                .hasStatusOk()
                .bodyText()
                .contains("<p>MD7LKB is already booked: 2 legs, Oct 18 to Oct 20. To record a change to it, "
                          + "open the itinerary instead. The email you pasted comes with you.</p>")
                .contains("<button type=\"submit\" class=\"cta\" form=\"handoff\">Change itinerary MD7LKB</button>")
                .contains("<form id=\"handoff\" method=\"post\" action=\"/booked-itineraries/" + KNOWN.id() + "/change\">")
                .contains("<input type=\"hidden\" name=\"action\" value=\"open\"/>")
                .contains("<input type=\"hidden\" name=\"pasted\" value=\"Confirmation Number: MD7LKB\"/>")
                .doesNotContain("value=\"book\"")
                .doesNotContain("schedule change");
    }

    @Test
    void bookingACodeAlreadyBookedStaysOnThePageAndChangesNothing() {
        given(itineraryBooking.book(any(), anyString(), any(), any(), any(), any()))
                .willReturn(onTheBooks());

        assertThat(mockMvc.post().uri("/book-flight/itinerary")
                           .param("itineraryId", ITINERARY_ID)
                           .param("pasted", "Confirmation Number: MD7LKB")
                           .param("action", "book")
                           .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<button type=\"submit\" class=\"cta\" form=\"handoff\">Change itinerary MD7LKB</button>");
    }

    private static final FlightItineraryId KNOWN = FlightItineraryId.of(UUID.fromString("33333333-3333-3333-3333-333333333333"));

    private static ItineraryEvaluation onTheBooks() {
        ZoneId pacific = ZoneId.of("America/Los_Angeles");
        return new ItineraryEvaluation(List.of(), "MD7LKB", List.of(), List.of(), null, false,
                new ItineraryEvaluation.OnTheBooks(KNOWN, 2,
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 18, 6, 10), pacific),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 20, 17, 0), pacific)));
    }

    private MvcTestResult previewWithPick(String airport, String zone) {
        return mockMvc.post().uri("/book-flight/itinerary")
                .param("itineraryId", ITINERARY_ID)
                .param("pasted", "Confirmation Number: MD7LKB")
                .param("airportZones[" + airport + "]", zone)
                .param("action", "preview")
                .with(csrf())
                .exchange();
    }

    private static ItineraryEvaluation clean() {
        ZoneId pacific = ZoneId.of("America/Los_Angeles");
        BookFlightItineraryCommand command = new BookFlightItineraryCommand(
                FlightItineraryId.of(UUID.fromString(ITINERARY_ID)), "United Airlines", "MD7LKB",
                List.of(new ItineraryLeg(FlightId.of(UUID.randomUUID()), "United Airlines", "UA2091",
                        AirportCode.of("SFO"), ZonedTimestamp.fromLocal(LEG.departureLocal(), pacific),
                        AirportCode.of("ORD"), ZonedTimestamp.fromLocal(LEG.arrivalLocal(), ZoneId.of("America/Chicago")))));
        return new ItineraryEvaluation(List.of(), "MD7LKB",
                List.of(new ItineraryEvaluation.Leg(LEG, null)), List.of(), command);
    }
}
