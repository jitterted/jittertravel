package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TrainBooking;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.InvalidTrainEntry;
import dev.ted.jittertravel.domain.LocationField;
import dev.ted.jittertravel.domain.LocationRole;
import dev.ted.jittertravel.domain.OverlappingLegRefused;
import dev.ted.jittertravel.domain.ScheduledLeg;
import dev.ted.jittertravel.domain.ScheduledLegId;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.UnresolvedStationZone;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class BookTrainControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 6, 2, 10, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant(),
            ZoneId.systemDefault());

    @Test
    void getBookTrainFormSetsDepartureOneWeekFromNowAtNineAm() {
        BookTrainController controller = new BookTrainController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookTrainForm(model, null, null, null);

        BookTrainRequest request = (BookTrainRequest) model.getAttribute("bookTrain");
        assertThat(request.departureDateTime())
                .isEqualTo(LocalDateTime.of(2026, 6, 9, 9, 0));
    }

    @Test
    void getBookTrainFormWithDateSeedsDepartureOnThatDayAtNineAm() {
        BookTrainController controller = new BookTrainController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookTrainForm(model, LocalDate.of(2026, 7, 15), null, null);

        BookTrainRequest request = (BookTrainRequest) model.getAttribute("bookTrain");
        assertThat(request.departureDateTime())
                .isEqualTo(LocalDateTime.of(2026, 7, 15, 9, 0));
        assertThat(request.arrivalDateTime())
                .isEqualTo(LocalDateTime.of(2026, 7, 15, 13, 0));
    }

    @Test
    void getBookTrainFormSetsArrivalSameDayAsDeparture() {
        BookTrainController controller = new BookTrainController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookTrainForm(model, null, null, null);

        BookTrainRequest request = (BookTrainRequest) model.getAttribute("bookTrain");
        assertThat(request.arrivalDateTime().toLocalDate())
                .isEqualTo(request.departureDateTime().toLocalDate());
    }

    @Test
    void getBookTrainFormAssignsDistinctTripIdOnEachRequest() {
        BookTrainController controller = new BookTrainController(null, FIXED_CLOCK);

        Model model1 = new ConcurrentModel();
        Model model2 = new ConcurrentModel();
        controller.bookTrainForm(model1, null, null, null);
        controller.bookTrainForm(model2, null, null, null);

        BookTrainRequest r1 = (BookTrainRequest) model1.getAttribute("bookTrain");
        BookTrainRequest r2 = (BookTrainRequest) model2.getAttribute("bookTrain");
        assertThat(r1.trainTripId())
                .isNotNull()
                .isNotEmpty()
                .isNotEqualTo(r2.trainTripId());
    }
    /**
     * The cleanest prefill in the slice: {@link BookTrainRequest} already carries city names, so a
     * travel gap's own cities go straight in with no resolution step and no ambiguity.
     */
    @Test
    void aFixLinksCitiesSeedTheStationCityFields() {
        BookTrainController controller = new BookTrainController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookTrainForm(model, LocalDate.of(2026, 6, 22), "Frankfurt", "Leipzig");

        BookTrainRequest request = (BookTrainRequest) model.getAttribute("bookTrain");
        assertThat(request.departureCityName()).isEqualTo("Frankfurt");
        assertThat(request.arrivalCityName()).isEqualTo("Leipzig");
        assertThat(request.departureDateTime()).isEqualTo(LocalDateTime.of(2026, 6, 22, 9, 0));
    }

    @Test
    void blankCitiesAreLeftAloneRatherThanWrittenIn() {
        BookTrainController controller = new BookTrainController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookTrainForm(model, null, "  ", null);

        BookTrainRequest request = (BookTrainRequest) model.getAttribute("bookTrain");
        assertThat(request.departureCityName()).isNull();
        assertThat(request.arrivalCityName()).isNull();
    }

    /**
     * Each refusal the service can raise lands on the inputs that fix it, and the form is
     * re-rendered. The service is a stub programmed to throw, so the only code deciding the field
     * is the controller's own; whether a real input produces each refusal is
     * {@code BookTrainCommandTest}'s and {@code BookTrainHandlerTest}'s to say.
     */
    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("refusals")
    void eachRefusalLandsOnTheInputsThatFixIt(RuntimeException refusal, List<String> fields) {
        BookTrainController controller = new BookTrainController(refusing(refusal), FIXED_CLOCK);
        BookTrainRequest request = aTrip();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookTrain");

        String view = controller.bookTrainSubmit(request, bindingResult, null, new ConcurrentModel());

        assertThat(view)
                .as("a refusal re-renders the form, where the error can be seen")
                .isEqualTo("book-train");
        assertThat(bindingResult.getFieldErrors())
                .extracting(FieldError::getField)
                .as("the refusal is reported on exactly these inputs")
                .containsExactlyInAnyOrderElementsOf(fields);
    }

    static Stream<Arguments> refusals() {
        ZoneId london = ZoneId.of("Europe/London");
        return Stream.of(
                arguments(new DepartureNotInFuture("Departure must be in the future"),
                          List.of("departureDateTime")),
                arguments(new InvalidDateRange("Arrival must be after departure"),
                          List.of("arrivalDateTime")),
                arguments(new OverlappingLegRefused(new ScheduledLeg(
                                  new ScheduledLegId.Train(TrainTripId.random()),
                                  ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 9, 0), london),
                                  ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 11, 0), london))),
                          List.of("departureDateTime")),
                arguments(new InvalidTrainEntry(List.of(
                                  new InvalidLocationEntry(LocationRole.ARRIVAL, LocationField.CITY,
                                                           "Venue name, not a city")), List.of()),
                          List.of("arrivalCityName")),
                arguments(new InvalidTrainEntry(List.of(
                                  new InvalidLocationEntry(LocationRole.DEPARTURE, LocationField.COUNTRY,
                                                           "Unknown country")), List.of()),
                          List.of("departureCountry")),
                arguments(new InvalidTrainEntry(List.of(), List.of(
                                  new UnresolvedStationZone(LocationRole.DEPARTURE,
                                                            UnresolvedStationZone.Cause.COUNTRY_MISSING),
                                  new UnresolvedStationZone(LocationRole.ARRIVAL,
                                                            UnresolvedStationZone.Cause.COUNTRY_UNRECOGNISED))),
                          List.of("departureCountry", "arrivalZone")));
    }

    /** Any well-formed trip: which refusal comes back is the stub's choice, not the input's. */
    private static BookTrainRequest aTrip() {
        return new BookTrainRequest(
                UUID.randomUUID().toString(), null,
                "London Euston", "London", "UK", null, null,
                LocalDateTime.of(2026, 6, 9, 9, 0),
                "Manchester Piccadilly", "Manchester", "UK", null, null,
                LocalDateTime.of(2026, 6, 9, 13, 0));
    }

    /** A service whose every booking is refused with {@code refusal}. */
    private static TrainBooking refusing(RuntimeException refusal) {
        return new TrainBooking(null, null, null) {
            @Override
            public void bookTrain(BookTrainRequest request, Instant now) {
                throw refusal;
            }
        };
    }
}
