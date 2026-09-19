package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.application.TravelLeg;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

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
 * <p>
 * Every href also carries {@code ?problem=} and {@code ?from=} (see {@link #explaining}), which is
 * what lets the landing page show the banner saying <em>why you are here</em> — the prefill puts
 * the right values in the inputs, and the banner is the sentence that made them the right ones.
 */
public record ProblemFix(String label, String href) {

    /**
     * Wall-clock at the leg's own end. Its own constant rather than one shared with
     * {@code ProblemBand}: a band's detail and a link's label are different sentences that
     * happen to agree today, and a shared formatter would be a single-method utility class
     * standing between them.
     */
    private static final DateTimeFormatter LEG_TIME =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /**
     * The fixes for {@code problem}, in the order they should be offered. Empty means the problem
     * has no actionable fix yet — the renderers show the same control greyed with the reason,
     * rather than dropping it (CLAUDE.md: an action that cannot be triggered is disabled, not
     * removed).
     */
    public static List<ProblemFix> forProblem(ScheduleProblem problem, FixOrigin origin) {
        return fixesFor(problem).stream()
                .map(fix -> fix.explaining(problem, origin))
                .toList();
    }

    /**
     * The {@code ?problem=} reference and the {@code ?from=} origin, appended in one place so a new
     * fix cannot forget them: without the reference the landing page cannot say why you are here,
     * and without the origin its Back link guesses at which surface you left.
     * <p>
     * Neither parameter changes where the link goes — every target is an existing OWNER-only GET
     * page, and a query parameter cannot escape a path matcher — so this still adds no
     * {@code SecurityConfig} matcher and no {@code AuthorizationMatrixTest} row.
     */
    private ProblemFix explaining(ScheduleProblem problem, FixOrigin origin) {
        String separator = href.contains("?") ? "&" : "?";
        return new ProblemFix(label, href + separator
                                     + "problem=" + encode(ProblemKey.of(problem).value())
                                     + "&from=" + origin.param());
    }

    private static List<ProblemFix> fixesFor(ScheduleProblem problem) {
        return switch (problem) {
            case ScheduleProblem.MissingHotel missingHotel -> List.of(bookHotel(missingHotel));
            case ScheduleProblem.MissingTravel missingTravel -> travelFixes(missingTravel);
            case ScheduleProblem.DuplicateHotel duplicateHotel -> cancelEachStay(duplicateHotel);
            case ScheduleProblem.OverlappingTravel overlap -> cancelEachLeg(overlap);
            case ScheduleProblem.DifferentCityConflict cityConflict -> List.of(clearConflict(cityConflict));
            // Its two sides are names, cities and times with no ids, and either may be a gathering
            // or a private event — so a link would need a kind+id reference the record does not
            // carry. That is the cause-linking gap: docs/ProblemCauseLinkingPlan.md.
            //
            // MissingTravel above has the same gap for a different reason — it knows the two
            // cities and not which entry raised them — which is why a dinner four miles from the
            // hotel cannot offer "same place as where I'm staying" here, though the page that
            // does it exists (/planned-private-events/{id}/matching-location). That second,
            // sharper case is what the plan was written for.
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
                // Only the date and, appended by explaining() below, the problem reference: the
                // transfer form takes no typed cities, and its endpoint options are flight legs and
                // booked hotels that the date itself brings into range. The two ends *are*
                // preselected as of D16 — from the gap the reference names, server-side, and only
                // when the gap leaves exactly one candidate at that end (GroundTransferPreselection).
                // The earlier "never preselect, one `airport:` value can belong to several legs"
                // (D13) was superseded there, and the hazard it named is why an option's value now
                // carries its leg — see GroundTransferEndpointResolver.airportToken.
                new ProblemFix("Ground transfer", "/plan-ground-transfer?date=" + date));
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

    /**
     * One link per leg, never a single "cancel the redundant one" — which of two overlapping legs
     * is the real one is Ted's call, exactly as it is for two overlapping hotel stays.
     * <p>
     * <strong>The label leads with the departure time</strong> (Ted, 2026-09-06), because the leg's
     * own name does not always distinguish the two. A train's service id is optional on the form,
     * and two <em>duplicate</em> legs entered without one fall back to the same route — two links
     * reading "Cancel Hamburg → Berlin", pointing at different trips, in exactly the situation this
     * detector exists for. The time is always there and always short. (Production's service ids run
     * to 51 characters, so leading with the name would not fit a chip either.)
     * <p>
     * A true exact duplicate — same route, same times, same service id — still produces two
     * identical labels. That is honest: the entries are interchangeable and it does not matter
     * which one goes. Numbering them would imply an order the report does not have.
     * <p>
     * <strong>A flight contributes no link</strong>, because there is no Cancel Flight. The switch
     * is exhaustive over {@link TravelLeg}, so the day one ships the compiler asks for its URL here
     * rather than letting a default arm go on offering nothing.
     */
    private static List<ProblemFix> cancelEachLeg(ScheduleProblem.OverlappingTravel overlap) {
        return Stream.of(overlap.first(), overlap.second())
                .map(ProblemFix::cancelLeg)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<ProblemFix> cancelLeg(ScheduleProblem.OverlappingLeg leg) {
        return cancelPath(leg.leg())
                .map(path -> new ProblemFix(
                        "Cancel " + LEG_TIME.format(leg.departure().atEntryZone())
                        + " \u00b7 " + leg.leg().label(),
                        path));
    }

    private static Optional<String> cancelPath(TravelLeg leg) {
        return switch (leg) {
            case TravelLeg.Train train -> Optional.of("/booked-trains/" + train.id().id() + "/cancel");
            case TravelLeg.Transfer transfer ->
                    Optional.of("/ground-transfers/" + transfer.id().id() + "/cancel");
            // No Cancel Flight yet. Deliberately not an "Edit this flight" link instead: editing
            // does not remove a duplicate, and a link that cannot fix the problem it hangs off is
            // worse than the greyed "no fix yet" control the renderers already show.
            case TravelLeg.Flight ignored -> Optional.empty();
        };
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
