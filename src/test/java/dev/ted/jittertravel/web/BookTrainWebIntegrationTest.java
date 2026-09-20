package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TrainBooking;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.InvalidTrainEntry;
import dev.ted.jittertravel.domain.LocationField;
import dev.ted.jittertravel.domain.LocationRole;
import dev.ted.jittertravel.domain.UnresolvedStationZone;
import org.junit.jupiter.api.BeforeEach;
import dev.ted.jittertravel.domain.OverlappingLegRefused;
import dev.ted.jittertravel.domain.ScheduledLeg;
import dev.ted.jittertravel.domain.ScheduledLegId;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(BookTrainController.class)
@WithMockUser(roles = "OWNER")
class BookTrainWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    TrainBooking trainBooking;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(Instant.parse("2026-06-01T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    @Test
    void getBookTrainFormRendersSuccessfully() {
        assertThat(mockMvc.get().uri("/book-train"))
                .hasStatusOk();
    }

    @Test
    void postValidTrainRedirectsToBookedTrains() {
        assertThat(mockMvc.post().uri("/book-train")
                .with(csrf())
                .param("trainTripId", "550e8400-e29b-41d4-a716-446655440000")
                .param("departureStationName", "London Euston")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureMapsUrl", "")
                .param("departureDateTime", "2026-07-01T09:00")
                .param("arrivalStationName", "Manchester Piccadilly")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalMapsUrl", "")
                .param("arrivalDateTime", "2026-07-01T13:00"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");
    }

    @Test
    void postWithPastDepartureRendersFormAgain() {
        willThrow(new DepartureNotInFuture("Departure must be in the future"))
                .given(trainBooking).bookTrain(any(), any());

        assertThat(mockMvc.post().uri("/book-train")
                .with(csrf())
                .param("trainTripId", "550e8400-e29b-41d4-a716-446655440000")
                .param("departureStationName", "London Euston")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureDateTime", "2025-01-01T09:00")
                .param("arrivalStationName", "Manchester Piccadilly")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalDateTime", "2025-01-01T13:00"))
                .hasStatusOk();
    }

    @Test
    void postWithArrivalBeforeDepartureRendersFormAgain() {
        willThrow(new InvalidDateRange("Arrival must be after departure"))
                .given(trainBooking).bookTrain(any(), any());

        assertThat(mockMvc.post().uri("/book-train")
                .with(csrf())
                .param("trainTripId", "550e8400-e29b-41d4-a716-446655440000")
                .param("departureStationName", "London Euston")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureDateTime", "2026-07-01T13:00")
                .param("arrivalStationName", "Manchester Piccadilly")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalDateTime", "2026-07-01T09:00"))
                .hasStatusOk();
    }

    @Test
    void stationPastedIntoTheArrivalCityErrorsOnThatCityField() {
        willThrow(new InvalidTrainEntry(List.of(
                new InvalidLocationEntry(LocationRole.ARRIVAL, LocationField.CITY,
                        "Venue name, not a city")), List.of()))
                .given(trainBooking).bookTrain(any(), any());

        MvcTestResult result = mockMvc.post().uri("/book-train")
                .with(csrf())
                .param("trainTripId", "550e8400-e29b-41d4-a716-446655440000")
                .param("departureStationName", "London Euston")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureDateTime", "2026-07-01T09:00")
                .param("arrivalStationName", "Frankfurt (Main) Hbf")
                .param("arrivalCityName", "Frankfurt (Main) Hbf")
                .param("arrivalCountry", "DE")
                .param("arrivalDateTime", "2026-07-01T13:00")
                .exchange();

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("bookTrain")
                .hasOnlyFieldErrors("arrivalCityName")
                .hasFieldErrorCode("arrivalCityName", "invalidLocation");
        // The field error is only half of it: the form has to be able to show it.
        assertThat(result)
                .bodyText()
                .contains("<span class=\"error\">Venue name, not a city</span>");
    }

    @Test
    void missingDepartureStationNameErrorsOnThatNameField() {
        willThrow(new InvalidTrainEntry(List.of(
                new InvalidLocationEntry(LocationRole.DEPARTURE, LocationField.VENUE_NAME,
                        "Name is required")), List.of()))
                .given(trainBooking).bookTrain(any(), any());

        MvcTestResult result = mockMvc.post().uri("/book-train")
                .with(csrf())
                .param("trainTripId", "550e8400-e29b-41d4-a716-446655440000")
                .param("departureStationName", "")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureDateTime", "2026-07-01T09:00")
                .param("arrivalStationName", "Manchester Piccadilly")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalDateTime", "2026-07-01T13:00")
                .exchange();

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("bookTrain")
                .hasOnlyFieldErrors("departureStationName");
        assertThat(result)
                .bodyText()
                .contains("<span class=\"error\">Name is required</span>");
    }

    @Test
    void aBlankCountryErrorsOnThatCountryFieldBecauseTypingOneIsTheFix() {
        willThrow(new InvalidTrainEntry(List.of(), List.of(
                new UnresolvedStationZone(LocationRole.ARRIVAL,
                        UnresolvedStationZone.Cause.COUNTRY_MISSING))))
                .given(trainBooking).bookTrain(any(), any());

        MvcTestResult result = trip("Frankfurt", "");

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("bookTrain")
                .hasOnlyFieldErrors("arrivalCountry")
                .hasFieldErrorCode("arrivalCountry", "zoneUnresolved");
        assertThat(result)
                .bodyText()
                .contains("<span class=\"error\">Country or time zone required</span>");
    }

    @Test
    void anUnrecognisedCountryErrorsOnTheZoneSelectBecauseRetypingItCannotHelp() {
        willThrow(new InvalidTrainEntry(List.of(), List.of(
                new UnresolvedStationZone(LocationRole.ARRIVAL,
                        UnresolvedStationZone.Cause.COUNTRY_UNRECOGNISED))))
                .given(trainBooking).bookTrain(any(), any());

        MvcTestResult result = trip("Frankfurt", "DE");

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("bookTrain")
                .hasOnlyFieldErrors("arrivalZone")
                .hasFieldErrorCode("arrivalZone", "zoneUnresolved");
        assertThat(result)
                .bodyText()
                .contains("<span class=\"error\">Unknown country — pick a zone, "
                          + "or fix Country name above</span>");
    }

    @Test
    void bothUnresolvableEndsAreMarkedOnTheOnePage() {
        willThrow(new InvalidTrainEntry(List.of(), List.of(
                new UnresolvedStationZone(LocationRole.DEPARTURE,
                        UnresolvedStationZone.Cause.COUNTRY_MISSING),
                new UnresolvedStationZone(LocationRole.ARRIVAL,
                        UnresolvedStationZone.Cause.COUNTRY_MISSING))))
                .given(trainBooking).bookTrain(any(), any());

        MvcTestResult result = trip("Frankfurt", "");

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("bookTrain")
                .hasOnlyFieldErrors("departureCountry", "arrivalCountry");
        assertThat(result)
                .bodyText()
                .as("the count is the one thing the marked fields cannot say from below the fold")
                .contains("2 problems to fix below.");
    }

    @Test
    void oneProblemIsCountedInTheSingular() {
        willThrow(new InvalidTrainEntry(List.of(), List.of(
                new UnresolvedStationZone(LocationRole.ARRIVAL,
                        UnresolvedStationZone.Cause.COUNTRY_MISSING))))
                .given(trainBooking).bookTrain(any(), any());

        assertThat(trip("Frankfurt", ""))
                .bodyText()
                .contains("1 problem to fix below.")
                .doesNotContain("1 problems to fix below.");
    }

    @Test
    void aMissingCityAtOneEndAndAMissingCountryAtTheOtherBothLand() {
        // Ted's second report, 2026-09-06. The two problems are different kinds, and the first
        // implementation reported only the location one — so the form still took two submits.
        willThrow(new InvalidTrainEntry(
                List.of(new InvalidLocationEntry(LocationRole.DEPARTURE, LocationField.CITY,
                        "City is required")),
                List.of(new UnresolvedStationZone(LocationRole.ARRIVAL,
                        UnresolvedStationZone.Cause.COUNTRY_MISSING))))
                .given(trainBooking).bookTrain(any(), any());

        MvcTestResult result = trip("Frankfurt", "");

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("bookTrain")
                .hasOnlyFieldErrors("departureCityName", "arrivalCountry");
        assertThat(result)
                .bodyText()
                .contains("<span class=\"error\">City is required</span>")
                .contains("<span class=\"error\">Country or time zone required</span>")
                .contains("2 problems to fix below.");
    }

    /**
     * <strong>Two problems at the <em>same</em> end, which only became possible on 2026-09-20</strong>
     * when {@code EnteredLocation} started answering with every rule a location breaks rather than
     * the earliest. Both inputs of the one fieldset are marked and the count says two — position
     * cannot distinguish them, since they are the two halves of the same station.
     */
    @Test
    void oneEndMissingBothItsNameAndItsCityMarksBothInputs() {
        willThrow(new InvalidTrainEntry(
                List.of(new InvalidLocationEntry(LocationRole.ARRIVAL, LocationField.VENUE_NAME,
                                "Name is required"),
                        new InvalidLocationEntry(LocationRole.ARRIVAL, LocationField.CITY,
                                "City is required")),
                List.of()))
                .given(trainBooking).bookTrain(any(), any());

        MvcTestResult result = trip("", "Germany");

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("bookTrain")
                .hasOnlyFieldErrors("arrivalStationName", "arrivalCityName");
        assertThat(result)
                .bodyText()
                .contains("<span class=\"error\">Name is required</span>")
                .contains("<span class=\"error\">City is required</span>")
                .contains("2 problems to fix below.");
    }

    @Test
    void aSubmitThatSucceedsGetsNoCountBanner() {
        assertThat(trip("Frankfurt", "Germany"))
                .hasStatus3xxRedirection();
    }

    /** A well-formed booking whose arrival city and country are the variable under test. */
    private MvcTestResult trip(String arrivalCity, String arrivalCountry) {
        return mockMvc.post().uri("/book-train")
                .with(csrf())
                .param("trainTripId", "550e8400-e29b-41d4-a716-446655440000")
                .param("departureStationName", "Aschaffenburg Hbf")
                .param("departureCityName", "Aschaffenburg")
                .param("departureCountry", "Germany")
                .param("departureDateTime", "2026-07-01T09:00")
                .param("arrivalStationName", arrivalCity + " Hbf")
                .param("arrivalCityName", arrivalCity)
                .param("arrivalCountry", arrivalCountry)
                .param("arrivalDateTime", "2026-07-01T13:00")
                .exchange();
    }
    // -------------------------------------------------------------------------
    // An overlapping journey is refused at entry (slice 3)
    // -------------------------------------------------------------------------

    @Test
    void anOverlappingTripIsRefusedAndNamesTheBlockingLegAsALink() throws Exception {
        // Ted, 2026-09-06: wherever the existing entry is named it links to that entry — its
        // details page, or its edit page while there is none, which is the case for a train.
        TrainTripId blocking = TrainTripId.random();
        willThrow(new OverlappingLegRefused(new ScheduledLeg(
                new ScheduledLegId.Train(blocking),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 9, 0), ZoneId.of("Europe/London")),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 11, 0), ZoneId.of("Europe/London")))))
                .given(trainBooking).bookTrain(any(), any());

        String html = submitTrip();

        assertThat(html)
                .as("the message lands under the departure time, the value this form can change")
                .contains("Overlaps a train departing Jul 1, 9:00 AM")
                .as("and the other way out is a link to the leg already booked")
                .contains("<a class=\"overlap-link\"")
                .contains("href=\"/booked-trains/" + blocking.id() + "\"")
                .contains("Open that train");
    }

    @Test
    void anOrdinaryRejectionRendersNoOverlapLink() throws Exception {
        willThrow(new DepartureNotInFuture("Departure must be in the future"))
                .given(trainBooking).bookTrain(any(), any());

        assertThat(submitTrip())
                .doesNotContain("<a class=\"overlap-link\"");
    }

    /** The valid-shaped submission every refusal case above drives through the form. */
    private String submitTrip() throws Exception {
        return mockMvc.post().uri("/book-train")
                .with(csrf())
                .param("trainTripId", "550e8400-e29b-41d4-a716-446655440000")
                .param("departureStationName", "London Euston")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureDateTime", "2026-07-01T10:00")
                .param("arrivalStationName", "Manchester Piccadilly")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalDateTime", "2026-07-01T13:00")
                .exchange().getResponse().getContentAsString();
    }

}
