package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.FlightBooking;
import dev.ted.jittertravel.application.StaticAirportCityResolver;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

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

    // The form GET only reads isReadOnly() and the clock; the AeroDataBoxClient is unused here.
    private FlightBooking writableService() {
        return new FlightBooking(null, null) {
            @Override public boolean isReadOnly() { return false; }

            @Override public void bookFlight(BookFlightRequest request, Instant now) {
                throw new UnsupportedOperationException("not used by the form GET");
            }
        };
    }
}
