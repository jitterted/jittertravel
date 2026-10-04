package dev.ted.jittertravel.web;

import java.util.List;

/**
 * A temporary setup task shown in the left column of {@code /admin} until it is finished and its
 * declaration is deleted. Presentation only: every string is already worded, so the template just
 * loops. The approved layout is the mockup at https://claude.ai/artifact/Xu3i63jGMKTdCbWKVqedxA
 * (placement X).
 * <p>
 * The steps tick themselves from what the running app can see, so nobody has to remember to tick
 * them. When every step is done the checklist asks to be deleted: it says so in words, and carries a
 * sentence to hand to Claude.
 */
public record SetupChecklist(
        String title,
        String subtitle,
        String count,
        boolean finished,
        List<Step> steps,
        String finishedHeadline,
        String finishedAdvice,
        String finishedHandoff,
        boolean highlightFinished
) {

    public SetupChecklist {
        steps = List.copyOf(steps);
    }

    public enum State { DONE, TODO, FAILED }

    /**
     * One row, worded as two things a reader needs (Ted, 2026-10-05): what is true <em>now</em>
     * ({@code what} as the headline, {@code why} as the explanation, written so it means something
     * to someone who has not read the code) and what to <em>do</em> about it ({@code next}). A label
     * like "Switch: off" is neither.
     * <p>
     * {@code why} and {@code next} are null when the row is shown compactly (a finished checklist).
     * A row with {@code buttonLabel} null has no button. {@code highlight} plays the one-shot flash
     * that makes a fresh result unmissable.
     */
    public record Step(
            State state,
            int number,
            String what,
            String why,
            String next,
            String buttonLabel,
            boolean buttonSecondary,
            String buttonAction,
            boolean highlight
    ) {

        public String mark() {
            return switch (state) {
                case DONE -> "✓";
                case FAILED -> "!";
                case TODO -> String.valueOf(number);
            };
        }

        public String cssClass() {
            return switch (state) {
                case DONE -> "ok";
                case FAILED -> "warn";
                case TODO -> "idle";
            } + (highlight ? " flash" : "");
        }

        public boolean hasWhy() {
            return why != null;
        }

        public boolean hasNext() {
            return next != null;
        }

        public boolean hasButton() {
            return buttonLabel != null;
        }
    }
}
