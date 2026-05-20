package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightsProjector;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class BookedFlightsController {

    private final BookedFlightsProjector projector;

    public BookedFlightsController(BookedFlightsProjector projector) {
        this.projector = projector;
    }

    @GetMapping("/booked-flights")
    public String bookedFlights(Model model) {
        model.addAttribute("flights", projector.views());
        return "booked-flights";
    }
}
