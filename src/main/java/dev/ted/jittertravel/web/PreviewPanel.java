package dev.ted.jittertravel.web;

/**
 * The send panel at the top of the email preview, drawn from the approved mockup
 * (https://claude.ai/artifact/C7mjK5bUm6yY5nDVX1JgWu). Presentation only: every string is already
 * worded, so the template just prints it. {@code next} and {@code buttonLabel} may be null.
 */
public record PreviewPanel(State state, String what, String why, String next, String buttonLabel,
                           boolean buttonSecondary, boolean highlight) {

    public enum State {
        READY("idle", "✉"), SENT("ok", "✓"), FAILED("warn", "!"), CANNOT_SEND("idle", "1");

        private final String css;
        private final String mark;

        State(String css, String mark) {
            this.css = css;
            this.mark = mark;
        }
    }

    public String cssClass() {
        return state.css + (highlight ? " flash" : "");
    }

    public String mark() {
        return state.mark;
    }

    /** A failure is announced as an alert; everything else as a status. */
    public String role() {
        return state == State.FAILED ? "alert" : "status";
    }

    public boolean hasNext() {
        return next != null;
    }

    public boolean hasButton() {
        return buttonLabel != null;
    }
}
