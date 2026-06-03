package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

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

        controller.bookTrainForm(model);

        BookTrainRequest request = (BookTrainRequest) model.getAttribute("bookTrain");
        assertThat(request.getDepartureDateTime())
                .isEqualTo(LocalDateTime.of(2026, 6, 9, 9, 0));
    }

    @Test
    void getBookTrainFormSetsArrivalSameDayAsDeparture() {
        BookTrainController controller = new BookTrainController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.bookTrainForm(model);

        BookTrainRequest request = (BookTrainRequest) model.getAttribute("bookTrain");
        assertThat(request.getArrivalDateTime().toLocalDate())
                .isEqualTo(request.getDepartureDateTime().toLocalDate());
    }

    @Test
    void getBookTrainFormAssignsDistinctTripIdOnEachRequest() {
        BookTrainController controller = new BookTrainController(null, FIXED_CLOCK);

        Model model1 = new ConcurrentModel();
        Model model2 = new ConcurrentModel();
        controller.bookTrainForm(model1);
        controller.bookTrainForm(model2);

        BookTrainRequest r1 = (BookTrainRequest) model1.getAttribute("bookTrain");
        BookTrainRequest r2 = (BookTrainRequest) model2.getAttribute("bookTrain");
        assertThat(r1.getTrainTripId())
                .isNotNull()
                .isNotEmpty()
                .isNotEqualTo(r2.getTrainTripId());
    }
}
