package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.HotelBooking;
import dev.ted.jittertravel.domain.CheckInNotInFuture;
import dev.ted.jittertravel.domain.InvalidHotelDateRange;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Controller
public class BookHotelController {
    private final HotelBooking hotelBooking;
    private final Clock clock;

    public BookHotelController(HotelBooking hotelBooking, Clock clock) {
        this.hotelBooking = hotelBooking;
        this.clock = clock;
    }

    @GetMapping("/book-hotel")
    public String bookHotelForm(Model model) {
        BookHotelRequest request = new BookHotelRequest();
        request.setHotelBookingId(UUID.randomUUID().toString());
        var checkIn = LocalDate.now(clock).plusWeeks(2).atTime(15, 0);
        request.setCheckIn(checkIn);
        request.setCheckOut(checkIn.toLocalDate().plusDays(1).atTime(11, 0));
        model.addAttribute("bookHotel", request);
        return "book-hotel";
    }

    @PostMapping("/book-hotel")
    public String bookHotelSubmit(@ModelAttribute("bookHotel") BookHotelRequest request,
                                  BindingResult bindingResult) {
        try {
            hotelBooking.bookHotel(request);
        } catch (CheckInNotInFuture e) {
            bindingResult.rejectValue("checkIn", "future", e.getMessage());
        } catch (InvalidHotelDateRange e) {
            bindingResult.rejectValue("checkOut", "minOneDay", e.getMessage());
        }

        if (bindingResult.hasErrors()) {
            return "book-hotel";
        }

        return "redirect:/booked-hotels";
    }
}
