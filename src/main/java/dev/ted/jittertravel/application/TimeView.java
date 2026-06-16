package dev.ted.jittertravel.application;

import java.time.LocalDateTime;
import java.util.Locale;

public enum TimeView {
    FUTURE {
        @Override
        public boolean includes(TemporalView view, LocalDateTime now) {
            return !view.relevantUntil().isBefore(now);
        }
    },
    ALL {
        @Override
        public boolean includes(TemporalView view, LocalDateTime now) {
            return true;
        }
    };

    /**
     * Whether {@code view} belongs in this filter as of {@code now}. ALL always
     * includes; FUTURE includes only items that have not ended — i.e. whose
     * {@link TemporalView#relevantUntil()} is not before {@code now}.
     */
    public abstract boolean includes(TemporalView view, LocalDateTime now);

    /**
     * Resolves a request parameter to a TimeView, falling back to FUTURE
     * when the value is absent or unrecognized. Case-insensitive.
     */
    public static TimeView fromParam(String value) {
        if (value == null) {
            return FUTURE;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return FUTURE;
        }
    }
}