package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.FlightBooking;
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
        BookFlightController controller = new BookFlightController(writableService(), null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookFlightForm(model, null);

        BookFlightRequest request = (BookFlightRequest) model.getAttribute("bookFlight");
        assertThat(request.getDepartureDateTime()).isEqualTo(LocalDateTime.of(2026, 6, 7, 9, 0));
        assertThat(request.getArrivalDateTime()).isEqualTo(LocalDateTime.of(2026, 6, 7, 12, 0));
    }

    @Test
    void getBookFlightFormWithDateSeedsDepartureOnThatDayAtNineAmAndArrivalThreeHoursLater() {
        BookFlightController controller = new BookFlightController(writableService(), null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookFlightForm(model, LocalDate.of(2026, 7, 20));

        BookFlightRequest request = (BookFlightRequest) model.getAttribute("bookFlight");
        assertThat(request.getDepartureDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 20, 9, 0));
        assertThat(request.getArrivalDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 20, 12, 0));
    }

    @Test
    void getBookFlightFormSeedsLookupDateWithTheSameDayAsTheDepartureDefault() {
        BookFlightController controller = new BookFlightController(writableService(), null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookFlightForm(model, LocalDate.of(2026, 7, 20));

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
