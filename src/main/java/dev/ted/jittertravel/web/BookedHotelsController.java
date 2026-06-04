package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedHotelsProjector;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class BookedHotelsController {

    private final BookedHotelsProjector projector;

    public BookedHotelsController(BookedHotelsProjector projector) {
        this.projector = projector;
    }

    @GetMapping("/booked-hotels")
    public String bookedHotels(Model model) {
        model.addAttribute("hotels", projector.views());
        return "booked-hotels";
    }
}
