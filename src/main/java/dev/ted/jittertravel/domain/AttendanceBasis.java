package dev.ted.jittertravel.domain;

/**
 * Why Ted is going to a conference — the fact behind a
 * {@link ConferenceAttendanceConfirmed}. <strong>OWNER-only.</strong> It must never reach a
 * {@code CalendarEntry}: the public calendar shows the collapsed commitment level and nothing more,
 * because "going because a talk was accepted" versus "going because I bought a ticket" is exactly
 * the submission status that {@code docs/archived/ConferenceSubmissionTrackingPlan.md} keeps private.
 *
 * <p>Three values, not four: the dropped {@code ATTENDING_ANYWAY} named the same fact as
 * {@link #TICKET_PURCHASED} (buying a ticket <em>is</em> how "I'll go anyway" happens, Ted
 * 2026-08-19), and the narrative it tried to preserve — going after a rejection — is already in the
 * stream as the preceding rejection event. Read the sequence, not the label.
 *
 * <p>The three partition cleanly into speaking ({@link #SPEAKING_ACCEPTED},
 * {@link #SPEAKING_INVITED}) and not ({@link #TICKET_PURCHASED}), which is the read the slice-4
 * conference speaking badge will need — and, before it, the {@code SPEAKER} marker planned for the
 * OWNER-only {@code /conferences} dashboard, which carries a derived {@code speaking} boolean rather
 * than this enum for the same reason {@code CalendarEntry} carries only a collapsed commitment.
 *
 * <p>No display label here, deliberately: how a basis is worded belongs to the presentation layer
 * (see "Presentation formatting stays out of the domain" in CLAUDE.md). The confirm form spells the
 * three labels out itself.
 */
public enum AttendanceBasis {
    /** A submitted talk was accepted — the speaking path, which auto-commits attendance. */
    SPEAKING_ACCEPTED,
    /** The organizers asked, with no CFP involved. An offer, so it needs Ted's explicit yes. */
    SPEAKING_INVITED,
    /** Going as an attendee: the ticket is bought, whatever happened on the speaking axis. */
    TICKET_PURCHASED
}
