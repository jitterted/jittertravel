package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleProblem;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * One way to fix one {@link ScheduleProblem}: the words on the link, and where it goes.
 * <p>
 * {@link #forProblem} is the single mapping, read by <strong>both</strong> views — the list card
 * (`ScheduleProblemsRenderer`) and the calendar band (`ProblemCalendarRenderer`) — so the two can
 * never offer different answers to the same problem. It is an exhaustive switch over the sealed
 * {@code ScheduleProblem}, like {@code ProblemBand.from}, so a new problem type cannot be added
 * without deciding how it gets fixed.
 * <p>
 * Every href points at an <strong>existing</strong> GET page with query prefill; nothing here POSTs
 * and nothing here is a new route. That is why this slice adds no {@code SecurityConfig} matcher
 * and no {@code AuthorizationMatrixTest} row: the matrix is keyed by path, a query parameter cannot
 * escape a path matcher, and every target is already an OWNER surface.
 */
public record ProblemFix(String label, String href) {

    /**
     * The fixes for {@code problem}, in the order they should be offered. Empty means the problem
     * has no actionable fix yet — the renderers show the same control greyed with the reason,
     * rather than dropping it (CLAUDE.md: an action that cannot be triggered is disabled, not
     * removed).
     */
    public static List<ProblemFix> forProblem(ScheduleProblem problem) {
        return switch (problem) {
            case ScheduleProblem.MissingHotel missingHotel -> List.of(bookHotel(missingHotel));
            case ScheduleProblem.MissingTravel missingTravel -> travelFixes(missingTravel);
            case ScheduleProblem.DuplicateHotel duplicateHotel -> cancelEachStay(duplicateHotel);
            case ScheduleProblem.DifferentCityConflict cityConflict -> List.of(clearConflict(cityConflict));
            // Its two sides are names, cities and times with no ids, and either may be a gathering
            // or a private event — so a link would need a kind+id reference the record does not
            // carry. That is the cause-linking gap, tracked with slice 4.
            case ScheduleProblem.SchedulingConflict ignored -> List.of();
        };
    }

    private static ProblemFix bookHotel(ScheduleProblem.MissingHotel missingHotel) {
        // No zone: the night sweep's location map is keyed city-only, so there is none to carry.
        // The form's own default clock times (15:00 / 11:00) fill the rest. See F5 in the plan.
        return new ProblemFix("Book hotel",
                "/book-hotel?city=" + encode(missingHotel.city())
                + "&checkIn=" + missingHotel.checkIn()
                + "&checkOut=" + missingHotel.checkOut());
    }

    /**
     * A gap has three answers, and which one is right is Ted's call, not a guess: flight first
     * because it is the common case in his data, then train (a Frankfurt→Leipzig gap is a train,
     * and guessing wrong costs a page load), then the ground transfer that covers the short hop no
     * booking exists for.
     */
    private static List<ProblemFix> travelFixes(ScheduleProblem.MissingTravel missingTravel) {
        String cities = "fromCity=" + encode(missingTravel.fromCity())
                        + "&toCity=" + encode(missingTravel.toCity());
        // The day the traveller has to have moved by, read in the departure's own zone.
        String date = missingTravel.nextDepartureAt().localDateTime().toLocalDate().toString();
        return List.of(
                new ProblemFix("Book flight", "/book-flight?" + cities + "&date=" + date),
                new ProblemFix("Book train", "/book-train?" + cities + "&date=" + date),
                // Only the date: the transfer form takes no typed cities, and its endpoint options
                // are flight legs and booked hotels that the date itself brings into range. It is
                // deliberately not preselected — one `airport:` value can belong to several legs,
                // so a preselection would silently pick a trip. See docs/archived/GroundTransferPlan.md D13.
                new ProblemFix("Add ground transfer", "/plan-ground-transfer?date=" + date));
    }

    /**
     * One link per stay, never a single "cancel the redundant one": which room to keep is Ted's
     * call, and the booking intent shown beside them is what informs it. The target is the existing
     * gated cancel page with its own confirmation, so the link navigates — the report never POSTs.
     */
    private static List<ProblemFix> cancelEachStay(ScheduleProblem.DuplicateHotel duplicateHotel) {
        List<ProblemFix> fixes = new ArrayList<>();
        for (ScheduleProblem.DuplicateStay stay : duplicateHotel.stays()) {
            fixes.add(new ProblemFix("Cancel \"" + stay.hotelName() + "\"",
                    "/booked-hotels/" + stay.bookingId().id() + "/cancel"));
        }
        return List.copyOf(fixes);
    }

    /** The existing URL, moved here unchanged so the band and the card demonstrably share it. */
    private static ProblemFix clearConflict(ScheduleProblem.DifferentCityConflict conflict) {
        return new ProblemFix("Clear this conflict",
                "/clear-conflict"
                + "?gatheringId=" + conflict.gatheringId().id()
                + "&conferenceId=" + conflict.conferenceId().id()
                + "&gatheringName=" + encode(conflict.gatheringName())
                + "&gatheringCity=" + encode(conflict.gatheringCity())
                + "&conferenceName=" + encode(conflict.conferenceName())
                + "&conferenceCity=" + encode(conflict.conferenceCity())
                + "&date=" + conflict.date());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
