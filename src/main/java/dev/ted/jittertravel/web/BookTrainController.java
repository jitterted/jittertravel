package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TrainBooking;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Controller
public class BookTrainController {

    private final TrainBooking trainBooking;
    private final Clock clock;

    public BookTrainController(TrainBooking trainBooking, Clock clock) {
        this.trainBooking = trainBooking;
        this.clock = clock;
    }

    @GetMapping("/book-train")
    public String bookTrainForm(Model model) {
        BookTrainRequest request = new BookTrainRequest();
        request.setTrainTripId(UUID.randomUUID().toString());
        LocalDateTime departure = LocalDate.now(clock).plusWeeks(1).atStartOfDay().plusHours(9);
        request.setDepartureDateTime(departure);
        request.setArrivalDateTime(departure.plusHours(4));
        model.addAttribute("bookTrain", request);
        return "book-train";
    }

    @PostMapping("/book-train")
    public String bookTrainSubmit(@ModelAttribute("bookTrain") BookTrainRequest request,
                                  BindingResult bindingResult) {
        try {
            trainBooking.bookTrain(request);
        } catch (DepartureNotInFuture e) {
            bindingResult.rejectValue("departureDateTime", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("arrivalDateTime", "afterDeparture", e.getMessage());
        }

        if (bindingResult.hasErrors()) {
            return "book-train";
        }

        return "redirect:/booked-trains";
    }
}
