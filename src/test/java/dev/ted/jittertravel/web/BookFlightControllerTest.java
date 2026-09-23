package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.FlightBooking;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.InvalidAirportCode;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.OverlappingLegRefused;
import dev.ted.jittertravel.domain.ScheduledLeg;
import dev.ted.jittertravel.domain.ScheduledLegId;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class BookFlightControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 5, 31, 10, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant(),
            ZoneId.systemDefault());

    @Test
    void getBookFlightFormSetsDepartureOneWeekFromNowAtNineAmAndArrivalThreeHoursLater() {
        BookFlightController controller = new BookFlightController(writableService(), null, new StaticAirportCityResolver(), FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookFlightForm(model, null, null, null);

        BookFlightRequest request = (BookFlightRequest) model.getAttribute("bookFlight");
        assertThat(request.getDepartureDateTime()).isEqualTo(LocalDateTime.of(2026, 6, 7, 9, 0));
        assertThat(request.getArrivalDateTime()).isEqualTo(LocalDateTime.of(2026, 6, 7, 12, 0));
    }

    @Test
    void getBookFlightFormWithDateSeedsDepartureOnThatDayAtNineAmAndArrivalThreeHoursLater() {
        BookFlightController controller = new BookFlightController(writableService(), null, new StaticAirportCityResolver(), FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookFlightForm(model, LocalDate.of(2026, 7, 20), null, null);

        BookFlightRequest request = (BookFlightRequest) model.getAttribute("bookFlight");
        assertThat(request.getDepartureDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 20, 9, 0));
        assertThat(request.getArrivalDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 20, 12, 0));
    }

    /**
     * F4: the fix link carries cities, never codes, because the airport table is many-to-one. A
     * city with exactly one airport seeds the field; anything else leaves it blank, because a wrong
     * prefilled airport is worse than an empty one — Ted has to notice it to undo it.
     */
    @Test
    void aFixLinksCitiesSeedTheAirportFieldsWhereEachCityHasExactlyOneAirport() {
        BookFlightController controller = new BookFlightController(
                writableService(), null, new StaticAirportCityResolver(), FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookFlightForm(model, LocalDate.of(2026, 9, 14), "Denver", "Frankfurt");

        BookFlightRequest request = (BookFlightRequest) model.getAttribute("bookFlight");
        assertThat(request.getDepartureAirport()).isEqualTo("DEN");
        assertThat(request.getArrivalAirport()).isEqualTo("FRA");
    }

    @Test
    void aCityWithSeveralAirportsLeavesTheFieldBlankRatherThanGuessing() {
        BookFlightController controller = new BookFlightController(
                writableService(), null, new StaticAirportCityResolver(), FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookFlightForm(model, LocalDate.of(2026, 9, 14), "London", "New York");

        BookFlightRequest request = (BookFlightRequest) model.getAttribute("bookFlight");
        assertThat(request.getDepartureAirport())
                .as("London is LHR/LGW/STN/LCY — picking one would be a guess Ted must notice")
                .isNull();
        assertThat(request.getArrivalAirport()).isNull();
        assertThat(request.getDepartureDateTime())
                .as("the dates still seed, so the link is useful even when the codes cannot be")
                .isEqualTo(LocalDateTime.of(2026, 9, 14, 9, 0));
    }

    @Test
    void aCityTheTableDoesNotKnowLeavesTheFieldBlank() {
        BookFlightController controller = new BookFlightController(
                writableService(), null, new StaticAirportCityResolver(), FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookFlightForm(model, null, "Soltau", "Johannesberg");

        BookFlightRequest request = (BookFlightRequest) model.getAttribute("bookFlight");
        assertThat(request.getDepartureAirport()).isNull();
        assertThat(request.getArrivalAirport()).isNull();
    }

    @Test
    void getBookFlightFormSeedsLookupDateWithTheSameDayAsTheDepartureDefault() {
        BookFlightController controller = new BookFlightController(writableService(), null, new StaticAirportCityResolver(), FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookFlightForm(model, LocalDate.of(2026, 7, 20), null, null);

        assertThat(model.getAttribute("lookupDepartureDate"))
                .isEqualTo(LocalDate.of(2026, 7, 20));
    }

    /**
     * The other half of a fix link's round trip. The markup half — the hidden input living in the
     * booking form and not the AeroDataBox lookup form beside it, which is where it shipped on
     * 2026-09-06 and where the POST never sends it — is pinned by
     * {@code ProblemContextFragmentConventionTest}.
     */
    @Test
    void bookingFromAFixLinkReturnsToTheReportItWasLaunchedFrom() {
        BookFlightController controller = new BookFlightController(
                bookingService(), null, new StaticAirportCityResolver(), FIXED_CLOCK);
        BookFlightRequest request = new BookFlightRequest();
        BindingResult noErrors = new BeanPropertyBindingResult(request, "bookFlight");

        String view = controller.bookFlightSubmit(request, noErrors, "list", new ConcurrentModel());

        assertThat(view).isEqualTo("redirect:/schedule-problems?view=list");
    }

    @Test
    void anOrdinaryBookingStillLandsOnTheFlightsList() {
        BookFlightController controller = new BookFlightController(
                bookingService(), null, new StaticAirportCityResolver(), FIXED_CLOCK);
        BookFlightRequest request = new BookFlightRequest();
        BindingResult noErrors = new BeanPropertyBindingResult(request, "bookFlight");

        String view = controller.bookFlightSubmit(request, noErrors, null, new ConcurrentModel());

        assertThat(view).isEqualTo("redirect:/booked-flights");
    }

    // The form GET only reads isReadOnly() and the clock; the AeroDataBoxClient is unused here.
    private FlightBooking writableService() {
        return new FlightBooking(null, null, null) {
            @Override public boolean isReadOnly() { return false; }

            @Override public void bookFlight(BookFlightRequest request, Instant now) {
                throw new UnsupportedOperationException("not used by the form GET");
            }
        };
    }

    /** Accepts the booking, so the POST reaches its redirect. */
    private FlightBooking bookingService() {
        return new FlightBooking(null, null, null) {
            @Override public boolean isReadOnly() { return false; }

            @Override public void bookFlight(BookFlightRequest request, Instant now) {
            }
        };
    }

    /**
     * Each refusal the service can raise lands where the form shows it — on the input that fixes
     * it, or, for the two that name no single input, as a global error at the top — and the form
     * is re-rendered. The service is a stub programmed to throw, so the only code deciding where is
     * the controller's own; whether a real input produces each refusal is
     * {@code BookFlightCommandTest}'s, {@code BookFlightHandlerTest}'s and {@code AirportCodeTest}'s
     * to say.
     */
    @ParameterizedTest(name = "{0} → fields {1}, global {2}")
    @MethodSource("refusals")
    void eachRefusalLandsWhereTheFormShowsIt(RuntimeException refusal,
                                             List<String> fields,
                                             List<String> globalCodes) {
        BookFlightController controller = new BookFlightController(
                refusing(refusal), null, new StaticAirportCityResolver(), FIXED_CLOCK);
        BookFlightRequest request = new BookFlightRequest();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookFlight");

        String view = controller.bookFlightSubmit(request, bindingResult, null, new ConcurrentModel());

        assertThat(view)
                .as("a refusal re-renders the form, where the error can be seen")
                .isEqualTo("book-flight");
        assertThat(bindingResult.getFieldErrors())
                .extracting(FieldError::getField)
                .as("the inputs the refusal is reported on")
                .containsExactlyInAnyOrderElementsOf(fields);
        assertThat(bindingResult.getGlobalErrors())
                .extracting(ObjectError::getCode)
                .as("the form-wide errors the refusal is reported as")
                .containsExactlyInAnyOrderElementsOf(globalCodes);
    }

    static Stream<Arguments> refusals() {
        ZoneId london = ZoneId.of("Europe/London");
        return Stream.of(
                arguments(new DepartureNotInFuture("Departure must be in the future"),
                          List.of("departureDateTime"), List.of()),
                arguments(new InvalidDateRange("Arrival must be after departure"),
                          List.of("arrivalDateTime"), List.of()),
                arguments(new OverlappingLegRefused(new ScheduledLeg(
                                  new ScheduledLegId.Flight(FlightId.random()),
                                  ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 9, 0), london),
                                  ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 11, 0), london))),
                          List.of("departureDateTime"), List.of()),
                arguments(new InvalidAirportCode("Airport code must be exactly 3 characters: BADCODE"),
                          List.of(), List.of("airportCode")),
                arguments(new ZoneResolutionException("ZZZ"),
                          List.of(), List.of("zoneUnresolved")));
    }

    @Test
    void readOnlyModeRedirectsRatherThanReportingAnError() {
        BookFlightController controller = new BookFlightController(
                refusing(new ReadOnlyModeException("read-only")), null,
                new StaticAirportCityResolver(), FIXED_CLOCK);
        BookFlightRequest request = new BookFlightRequest();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookFlight");

        String view = controller.bookFlightSubmit(request, bindingResult, null, new ConcurrentModel());

        assertThat(view)
                .isEqualTo("redirect:/read-only");
        assertThat(bindingResult.hasErrors())
                .as("nothing on the form was wrong")
                .isFalse();
    }

    /** A writable service whose every booking is refused with {@code refusal}. */
    private static FlightBooking refusing(RuntimeException refusal) {
        return new FlightBooking(null, null, null) {
            @Override public boolean isReadOnly() { return false; }

            @Override public void bookFlight(BookFlightRequest request, Instant now) {
                throw refusal;
            }
        };
    }
}
