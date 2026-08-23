package dev.ted.jittertravel.domain;

/**
 * Thrown when something CFP-shaped is recorded against a conference whose
 * {@link ConferenceFormat} is {@code OPEN_SPACE} — submitting a talk to it, or recording a closing
 * deadline for it. An open-space conference chooses its sessions on the day, so there is no call
 * for papers to submit to and no deadline to miss.
 * <p>
 * The dashboard offers neither action on an open-space row, so it cannot be reached from there — but
 * the <strong>plan form can reach it</strong>, since that form carries the format radio and the CFP
 * fields together (2026-08-22). {@code ConferencePlanning} therefore throws this itself, before it
 * writes anything, and the controller renders it as a field error; this command's own refusal stays
 * as the backstop for a direct POST, and it is the reason the format has to reach the decision
 * context at all.
 */
public class ConferenceHasNoCfp extends RuntimeException {
    public ConferenceHasNoCfp(String message) {
        super(message);
    }
}
