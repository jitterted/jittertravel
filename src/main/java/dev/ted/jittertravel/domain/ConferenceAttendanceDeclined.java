package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * Ted has decided not to attend a conference he had planned. This is <em>his</em> decision — kept
 * deliberately distinct from {@link ConferenceCancelled} (the organizers cancelled the event) and
 * from a talk rejection. Conflating "I changed my mind" with "the organizers cancelled" was the
 * exact merge {@code docs/ConferenceSubmissionTrackingPlan.md} forbids: the two are different facts
 * with different downstream meaning, so they get different events.
 * <p>
 * Today a declined conference leaves every read model (calendar, itinerary, schedule-problems,
 * tentative list), the same way {@link ConferenceCancelled} does. The plan's eventual "keep it on
 * the list behind a dropped toggle" behaviour is a read-model change replayable from this same
 * event — this event carries the durable, correctly-labelled fact regardless.
 *
 * @param reason     free text; may be blank. Never rendered on the anonymous calendar — the
 *                   conference is removed entirely, so no field of this event reaches a view.
 * @param declinedOn the moment Ted declined, captured at the boundary (external-inputs rule).
 */
public record ConferenceAttendanceDeclined(
        ConferenceId conferenceId,
        String reason,
        Instant declinedOn
) implements Event {
    public ConferenceAttendanceDeclined {
        if (reason == null) {
            reason = "";
        }
    }
}
