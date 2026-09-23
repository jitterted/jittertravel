package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.HotelBooking;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.CheckInNotInFuture;
import dev.ted.jittertravel.domain.InvalidCancelByDate;
import dev.ted.jittertravel.domain.InvalidEnteredLocation;
import dev.ted.jittertravel.domain.InvalidHotelDateRange;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.LocationField;
import dev.ted.jittertravel.domain.LocationRole;
import dev.ted.jittertravel.domain.ZoneResolutionException;
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

class BookHotelControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 5, 31, 10, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant(),
            ZoneId.systemDefault());

    @Test
    void getBookHotelFormSetsCheckInTwoWeeksFromNowAtThreePmAndCheckOutNextMorningAtElevenAm() {
        BookHotelController controller = new BookHotelController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookHotelForm(model, null, null, null, null);

        BookHotelRequest request = (BookHotelRequest) model.getAttribute("bookHotel");
        assertThat(request.checkIn()).isEqualTo(LocalDateTime.of(2026, 6, 14, 15, 0));
        assertThat(request.checkOut()).isEqualTo(LocalDateTime.of(2026, 6, 15, 11, 0));
    }

    @Test
    void getBookHotelFormWithDateSeedsCheckInOnThatDayAtThreePmAndCheckOutNextMorning() {
        BookHotelController controller = new BookHotelController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookHotelForm(model, LocalDate.of(2026, 7, 20), null, null, null);

        BookHotelRequest request = (BookHotelRequest) model.getAttribute("bookHotel");
        assertThat(request.checkIn()).isEqualTo(LocalDateTime.of(2026, 7, 20, 15, 0));
        assertThat(request.checkOut()).isEqualTo(LocalDateTime.of(2026, 7, 21, 11, 0));
    }

    /**
     * The "Book hotel" fix link on /schedule-problems knows exactly which city and which nights are
     * uncovered, so the form opens on them instead of on a default fortnight away. The clock times
     * stay the form's own (15:00 / 11:00): the night sweep carries dates, not times.
     */
    @Test
    void getBookHotelFormWithAFixLinksCityAndNightsOpensOnThatStay() {
        BookHotelController controller = new BookHotelController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookHotelForm(model, null, "Johannesberg",
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14));

        BookHotelRequest request = (BookHotelRequest) model.getAttribute("bookHotel");
        assertThat(request.city()).isEqualTo("Johannesberg");
        assertThat(request.checkIn()).isEqualTo(LocalDateTime.of(2026, 9, 10, 15, 0));
        assertThat(request.checkOut())
                .as("the gap's own checkout, not a single night")
                .isEqualTo(LocalDateTime.of(2026, 9, 14, 11, 0));
    }

    @Test
    void aCheckOutThatIsNotAfterCheckInFallsBackToOneNight() {
        BookHotelController controller = new BookHotelController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookHotelForm(model, null, "Aachen",
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));

        BookHotelRequest request = (BookHotelRequest) model.getAttribute("bookHotel");
        assertThat(request.checkOut()).isEqualTo(LocalDateTime.of(2026, 9, 11, 11, 0));
    }

    @Test
    void aBlankCityIsLeftAloneRatherThanWrittenIn() {
        BookHotelController controller = new BookHotelController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookHotelForm(model, null, "  ", null, null);

        BookHotelRequest request = (BookHotelRequest) model.getAttribute("bookHotel");
        assertThat(request.city()).isNull();
    }

    @Test
    void getBookHotelFormAssignsDistinctHotelBookingIdOnEachRequest() {
        BookHotelController controller = new BookHotelController(null, FIXED_CLOCK);

        Model model1 = new ConcurrentModel();
        Model model2 = new ConcurrentModel();
        controller.bookHotelForm(model1, null, null, null, null);
        controller.bookHotelForm(model2, null, null, null, null);

        BookHotelRequest request1 = (BookHotelRequest) model1.getAttribute("bookHotel");
        BookHotelRequest request2 = (BookHotelRequest) model2.getAttribute("bookHotel");
        assertThat(request1.hotelBookingId()).isNotNull().isNotEmpty();
        assertThat(request1.hotelBookingId()).isNotEqualTo(request2.hotelBookingId());
    }

    /**
     * Each refusal the service can raise lands on the inputs that fix it, and the form is
     * re-rendered. The service is a stub programmed to throw, so the only code deciding the field
     * is the controller's own; whether a real input produces each refusal is
     * {@code BookHotelCommandTest}'s and {@code HotelHandlerTest}'s to say.
     */
    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("refusals")
    void eachRefusalLandsOnTheInputsThatFixIt(RuntimeException refusal, List<String> fields) {
        BookHotelController controller = new BookHotelController(refusing(refusal), FIXED_CLOCK);
        BookHotelRequest request = aStay();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookHotel");

        String view = controller.bookHotelSubmit(request, bindingResult, null);

        assertThat(view)
                .as("a refusal re-renders the form, where the error can be seen")
                .isEqualTo("book-hotel");
        assertThat(bindingResult.getFieldErrors())
                .extracting(FieldError::getField)
                .as("the refusal is reported on exactly these inputs")
                .containsExactlyInAnyOrderElementsOf(fields);
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                arguments(new CheckInNotInFuture("Check-in must be in the future"),
                          List.of("checkIn")),
                arguments(new InvalidHotelDateRange("Check-out must be at least one day after check-in"),
                          List.of("checkOut")),
                arguments(new InvalidCancelByDate("Cancel-by must not be after check-in"),
                          List.of("cancelBy")),
                arguments(new InvalidEnteredLocation(List.of(
                                  locationProblem(LocationField.VENUE_NAME, "Name is required"))),
                          List.of("hotelName")),
                arguments(new InvalidEnteredLocation(List.of(
                                  locationProblem(LocationField.CITY, "City is required"))),
                          List.of("city")),
                arguments(new InvalidEnteredLocation(List.of(
                                  locationProblem(LocationField.VENUE_NAME, "Name is required"),
                                  locationProblem(LocationField.CITY, "City is required"))),
                          List.of("hotelName", "city")),
                arguments(new ZoneResolutionException("Springfield", "Freedonia"),
                          List.of("zone")));
    }

    private static InvalidLocationEntry locationProblem(LocationField field, String message) {
        return new InvalidLocationEntry(LocationRole.STAY, field, message);
    }

    /** Any well-formed stay: which refusal comes back is the stub's choice, not the input's. */
    private static BookHotelRequest aStay() {
        return new BookHotelRequest(
                UUID.randomUUID().toString(), "Grand Hotel",
                "123 Main St", "Springfield", "IL", "US", "62701", null, null,
                "US_CENTRAL", LocalDateTime.of(2026, 6, 14, 15, 0), LocalDateTime.of(2026, 6, 15, 11, 0),
                null, BookingIntent.TENTATIVE);
    }

    /** A service whose every booking is refused with {@code refusal}. */
    private static HotelBooking refusing(RuntimeException refusal) {
        return new HotelBooking(null, null) {
            @Override
            public void bookHotel(BookHotelRequest request, Instant now) {
                throw refusal;
            }
        };
    }
}
