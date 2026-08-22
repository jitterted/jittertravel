package dev.ted.jittertravel.domain;

/**
 * A conference's call for papers is open, and closes at {@code closesOn}.
 * <p>
 * The opening <em>is</em> the event, which is why there is no separate "CFP window recorded" fact
 * carrying both ends: an earlier draft had {@code CfpWindowRecorded(opensOn, closesOn)} and it was
 * renamed here because a window is a shape, not something that happened
 * ({@code docs/ConferenceSubmissionTrackingPlan.md}). Nothing yet needs "watch for this CFP to
 * open" — Ted records a CFP once it is already open — so the opening date has nowhere to be used
 * and is not stored.
 * <p>
 * <strong>The organizers opened it; Ted is recording it.</strong> The event names what happened in
 * the world, not who typed it, exactly as {@link ConferenceCancelled} does.
 * <p>
 * <strong>{@code closesOn} is the deadline, and it is the point of the event.</strong> It is
 * structurally a hotel's {@code cancelBy} — a moment not to miss — and it drives the same machinery:
 * a VEVENT on the private iCal feed with alarms before it, fired locally by the device. So it is a
 * {@link ZonedTimestamp} rather than a date: an alarm needs an instant.
 * <p>
 * <strong>Which zone.</strong> The conference's own venue zone, taken from the dates already stored
 * on {@link ConferencePlanned} rather than resolved again — one conference, one zone, and a second
 * resolution could only disagree with the first. Real CFPs often close "anywhere on earth" (UTC-12),
 * which is <em>later</em> than any venue zone, so this errs toward treating the deadline as earlier
 * than it is: the alarms fire early rather than late, which is the safe direction for a deadline.
 * <p>
 * Recording a CFP twice is how a moved deadline is corrected; the fold takes the last one.
 * <p>
 * <strong>OWNER-only.</strong> CFP dates are on the private list in CLAUDE.md — they say Ted is
 * considering a conference and has not committed — so no field of this event may reach a
 * {@code CalendarEntry}. {@code PublicCalendarProjector} does not read it.
 */
public record CfpOpened(
        ConferenceId conferenceId,
        ZonedTimestamp closesOn
) implements Event {

    public CfpOpened {
        if (closesOn == null) {
            throw new IllegalArgumentException(
                    "closesOn must not be null — the closing deadline is the whole point of "
                    + "recording that a CFP is open");
        }
    }
}
