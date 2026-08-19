package dev.ted.jittertravel.domain;

import java.util.Locale;

/**
 * How a conference forms its program — intrinsic to the conference and known when it is entered, so
 * it rides on {@link ConferenceTentativelyPlanned} rather than a separate "what happened" event
 * (nothing happened; an open-space conference simply <em>is</em> open-space).
 *
 * <p>The value answers the one question "how do you get on the program," and the speaking pipeline
 * downstream branches on it: whether there is a CFP to submit to at all, and what a rejection means
 * for attendance. See {@code docs/ConferenceSubmissionTrackingPlan.md}.
 */
public enum ConferenceFormat {
    /** Open CFP; attend regardless of the submission outcome (dev2next, ExploreDDD, J-Fall). */
    CALL_FOR_PAPERS("Call for Papers"),
    /** Acceptance gates attendance — a rejection drops the conference (PLoP writers' workshop). */
    ACCEPTANCE_REQUIRED("Acceptance Required"),
    /** No CFP; sessions are chosen on the day (SoCraTes open-space). */
    OPEN_SPACE("Open Space");

    private final String label;

    ConferenceFormat(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Resolve a form value (enum name, case-insensitive) to a format. A blank/absent or
     * unrecognized value falls back to {@link #CALL_FOR_PAPERS} — the safe default that offers the
     * CFP action rather than silently hiding it, and the same value the read-time upcaster injects
     * into legacy payloads.
     */
    public static ConferenceFormat fromParam(String value) {
        if (value == null || value.isBlank()) {
            return CALL_FOR_PAPERS;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return CALL_FOR_PAPERS;
        }
    }
}
