package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleGapProjector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;

/**
 * Puts the "why you are here" banner on every page a fix link can reach.
 * <p>
 * <strong>Why an advice and not six constructor dependencies</strong> (D6, decided by Ted
 * 2026-08-21). Six controllers are reachable from a {@link ProblemFix} href — book hotel, book
 * flight, book train, plan ground transfer, cancel hotel, clear conflict — and none of them is
 * <em>about</em> schedule problems. Giving each one a lookup it does not otherwise use is the
 * coupling shape Ted rejected for the state-aware nav link; the banner's trigger is a query
 * parameter, not anything about booking a hotel, so it is handled once, here.
 * <p>
 * The cost of that choice is that nothing in {@code BookHotelController} says the banner exists.
 * {@code ProblemContextFragmentConventionTest} is what stops that from being invisible: it fails
 * when a fix target's template does not render the fragment.
 * <p>
 * <strong>Why the dependencies are optional.</strong> A {@code @WebMvcTest} slice instantiates
 * every {@code @ControllerAdvice} it scans, and there are forty-odd slices in this codebase for
 * controllers that have nothing to do with the schedule. Requiring the beans outright would make
 * each of them mint a {@code ScheduleGapProjector} and a {@code Clock} to render an unrelated form.
 * Absent either, there is no banner — the same thing that happens when the key matches nothing, and
 * a page without it is the page we ship today.
 * <p>
 * <strong>The banner prints OWNER-only data</strong> — hotel cities, gathering names, exact arrival
 * times: the content of a report {@code SecurityConfig} gates at OWNER. Every fix target is
 * OWNER-only today, and that is a standing condition, not a one-time check. If one of these routes
 * is ever opened to FAMILY or anonymous, the banner must not render on it.
 */
@ControllerAdvice
public class ProblemContextAdvice {

    private final ProblemContextLookup problemContextLookup;
    private final Clock clock;

    public ProblemContextAdvice(ObjectProvider<ScheduleGapProjector> scheduleGapProjector,
                                ObjectProvider<Clock> clock) {
        ScheduleGapProjector projector = scheduleGapProjector.getIfAvailable();
        this.problemContextLookup = projector == null ? null : new ProblemContextLookup(projector);
        this.clock = clock.getIfAvailable();
    }

    /**
     * {@code null} — no banner — is the normal case: every page reached from the index nav card,
     * the calendar day-menu, or a POST comes through here without a {@code ?problem=}, and renders
     * exactly as it did before.
     */
    @ModelAttribute("problemContext")
    public ProblemContextView problemContext(@RequestParam(required = false) String problem,
                                             @RequestParam(required = false) String from) {
        if (problem == null || problem.isBlank() || problemContextLookup == null || clock == null) {
            return null;
        }
        // now is captured here, at the boundary, and passed inward — the lookup never asks what
        // time it is.
        return problemContextLookup.forKey(problem, FixOrigin.fromParam(from), clock.instant())
                .orElse(null);
    }
}
