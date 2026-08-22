package dev.ted.jittertravel.domain;

/**
 * Thrown when an outcome — {@link AcceptTalkCommand} or {@link RejectTalkCommand} — is recorded
 * against a conference where no talk was ever submitted. Organizers can only accept or reject
 * something they were sent, so the fact would be untrue rather than merely redundant.
 * <p>
 * An <em>invitation</em> is not a submission and cannot be accepted this way either: saying yes to
 * one is {@link ConfirmConferenceAttendanceCommand} on the attendance axis.
 */
public class NoTalkToDecide extends RuntimeException {
    public NoTalkToDecide(String message) {
        super(message);
    }
}
