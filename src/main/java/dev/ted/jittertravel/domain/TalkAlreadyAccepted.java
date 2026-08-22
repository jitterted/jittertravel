package dev.ted.jittertravel.domain;

/**
 * Thrown when a talk is submitted to a conference whose talk has already been accepted. There is
 * nothing left to submit — and, because {@link SpeakingStatus} takes the last event, recording one
 * would quietly un-accept the talk that is already in the program.
 */
public class TalkAlreadyAccepted extends RuntimeException {
    public TalkAlreadyAccepted(String message) {
        super(message);
    }
}
