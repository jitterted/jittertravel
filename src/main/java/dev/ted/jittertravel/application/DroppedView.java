package dev.ted.jittertravel.application;

import java.util.Locale;

/**
 * Whether the conference dashboard shows the conferences Ted dropped. Hidden by default: they are
 * a record to look back on, not work to do.
 * <p>
 * <strong>A separate parameter from {@link TimeView}, deliberately</strong> — {@code ?dropped=show}
 * alongside {@code ?filter=all}, never a third value crammed into {@code filter}. The two ask
 * unrelated questions (when, versus whether Ted is going), and folding them together yields a
 * parameter whose values are the cross-product and have to be enumerated one by one. Keeping them
 * apart also leaves {@code TimeFilterToggle} untouched, so the shared FUTURE/ALL convention and the
 * test that enforces it are unaffected.
 */
public enum DroppedView {
    /** The default: dropped conferences are left out of the dashboard entirely. */
    HIDE {
        @Override
        public boolean includes(AttendanceCommitment commitment) {
            return commitment != AttendanceCommitment.NOT_GOING;
        }
    },
    /** Everything, dropped conferences included, each under the dashboard's dropped heading. */
    SHOW {
        @Override
        public boolean includes(AttendanceCommitment commitment) {
            return true;
        }
    };

    /** Whether a conference at this commitment level belongs in this view. */
    public abstract boolean includes(AttendanceCommitment commitment);

    /**
     * Resolves a request parameter, falling back to HIDE when the value is absent or unrecognized —
     * the same shape as {@link TimeView#fromParam}, and the same reason for it: a hand-edited URL
     * gets the default rather than an error page.
     */
    public static DroppedView fromParam(String value) {
        if (value == null) {
            return HIDE;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return HIDE;
        }
    }
}
