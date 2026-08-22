package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleContext;
import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.ScheduleProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Turning the {@code ?problem=} reference on a fix link back into the banner the landing page
 * shows. The report is the only source: a key that matches nothing yields nothing, and the form
 * renders exactly as it does when reached from the index nav card.
 */
@ExtendWith(MockitoExtension.class)
class ProblemContextLookupTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    private static final ScheduleProblem.MissingHotel MISSING_BED = new ScheduleProblem.MissingHotel(
            "Denver", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18), "dev2next");

    @Mock
    ScheduleGapProjector scheduleGapProjector;

    @Test
    void aReferenceResolvesToTheProblemInTheReportsOwnWords() {
        given(scheduleGapProjector.problems(NOW)).willReturn(List.of(MISSING_BED));
        given(scheduleGapProjector.context()).willReturn(List.of());

        ProblemContextView banner = bannerFor(MISSING_BED, FixOrigin.PROBLEM_CALENDAR).orElseThrow();

        assertThat(banner.title()).isEqualTo("No hotel — Denver");
        assertThat(banner.detail()).isEqualTo("4 nights — dev2next");
        assertThat(banner.markerModifier())
                .as("the banner wears the kind the band Ted clicked wears")
                .isEqualTo("bed");
    }

    @Test
    void theOriginDecidesWhereBackGoes() {
        given(scheduleGapProjector.problems(NOW)).willReturn(List.of(MISSING_BED));
        given(scheduleGapProjector.context()).willReturn(List.of());

        assertThat(bannerFor(MISSING_BED, FixOrigin.ITINERARY).orElseThrow())
                .extracting(ProblemContextView::backLabel, ProblemContextView::backHref)
                .containsExactly("Back to itinerary", "/itinerary");
    }

    /**
     * The causes are the whole point: a bed is missing <em>because</em> a conference runs those
     * days and a flight lands the morning it starts.
     */
    @Test
    void theBannerNamesWhatTheScheduleHoldsAroundTheProblem() {
        given(scheduleGapProjector.problems(NOW)).willReturn(List.of(MISSING_BED));
        given(scheduleGapProjector.context()).willReturn(List.of(
                new ScheduleContext.Conference("dev2next", "Denver",
                        LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18)),
                new ScheduleContext.Travel("San Francisco", "Denver",
                        LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14))));

        assertThat(bannerFor(MISSING_BED, FixOrigin.PROBLEM_LIST).orElseThrow().contextLines())
                .containsExactly("San Francisco → Denver · Sep 14",
                                 "dev2next, Denver · Sep 14–18");
    }

    /**
     * A day either side, so the flight that lands the morning the gap opens still shows — and
     * nothing from the trip a fortnight later, which explains nothing about this bed.
     */
    @Test
    void contextIsClippedToTheProblemsOwnDaysWidenedByOne() {
        given(scheduleGapProjector.problems(NOW)).willReturn(List.of(MISSING_BED));
        given(scheduleGapProjector.context()).willReturn(List.of(
                new ScheduleContext.Stay("Reichshof", "Denver",
                        LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13)),
                new ScheduleContext.Gathering("Denver JUG", "Denver",
                        LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 11)),
                new ScheduleContext.Conference("JCON", "Cologne",
                        LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8))));

        assertThat(bannerFor(MISSING_BED, FixOrigin.PROBLEM_LIST).orElseThrow().contextLines())
                .containsExactly("Reichshof, Denver · Sep 13");
    }

    /** A reminder, not a second report — the Back link is there for the rest. */
    @Test
    void theBannerShowsAtMostFourCauses() {
        given(scheduleGapProjector.problems(NOW)).willReturn(List.of(MISSING_BED));
        given(scheduleGapProjector.context()).willReturn(List.of(
                gatheringOn(14), gatheringOn(15), gatheringOn(16), gatheringOn(17), gatheringOn(18)));

        assertThat(bannerFor(MISSING_BED, FixOrigin.PROBLEM_LIST).orElseThrow().contextLines())
                .hasSize(4);
    }

    @Test
    void anUnknownKeyExplainsNothing() {
        given(scheduleGapProjector.problems(NOW)).willReturn(List.of(MISSING_BED));

        assertThat(lookup().forKey("hotel|Atlantis|2026-09-14|2026-09-18",
                FixOrigin.PROBLEM_CALENDAR, NOW))
                .isEmpty();
    }

    /**
     * The link is stale in exactly the way the report says it is: fix the booking in another tab,
     * come back, and there is no banner rather than a sentence describing a solved problem.
     */
    @Test
    void aProblemThatHasSinceBeenFixedExplainsNothing() {
        given(scheduleGapProjector.problems(NOW)).willReturn(List.of());

        assertThat(bannerFor(MISSING_BED, FixOrigin.PROBLEM_CALENDAR)).isEmpty();
    }

    @Test
    void noKeyAtAllExplainsNothingAndAsksTheReportNothing() {
        assertThat(lookup().forKey(null, FixOrigin.PROBLEM_CALENDAR, NOW)).isEmpty();
        assertThat(lookup().forKey("  ", FixOrigin.PROBLEM_CALENDAR, NOW)).isEmpty();
    }

    private Optional<ProblemContextView> bannerFor(ScheduleProblem problem, FixOrigin origin) {
        return lookup().forKey(ProblemRef.of(problem).key(), origin, NOW);
    }

    private ProblemContextLookup lookup() {
        return new ProblemContextLookup(scheduleGapProjector);
    }

    private static ScheduleContext gatheringOn(int day) {
        return new ScheduleContext.Gathering("Dinner " + day, "Denver",
                LocalDate.of(2026, 9, day), LocalDate.of(2026, 9, day));
    }
}
