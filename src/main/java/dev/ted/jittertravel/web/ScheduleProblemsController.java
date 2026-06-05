package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.ScheduleProblem;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class ScheduleProblemsController {

    private final ScheduleGapProjector scheduleGapProjector;

    public ScheduleProblemsController(ScheduleGapProjector scheduleGapProjector) {
        this.scheduleGapProjector = scheduleGapProjector;
    }

    @GetMapping("/schedule-problems")
    public String scheduleProblems(Model model) {
        List<ScheduleProblem> problems = scheduleGapProjector.problems();
        model.addAttribute("travelProblems", problems.stream()
                .filter(p -> p instanceof ScheduleProblem.MissingTravel).toList());
        model.addAttribute("hotelProblems", problems.stream()
                .filter(p -> p instanceof ScheduleProblem.MissingHotel).toList());
        model.addAttribute("schedulingProblems", problems.stream()
                .filter(p -> p instanceof ScheduleProblem.SchedulingConflict).toList());
        return "schedule-problems";
    }
}
