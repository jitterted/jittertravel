package dev.ted.jittertravel.domain;

/**
 * The organizers moved a conference: it now runs from {@code startDate} to {@code endDate}.
 * DevNexus 2027 moving from April 5–7 back a week to March 29–31 is the case that prompted it
 * (Ted, 2026-09-22).
 * <p>
 * <strong>The dates and nothing else</strong> — H1 in {@code EventSourcingRulesHeuristics.md}.
 * The full-snapshot {@code ConferenceChanged} that {@code docs/ConferenceDetailAndChangePlan.md}
 * D4 specified would carry {@code format} and {@code infoUrl} too, and a change form that forgot
 * either would silently reset it for every viewer. Naming only the fact that changed makes that
 * trap unrepresentable rather than tested-for.
 * <p>
 * <strong>Both dates are in the zone the conference was planned in</strong>, and the command is
 * what guarantees it: the zone comes from the stream, never from the form. That is what keeps a
 * recorded {@link CfpOpened#closesOn()} — stamped in that same zone — meaning what it meant.
 */
public record ConferenceDatesChanged(
        ConferenceId conferenceId,
        ZonedTimestamp startDate,
        ZonedTimestamp endDate
) implements Event {
}
