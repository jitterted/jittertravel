package dev.ted.jittertravel.domain;

/**
 * Thrown when something CFP-shaped is recorded against a conference whose
 * {@link ConferenceFormat} is {@code OPEN_SPACE} — submitting a talk to it, or recording a closing
 * deadline for it. An open-space conference chooses its sessions on the day, so there is no call
 * for papers to submit to and no deadline to miss.
 * <p>
 * Not reachable through the dashboard, which offers neither action on an open-space row. It guards
 * the direct POST, and it is the reason the format has to reach the decision context at all.
 */
public class ConferenceHasNoCfp extends RuntimeException {
    public ConferenceHasNoCfp(String message) {
        super(message);
    }
}
