package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryChangeEvaluation;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.ItineraryChangeKind;
import dev.ted.jittertravel.domain.ItineraryChangePlan;
import dev.ted.jittertravel.domain.ItineraryChangePlan.Kind;
import dev.ted.jittertravel.domain.ItineraryChangePlan.LegDiff;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.web.ItineraryPreview.Before;
import dev.ted.jittertravel.web.ItineraryPreview.Row;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link ItineraryChangeEvaluation} in the change page's words, as the approved mockup draws it
 * ({@code docs/FlightItineraryRebookingPlan.md}, "Approved"): the diff of what applying records, one
 * line counting it, and the button naming what will be recorded.
 * <p>
 * Problems land where they are fixed, and are counted together at the top: a missing "Who changed
 * it?" under its fieldset, an email that could not be read or is for another code under the text
 * box, an unknown airport under its zone picker, a refused leg on its own row.
 * <p>
 * Rows are in departure order and numbered by their place in the email, so "Overlaps flight 2 of
 * this itinerary" names the row marked 2. A leg the email does not list has no number: a dropped
 * one, or a flown one left alone (D6).
 */
public record ItineraryChangePreview(
        String parseProblem,
        String kindProblem,
        List<String> unresolvedAirports,
        List<Row> rows,
        String summary,
        boolean nothingToChange,
        boolean applicable,
        String applyLabel
) {

    static final String PICK_WHO = "Pick who changed it";

    /** Before any preview: nothing to say yet, and no problem to count. */
    static final ItineraryChangePreview NONE =
            new ItineraryChangePreview("", "", List.of(), List.of(), "", false, false, "");

    public static ItineraryChangePreview from(ItineraryChangeEvaluation evaluation) {
        ItineraryChangePlan plan = evaluation.plan();
        return new ItineraryChangePreview(
                String.join("; ", evaluation.parseProblems()),
                evaluation.kindMissing() ? PICK_WHO : "",
                evaluation.unresolvedAirports().stream().map(AirportCode::code).toList(),
                plan == null ? List.of() : plan.diffs().stream().map(ItineraryChangePreview::row).toList(),
                plan == null || !plan.hasChanges() ? "" : summary(plan, evaluation.kind()),
                evaluation.nothingToChange(),
                evaluation.applicable(),
                evaluation.kindMissing() ? "" : applyLabel(evaluation.kind()));
    }

    public boolean planned() {
        return !rows.isEmpty();
    }

    /** Every problem on the page: who changed it, the paste, each unknown airport, each refused leg. */
    public int problemCount() {
        return (kindProblem.isEmpty() ? 0 : 1)
               + (parseProblem.isEmpty() ? 0 : 1)
               + unresolvedAirports.size()
               + (int) rows.stream().filter(Row::hasProblem).count();
    }

    public String problemSummary() {
        return problemCount() == 0 ? "" : FormErrors.countSentence(problemCount());
    }

    /** "Recorded as your rebooking: 1 leg added, 2 dropped, 2 unchanged." Only the kinds present. */
    private static String summary(ItineraryChangePlan plan, ItineraryChangeKind kind) {
        List<String> parts = new ArrayList<>();
        addCount(parts, plan.count(Kind.ADDED), "added");
        addCount(parts, plan.count(Kind.CHANGED), "moved");
        addCount(parts, plan.count(Kind.REMOVED), "dropped");
        addCount(parts, plan.count(Kind.UNCHANGED), "unchanged");
        String counted = String.join(", ", parts) + ".";
        return kind == null ? counted : "Recorded as " + recordedAs(kind) + ": " + counted;
    }

    /** "1 leg added", then "2 dropped": the noun is said once, on the first count. */
    private static void addCount(List<String> parts, int count, String what) {
        if (count == 0) {
            return;
        }
        String noun = parts.isEmpty() ? (count == 1 ? " leg " : " legs ") : " ";
        parts.add(count + noun + what);
    }

    private static String recordedAs(ItineraryChangeKind kind) {
        return switch (kind) {
            case SCHEDULE_CHANGE -> "the airline's schedule change";
            case REBOOKING -> "your rebooking";
        };
    }

    private static String applyLabel(ItineraryChangeKind kind) {
        return switch (kind) {
            case SCHEDULE_CHANGE -> "Record schedule change";
            case REBOOKING -> "Record rebooking";
        };
    }

    private static Row row(LegDiff diff) {
        ItineraryLeg shown = diff.effective();
        return ItineraryPreview.row(diff.pasteNumber(), shown.flightNumber(),
                shown.departureAirport(), shown.arrivalAirport(),
                shown.departureDateTime().localDateTime(), shown.arrivalDateTime().localDateTime(),
                diff.refusal(), statusOf(diff.kind()), before(diff));
    }

    private static String statusOf(Kind kind) {
        return switch (kind) {
            case UNCHANGED -> "Unchanged";
            case CHANGED -> "Moved";
            case ADDED -> "Added";
            case REMOVED -> "Dropped";
        };
    }

    /** What a moved leg used to be, only for the parts that differ. */
    private static Before before(LegDiff diff) {
        if (diff.kind() != Kind.CHANGED) {
            return Before.NONE;
        }
        ItineraryLeg before = diff.before();
        ItineraryLeg after = diff.after();
        boolean routeDiffers = !before.departureAirport().equals(after.departureAirport())
                               || !before.arrivalAirport().equals(after.arrivalAirport());
        boolean departsDiffers = !before.departureDateTime().utc().equals(after.departureDateTime().utc());
        boolean arrivesDiffers = !before.arrivalDateTime().utc().equals(after.arrivalDateTime().utc());
        return new Before(
                before.flightNumber().equals(after.flightNumber()) ? "" : before.flightNumber(),
                routeDiffers ? before.departureAirport().code() + "→" + before.arrivalAirport().code() : "",
                departsDiffers ? ItineraryPreview.WHEN.format(before.departureDateTime().localDateTime()) : "",
                arrivesDiffers ? ItineraryPreview.WHEN.format(before.arrivalDateTime().localDateTime()) : "");
    }
}
