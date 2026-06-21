package dev.ted.jittertravel.domain;

import java.time.ZoneId;
import java.util.Locale;

/**
 * The short list of time zones a user can pick from a form when we cannot derive the zone from a
 * location (e.g. a manually-entered flight, which carries only airport codes). Deliberately small —
 * the destinations the owner actually travels to (USA, Canada, UK, Western Europe). When none fits,
 * the configured default zone is used instead; a wrong pick is correctable by re-editing.
 */
public enum CommonZone {
    US_EASTERN("US Eastern", "America/New_York"),
    US_CENTRAL("US Central", "America/Chicago"),
    US_MOUNTAIN("US Mountain", "America/Denver"),
    US_PACIFIC("US Pacific", "America/Los_Angeles"),
    CANADA_EASTERN("Canada Eastern", "America/Toronto"),
    CANADA_PACIFIC("Canada Pacific", "America/Vancouver"),
    UK("United Kingdom", "Europe/London"),
    WESTERN_EUROPE("Western Europe", "Europe/Paris");

    private final String label;
    private final ZoneId zoneId;

    CommonZone(String label, String zoneId) {
        this.label = label;
        this.zoneId = ZoneId.of(zoneId);
    }

    public String label() {
        return label;
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    /** Resolve a request-parameter value (enum name, case-insensitive) to a CommonZone, or null. */
    public static CommonZone fromParam(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
