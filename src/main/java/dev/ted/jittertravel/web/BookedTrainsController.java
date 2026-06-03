package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedTrainsProjector;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class BookedTrainsController {

    private final BookedTrainsProjector projector;

    public BookedTrainsController(BookedTrainsProjector projector) {
        this.projector = projector;
    }

    @GetMapping("/booked-trains")
    public String bookedTrains(Model model) {
        model.addAttribute("trains", projector.views());
        return "booked-trains";
    }
}
