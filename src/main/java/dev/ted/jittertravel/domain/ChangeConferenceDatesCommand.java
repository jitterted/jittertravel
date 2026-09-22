package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;
import java.util.stream.Stream;

/**
 * Records that a conference's organizers moved it, emitting {@link ConferenceDatesChanged}.
 * <p>
 * The dates arrive as the wall-clock Ted reads off the conference's own site, and are zoned here in
 * the zone the conference already has ({@link ChangeConferenceDatesContext#zone()}). The zone is a
 * decision fact rather than a form field because it is a rule, not an input: a dates change never
 * moves a conference to another zone.
 * <p>
 * Two refusals: a conference that is not live (never planned, cancelled, or declined), and an end
 * before the start. <strong>There is deliberately no time gate</strong>, unlike
 * {@link PlanConferenceCommand}: this records something the organizers did, and fixing a past
 * conference's dates is a legitimate correction — the reasoning {@link OpenCfpCommand} gives for a
 * CFP deadline that has already passed. Nor is a recorded CFP deadline compared with the new start;
 * nothing compares the two today (Ted, 2026-09-22).
 */
public record ChangeConferenceDatesCommand(
        ConferenceId conferenceId,
        LocalDateTime startDate,
        LocalDateTime endDate
) implements DomainCommand<ChangeConferenceDatesContext> {

    @Override
    public Stream<ConferenceDatesChanged> execute(ChangeConferenceDatesContext context) {
        if (!context.conferenceExists()) {
            throw new ConferenceNotFound("No conference found to change the dates of: " + conferenceId);
        }
        if (endDate.isBefore(startDate)) {
            throw new InvalidDateRange("End date must be on or after start date");
        }
        return Stream.of(new ConferenceDatesChanged(
                conferenceId,
                ZonedTimestamp.fromLocal(startDate, context.zone()),
                ZonedTimestamp.fromLocal(endDate, context.zone())));
    }
}
