package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightView;
import dev.ted.jittertravel.application.BookedItinerariesProjector;
import dev.ted.jittertravel.application.BookedItineraryView;
import dev.ted.jittertravel.application.CancelFlightItinerary;
import dev.ted.jittertravel.application.FlightDetailsView;
import dev.ted.jittertravel.application.FlightDetailsViewProjector;
import dev.ted.jittertravel.application.FlightTrips;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.application.StayingFlight;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryHasDeparted;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.FlightItineraryNotFound;
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
import java.util.List;
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
@WebMvcTest(CancelFlightItineraryController.class)
@Import(WebTodayTestConfig.class)
@WithMockUser(roles = "OWNER")
class CancelFlightItineraryControllerTest {

    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    CancelFlightItinerary cancelFlightItinerary;

    @MockitoBean
    BookedItinerariesProjector itinerariesProjector;

    @MockitoBean
    FlightDetailsViewProjector detailsProjector;

    @MockitoBean
    FlightTrips flightTrips;

    private final UUID itineraryId = UUID.randomUUID();
    private final FlightId out = FlightId.random();
    private final FlightId back = FlightId.random();

    @Test
    void confirmationPageNamesTheItineraryAndItsCodeAndEveryLegItWouldCancel() {
        givenALiveItinerary();

        assertThat(mockMvc.get().uri("/booked-itineraries/{id}/cancel", itineraryId))
                .hasStatusOk()
                .bodyText()
                .contains("<span>United Airlines</span>")
                .contains("<span>MD7LKB</span>")
                .contains("2 flights will be cancelled")
                .contains("<span>UA2091</span>")
                .contains("<span>UA2092</span>");
    }

    @Test
    void legsAreListedInDepartureOrderWhateverOrderTheItineraryNamesThem() {
        givenALiveItinerary();

        assertThat(mockMvc.get().uri("/booked-itineraries/{id}/cancel", itineraryId))
                .hasStatusOk()
                .bodyText()
                .as("Outbound flight comes before the return")
                .containsSubsequence("<span>UA2091</span>", "<span>UA2092</span>");
    }

    @Test
    void flightsThatStayBookedAreListedWithTheirOwnTripsCode() {
        givenALiveItinerary();
        given(flightTrips.staysBooked(any(BookedItineraryView.class), eq(WebTodayTestConfig.FIXED_INSTANT)))
                .willReturn(List.of(
                        new StayingFlight(bookedView("YOW→YYZ", 20), "QX4TZN"),
                        new StayingFlight(bookedView("YYZ→YOW", 22), "")));

        assertThat(mockMvc.get().uri("/booked-itineraries/{id}/cancel", itineraryId))
                .hasStatusOk()
                .bodyText()
                .contains("<strong>Stays booked.</strong>")
                .contains("2 other flights fall between these and are not part of this itinerary:")
                .contains("<span>YOW→YYZ</span>")
                .contains("<span>Tue, Oct 20, 2026, 6:10 AM</span>")
                .contains("<span class=\"stay-code\">QX4TZN</span>")
                .contains("<span>YYZ→YOW</span>");
    }

    @Test
    void oneStayingFlightIsSaidInTheSingularAndAHandEnteredOneCarriesNoCode() {
        givenALiveItinerary();
        given(flightTrips.staysBooked(any(BookedItineraryView.class), any(Instant.class)))
                .willReturn(List.of(new StayingFlight(bookedView("ORD→DEN", 20), "")));

        assertThat(mockMvc.get().uri("/booked-itineraries/{id}/cancel", itineraryId))
                .hasStatusOk()
                .bodyText()
                .contains("1 other flight falls between these and is not part of this itinerary:")
                .doesNotContain("class=\"stay-code\"");
    }

    @Test
    void whenNothingStaysTheBlockIsNotThere() {
        givenALiveItinerary();

        assertThat(mockMvc.get().uri("/booked-itineraries/{id}/cancel", itineraryId))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("Stays booked.");
    }

    @Test
    void theStaysBlockIsLeftOutWhenTheItineraryCannotBeCancelledWhole() {
        givenALiveItinerary();
        given(flightTrips.staysBooked(any(BookedItineraryView.class), any(Instant.class)))
                .willReturn(List.of(new StayingFlight(bookedView("YOW→YYZ", 20), "QX4TZN")));
        willThrow(new FlightItineraryHasDeparted("left"))
                .given(cancelFlightItinerary)
                .cancelItinerary(any(UUID.class), any(CancelFlightItineraryRequest.class), any(Instant.class));

        assertThat(mockMvc.post().uri("/booked-itineraries/{id}/cancel", itineraryId).with(csrf()))
                .hasStatusOk()
                .bodyText()
                .as("nothing is being cancelled, so there is nothing to say stays")
                .doesNotContain("Stays booked.");
    }

    @Test
    void aLegAlreadyCancelledOnItsOwnIsNotListed() {
        givenALiveItinerary();
        given(detailsProjector.findById(back)).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/booked-itineraries/{id}/cancel", itineraryId))
                .hasStatusOk()
                .bodyText()
                .contains("1 flight will be cancelled")
                .doesNotContain("<span>UA2092</span>");
    }

    @Test
    void confirmationPagePostsBackToItsOwnCancelPath() {
        givenALiveItinerary();

        assertThat(mockMvc.get().uri("/booked-itineraries/{id}/cancel", itineraryId))
                .hasStatusOk()
                .bodyText()
                .contains("action=\"/booked-itineraries/" + itineraryId + "/cancel\"");
    }

    @Test
    void confirmingCancelsTheItineraryAndReturnsToTheList() {
        givenALiveItinerary();

        assertThat(mockMvc.post().uri("/booked-itineraries/{id}/cancel", itineraryId)
                           .param("reason", "Trip called off")
                           .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");

        then(cancelFlightItinerary).should()
                .cancelItinerary(any(UUID.class),
                        eq(new CancelFlightItineraryRequest(itineraryId, "Trip called off")),
                        eq(WebTodayTestConfig.FIXED_INSTANT));
    }

    @Test
    void anOmittedReasonIsRecordedAsEmptyRatherThanNull() {
        givenALiveItinerary();

        assertThat(mockMvc.post().uri("/booked-itineraries/{id}/cancel", itineraryId).with(csrf()))
                .hasStatus3xxRedirection();

        then(cancelFlightItinerary).should()
                .cancelItinerary(any(UUID.class), eq(new CancelFlightItineraryRequest(itineraryId, "")),
                        any(Instant.class));
    }

    @Test
    void aStaleLinkForAnItineraryThatIsNotLiveNavigatesToTheListWritingNothing() {
        given(itinerariesProjector.findLive(FlightItineraryId.of(itineraryId))).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/booked-itineraries/{id}/cancel", itineraryId))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");

        assertThat(mockMvc.post().uri("/booked-itineraries/{id}/cancel", itineraryId).with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");

        then(cancelFlightItinerary).shouldHaveNoInteractions();
    }

    @Test
    void aMalformedIdNavigatesToTheListRatherThanFailing() {
        assertThat(mockMvc.get().uri("/booked-itineraries/not-a-uuid/cancel"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");

        then(cancelFlightItinerary).shouldHaveNoInteractions();
    }

    @Test
    void cancelledInAnotherTabBetweenLookupAndWriteIsNotAnError() {
        givenALiveItinerary();
        willThrow(new FlightItineraryNotFound("gone"))
                .given(cancelFlightItinerary)
                .cancelItinerary(any(UUID.class), any(CancelFlightItineraryRequest.class), any(Instant.class));

        assertThat(mockMvc.post().uri("/booked-itineraries/{id}/cancel", itineraryId).with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-flights");
    }

    @Test
    void aDepartedLegIsAnsweredOnThePageWithALinkToCancelEachRemainingFlight() {
        givenALiveItinerary();
        willThrow(new FlightItineraryHasDeparted("left"))
                .given(cancelFlightItinerary)
                .cancelItinerary(any(UUID.class), any(CancelFlightItineraryRequest.class), any(Instant.class));

        assertThat(mockMvc.post().uri("/booked-itineraries/{id}/cancel", itineraryId)
                           .param("reason", "Trip called off")
                           .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("This itinerary cannot be cancelled as a whole: a flight on it has already departed.")
                .contains("href=\"/booked-flights/" + out.id() + "/cancel\"")
                .contains("href=\"/booked-flights/" + back.id() + "/cancel\"")
                .doesNotContain("<button type=\"submit\" class=\"danger\">");
    }

    @Test
    void readOnlyModeSendsTheReaderToThePageThatExplainsIt() {
        givenALiveItinerary();
        willThrow(new ReadOnlyModeException("read-only"))
                .given(cancelFlightItinerary)
                .cancelItinerary(any(UUID.class), any(CancelFlightItineraryRequest.class), any(Instant.class));

        assertThat(mockMvc.post().uri("/booked-itineraries/{id}/cancel", itineraryId).with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/read-only");
    }

    /**
     * Red because nothing puts the itinerary back from inside the app; no typed word because
     * appending cancellations destroys nothing.
     */
    @Test
    void theConfirmationIsRedAndTakesNoTypedWord() {
        givenALiveItinerary();

        assertThat(mockMvc.get().uri("/booked-itineraries/{id}/cancel", itineraryId))
                .hasStatusOk()
                .bodyText()
                .contains("<button type=\"submit\" class=\"danger\">Cancel this itinerary</button>")
                .contains("background: #b00;")
                .doesNotContain("placeholder=\"CANCEL\"");
    }

    private void givenALiveItinerary() {
        // Named back-then-out on purpose: the page sorts, it does not trust the order it is given.
        given(itinerariesProjector.findLive(FlightItineraryId.of(itineraryId)))
                .willReturn(Optional.of(new BookedItineraryView(FlightItineraryId.of(itineraryId),
                        "United Airlines", "MD7LKB", List.of(back, out), false)));
        given(detailsProjector.findById(out))
                .willReturn(Optional.of(leg(out, "UA2091", "SFO", "ORD", 18)));
        given(detailsProjector.findById(back))
                .willReturn(Optional.of(leg(back, "UA2092", "ORD", "SFO", 25)));
    }

    private static BookedFlightView bookedView(String route, int dayOfOctober) {
        return new BookedFlightView(FlightId.random(), "United Airlines", "UA1", route,
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, dayOfOctober, 6, 10), LOS_ANGELES),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, dayOfOctober, 8, 0), LOS_ANGELES),
                List.of());
    }

    private static FlightDetailsView leg(FlightId id, String flightNumber, String from, String to, int day) {
        return new FlightDetailsView(id, "United Airlines", flightNumber,
                AirportCode.of(from),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, day, 6, 10), LOS_ANGELES),
                AirportCode.of(to),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, day, 12, 45), LOS_ANGELES));
    }
}
