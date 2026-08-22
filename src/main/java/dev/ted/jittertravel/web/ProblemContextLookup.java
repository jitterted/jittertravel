package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.ScheduleProblem;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the {@code ?problem=} reference on a fix link back into the banner the landing page
 * shows: the problem in the report's own words, and the schedule facts around it.
 * <p>
 * The report is the only source. A key that matches nothing — hand-edited, bookmarked from
 * yesterday, or naming a problem that has since been fixed in another tab — yields
 * {@link Optional#empty()}, and the page renders exactly as it does when reached from the index nav
 * card. That is the whole error path: there is no "problem not found" message, because the form is
 * perfectly usable without the banner and an error about a decoration is noise.
 * <p>
 * The wording comes from {@link ProblemBand} and {@link ContextBand} rather than being written
 * again here. Those are the problem calendar's view types, and reusing them is the point: the
 * banner has to say what the band Ted just clicked said, and a second copy of the phrasing is a
 * second copy to drift. The band's {@code fixes} are ignored — the banner explains, it does not
 * re-offer the fix you are already on.
 */
public class ProblemContextLookup {

    /**
     * The banner is a reminder, not a second report — the Back link is there for the rest. Four
     * lines is about a conference, its flights either side, and one stay.
     */
    private static final int MAX_CONTEXT_LINES = 4;

    /**
     * How far either side of the problem a context fact still counts as surrounding it. One day,
     * so the flight that lands the morning the gap opens still shows.
     */
    private static final int CONTEXT_PADDING_DAYS = 1;

    private final ScheduleGapProjector scheduleGapProjector;

    public ProblemContextLookup(ScheduleGapProjector scheduleGapProjector) {
        this.scheduleGapProjector = scheduleGapProjector;
    }

    /**
     * The banner for {@code key}, as of {@code now} — captured at the boundary by
     * {@link ProblemContextAdvice} and passed in, never read from an ambient clock here.
     * <p>
     * {@code now} is the same cut the report itself applies, so a problem that has aged out is not
     * explained on a page you can still reach: the link is stale in exactly the way the report says
     * it is.
     */
    public Optional<ProblemContextView> forKey(String key, FixOrigin origin, Instant now) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return new ProblemRef(key).findIn(scheduleGapProjector.problems(now))
                .map(problem -> banner(problem, origin));
    }

    private ProblemContextView banner(ScheduleProblem problem, FixOrigin origin) {
        ProblemBand band = ProblemBand.from(problem);
        return new ProblemContextView(
                band.marker().cssModifier(),
                band.title(),
                band.detail(),
                causesAround(band),
                origin.backLabel(),
                origin.backHref());
    }

    /**
     * What the schedule holds over the problem's own days, widened by a day at each end.
     * {@code context()} is deliberately unfiltered by {@code now} — clipping to the window being
     * drawn is the caller's job, exactly as it is on the problem calendar.
     */
    private List<String> causesAround(ProblemBand band) {
        LocalDate from = band.firstDay().minusDays(CONTEXT_PADDING_DAYS);
        LocalDate until = band.lastDay().plusDays(CONTEXT_PADDING_DAYS);
        return scheduleGapProjector.context().stream()
                .map(ContextBand::from)
                .filter(context -> !context.firstDay().isAfter(until) && !context.lastDay().isBefore(from))
                .sorted(Comparator.comparing(ContextBand::firstDay).thenComparing(ContextBand::label))
                .map(ContextBand::label)
                .limit(MAX_CONTEXT_LINES)
                .toList();
    }
}
