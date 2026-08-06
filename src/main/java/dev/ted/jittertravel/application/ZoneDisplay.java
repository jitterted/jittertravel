package dev.ted.jittertravel.application;

/**
 * How one rendered page treats time zones: which zone it starts in, and whether the viewer may
 * switch. Produced by {@link ViewerZonePolicy} at the boundary and handed to a renderer.
 * <p>
 * The server always renders entry-local text; {@code active == BROWSER} means a client-side
 * script re-localizes it on load. With JavaScript off, every mode degrades to entry-local
 * (decision 8 of {@code docs/UtcDatetimeStoragePlan.md}).
 */
public record ZoneDisplay(DisplayZone active, boolean toggleable) {

    /** OWNER: entry-local, always, with no script and no toggle. */
    public static ZoneDisplay entryOnly() {
        return new ZoneDisplay(DisplayZone.ENTRY, false);
    }

    /** Whether the page needs the browser-zone script at all. */
    public boolean needsScript() {
        return active == DisplayZone.BROWSER || toggleable;
    }
}
