package dev.ted.jittertravel.application;

import java.util.Locale;

public enum TimeView {
    FUTURE,
    ALL;

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