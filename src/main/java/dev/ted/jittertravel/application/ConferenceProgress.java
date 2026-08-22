package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.SpeakingStatus;

/**
 * Where one conference stands on both axes at once — how committed Ted is, where his talk is, and
 * therefore whether he is speaking. Derived by folding that conference's own events; nothing here
 * is stored on any of them.
 *
 * <p><strong>Why this is shared when the projectors' folds are not.</strong> Three read models now
 * need these answers — the dashboard, the owner calendar and the public calendar — and they are
 * <em>rules</em>, not rendering: that an acceptance commits attendance, that a rejection drops a
 * conference where acceptance was the way in, that an invitation commits nothing. Written out three
 * times they would be three chances to disagree, and one of the three is the anonymous calendar,
 * where disagreeing means leaking. Each projector still writes its own switch over the event stream
 * and builds its own view — this only answers the questions.
 *
 * <p>The three couplings between the axes all live here, and they are folds rather than extra
 * events, so they replay and they reverse if the event that produced them is superseded.
 *
 * @param confirmationNamedSpeaking whether the last {@code ConferenceAttendanceConfirmed} gave a
 *                                  speaking reason. <strong>Private</strong>: which speaking basis
 *                                  applies is submission status, so it lives here and is answered
 *                                  only as {@link #speaking()} — never carried onto a view.
 */
public record ConferenceProgress(
        ConferenceFormat format,
        AttendanceCommitment commitment,
        SpeakingStatus speakingStatus,
        boolean confirmationNamedSpeaking
) {

    /** A conference just put on the watch list: no commitment, and no speaking evidence either way. */
    public static ConferenceProgress planned(ConferenceFormat format) {
        return new ConferenceProgress(format, AttendanceCommitment.WATCHING,
                                      SpeakingStatus.NOT_SPEAKING, false);
    }

    public ConferenceProgress submitted() {
        return movedTo(SpeakingStatus.SUBMITTED, commitment);
    }

    /**
     * <strong>The auto-commit.</strong> An acceptance makes Ted GOING on its own, with no
     * confirmation event anywhere: submitting the talk was already the opt-in, so the acceptance
     * completes a decision rather than posing a new one.
     */
    public ConferenceProgress accepted() {
        return movedTo(SpeakingStatus.ACCEPTED, AttendanceCommitment.GOING);
    }

    /**
     * <strong>The auto-drop.</strong> Where acceptance was the way in, a rejection takes the
     * conference with it — there is no going anyway. Everywhere else the same event leaves the
     * conference merely watched, with a decision to make.
     */
    public ConferenceProgress rejected() {
        return movedTo(SpeakingStatus.REJECTED,
                       format == ConferenceFormat.ACCEPTANCE_REQUIRED
                               ? AttendanceCommitment.NOT_GOING
                               : commitment);
    }

    /** Pulling a talk moves one axis only: a conference Ted committed to stays committed. */
    public ConferenceProgress withdrawn() {
        return movedTo(SpeakingStatus.WITHDRAWN, commitment);
    }

    /**
     * An invitation is an offer, so it commits nothing. That is the whole difference from an
     * acceptance, and it is why an unanswered invitation stays off the public calendar.
     */
    public ConferenceProgress invited() {
        return movedTo(SpeakingStatus.INVITED, commitment);
    }

    public ConferenceProgress confirmed(AttendanceBasis basis) {
        return new ConferenceProgress(format, AttendanceCommitment.GOING, speakingStatus,
                                      speakingBasis(basis));
    }

    public ConferenceProgress declined() {
        return movedTo(speakingStatus, AttendanceCommitment.NOT_GOING);
    }

    /** Whether this conference has left every calendar: Ted declined, or a rejection dropped it. */
    public boolean dropped() {
        return commitment == AttendanceCommitment.NOT_GOING;
    }

    /**
     * Whether Ted speaks here.
     * <p>
     * <strong>The stream wins wherever it has spoken</strong>, and the basis is the fallback for
     * conferences recorded before these events existed. Exhaustive, so a new
     * {@link SpeakingStatus} cannot be added without deciding whether it counts as speaking.
     */
    public boolean speaking() {
        return switch (speakingStatus) {
            // The talk is in the program. Nothing else is consulted, and no confirmation is needed
            // — being accepted is what made him GOING in the first place.
            case ACCEPTED -> true;
            // The stream has spoken, and it said no talk: waiting to hear, turned down, or pulled.
            // A basis claiming otherwise is a stale manual annotation and loses.
            case SUBMITTED, REJECTED, WITHDRAWN -> false;
            // An offer he has taken up. Going on a bought ticket after an invitation is attending,
            // not speaking, so the basis is what separates the two.
            case INVITED -> committedOnASpeakingBasis();
            // The stream is silent, which is every conference recorded before these events
            // existed. Here the basis is the only evidence there is.
            case NOT_SPEAKING -> committedOnASpeakingBasis();
        };
    }

    private boolean committedOnASpeakingBasis() {
        return commitment == AttendanceCommitment.GOING && confirmationNamedSpeaking;
    }

    /**
     * The partition {@link AttendanceBasis}'s three values were chosen for: two speaking bases and
     * one that is not. Exhaustive, so a fourth basis cannot be added without deciding which side of
     * the line it falls on.
     */
    private boolean speakingBasis(AttendanceBasis basis) {
        return switch (basis) {
            case SPEAKING_ACCEPTED, SPEAKING_INVITED -> true;
            case TICKET_PURCHASED -> false;
        };
    }

    private ConferenceProgress movedTo(SpeakingStatus status, AttendanceCommitment newCommitment) {
        return new ConferenceProgress(format, newCommitment, status, confirmationNamedSpeaking);
    }
}
