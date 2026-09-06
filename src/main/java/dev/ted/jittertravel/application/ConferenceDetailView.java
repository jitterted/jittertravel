package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.ZonedTimestamp;

/**
 * One conference in full, for the OWNER-only page at {@code /conferences/{id}} — the place Ted goes
 * to re-read what he already recorded, rather than to decide or to act.
 * <p>
 * <strong>This is a second read model over the same events, not a widened {@link ConferenceView}.</strong>
 * The dashboard's row and the iCal feed's CFP reminder both read {@code ConferenceView}, and this
 * page needs one field neither of them may carry — see {@code basis} below. Giving that field to
 * {@code ConferenceView} would put it on every surface that record already reaches; giving it its
 * own record keeps the blast radius to the one page whose route is gated at OWNER
 * ({@code docs/ConferenceDetailAndChangePlan.md} D2).
 * <p>
 * <strong>{@code basis} is publishable here and nowhere else.</strong>
 * {@link AttendanceBasis} is on CLAUDE.md's private list because it re-states the submission
 * outcome: "going because a talk was accepted" is the acceptance, said again. This page is behind
 * {@code hasRole("OWNER")} in {@code SecurityConfig}, which is what makes it showable — so if this
 * record is ever reused by anything reachable at a lower tier, the field has to come off first.
 * {@code null} when Ted has never confirmed attendance, which is every conference he is merely
 * watching, and also the older ones committed before confirmations carried a basis at all.
 * <p>
 * {@code cfpClosesOn} is {@code null} when no CFP has been recorded, exactly as on
 * {@link ConferenceView}, and the two absences it does <em>not</em> distinguish are the page's job:
 * "no CFP recorded" and "this conference has no CFP" are different sentences, and {@code format} is
 * what tells them apart (D3).
 * <p>
 * {@code infoUrl} is the conference's own public page, {@code ""} when none was recorded.
 */
public record ConferenceDetailView(
        ConferenceId conferenceId,
        String name,
        String venueName,
        Address venueAddress,
        ZonedTimestamp startDate,
        ZonedTimestamp endDate,
        AttendanceCommitment commitment,
        AttendanceBasis basis,
        boolean speaking,
        SpeakingStatus speakingStatus,
        ZonedTimestamp cfpClosesOn,
        String cfpSubmissionUrl,
        ConferenceFormat format,
        String infoUrl
) {
    public String city() { return venueAddress.city(); }

    public String country() { return venueAddress.country(); }
}
