package dev.ted.jittertravel.application;

import java.util.Locale;

/**
 * Whether {@code /booked-flights} and {@code /booked-hotels} show what Ted cancelled. Hidden by
 * default: a cancelled flight or stay is a record to look back on (a travel credit, a refund still
 * owed), not travel to take.
 * <p>
 * A separate parameter from {@link TimeView}, for the reason {@link DroppedView} gives: the two
 * ask unrelated questions, and keeping them apart leaves the shared FUTURE/ALL toggle untouched.
 */
public enum CancelledView {
    /** The default: cancelled rows are left out of the list entirely. */
    HIDE {
        @Override
        public boolean includes(CancellableView view) {
            return !view.cancelled();
        }
    },
    /** Everything, cancelled rows included and marked. */
    SHOW {
        @Override
        public boolean includes(CancellableView view) {
            return true;
        }
    };

    public abstract boolean includes(CancellableView view);

    /** Resolves a request parameter, falling back to HIDE when absent or unrecognized. */
    public static CancelledView fromParam(String value) {
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
