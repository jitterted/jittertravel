package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

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

        controller.bookHotelForm(model, null);

        BookHotelRequest request = (BookHotelRequest) model.getAttribute("bookHotel");
        assertThat(request.getCheckIn()).isEqualTo(LocalDateTime.of(2026, 6, 14, 15, 0));
        assertThat(request.getCheckOut()).isEqualTo(LocalDateTime.of(2026, 6, 15, 11, 0));
    }

    @Test
    void getBookHotelFormWithDateSeedsCheckInOnThatDayAtThreePmAndCheckOutNextMorning() {
        BookHotelController controller = new BookHotelController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookHotelForm(model, LocalDate.of(2026, 7, 20));

        BookHotelRequest request = (BookHotelRequest) model.getAttribute("bookHotel");
        assertThat(request.getCheckIn()).isEqualTo(LocalDateTime.of(2026, 7, 20, 15, 0));
        assertThat(request.getCheckOut()).isEqualTo(LocalDateTime.of(2026, 7, 21, 11, 0));
    }

    @Test
    void getBookHotelFormAssignsDistinctHotelBookingIdOnEachRequest() {
        BookHotelController controller = new BookHotelController(null, FIXED_CLOCK);

        Model model1 = new ConcurrentModel();
        Model model2 = new ConcurrentModel();
        controller.bookHotelForm(model1, null);
        controller.bookHotelForm(model2, null);

        BookHotelRequest request1 = (BookHotelRequest) model1.getAttribute("bookHotel");
        BookHotelRequest request2 = (BookHotelRequest) model2.getAttribute("bookHotel");
        assertThat(request1.getHotelBookingId()).isNotNull().isNotEmpty();
        assertThat(request1.getHotelBookingId()).isNotEqualTo(request2.getHotelBookingId());
    }
}
