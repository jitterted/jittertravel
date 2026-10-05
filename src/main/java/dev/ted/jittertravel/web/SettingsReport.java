package dev.ted.jittertravel.web;

import java.util.List;

/**
 * What the running app is actually using, grouped for the OWNER-only settings page. Presentation
 * only: every string is already worded, so the template just loops. The approved layout is option A
 * of https://claude.ai/artifact/7QRKoUEwoq8v9JWgzUzqrF, and the {@code /admin} card for it goes amber
 * when {@link #problems()} is above zero, as the "Schedule problems" card does.
 * <p>
 * <strong>A problem is something that is wrong now, not something that is merely not configured.</strong>
 * An optional feature that is off (no calendar token, no flight-lookup key) is a state, and shown as
 * one, because amber that fires for a deliberate choice stops being read. Wrong now means: the switch
 * is on but the app cannot send, or the last test email failed.
 */
public record SettingsReport(List<Group> groups) {

    public SettingsReport {
        groups = List.copyOf(groups);
    }

    public enum Level {
        OK("ok"), PROBLEM("warn"), NEUTRAL("idle");

        private final String css;

        Level(String css) {
            this.css = css;
        }

        public String css() {
            return css;
        }
    }

    /**
     * One titled card of rows. {@code pill} is the group's verdict in a word or two.
     */
    public record Group(String title, String sub, Level level, String pill, List<Row> rows) {

        public Group {
            rows = List.copyOf(rows);
        }

        public String cssClass() {
            return level.css();
        }
    }

    /**
     * One setting. {@code name} is the variable or property it comes from, {@code label} says what it
     * is for in plain words, {@code pill} is its state ("Set", "On", "Not set"), {@code value} is the
     * value when it is not a secret, {@code hint} is the end of a secret that lets two be told apart,
     * and {@code what} says what it does in the state it is in. Any of {@code pill}, {@code value},
     * {@code hint} may be null.
     */
    public record Row(String name, String label, Level level, String pill, String value, String hint,
                      String what, String linkHref, String linkText) {

        /** A row with no link, which is nearly all of them. */
        public Row(String name, String label, Level level, String pill, String value, String hint, String what) {
            this(name, label, level, pill, value, hint, what, null, null);
        }

        public boolean hasLink() {
            return linkHref != null;
        }

        public String cssClass() {
            return level.css();
        }

        public boolean hasPill() {
            return pill != null;
        }

        public boolean hasValue() {
            return value != null;
        }

        public boolean hasHint() {
            return hint != null;
        }
    }

    /** How many rows are wrong now. */
    public int problems() {
        return (int) groups.stream()
                .flatMap(group -> group.rows().stream())
                .filter(row -> row.level() == Level.PROBLEM)
                .count();
    }
}
