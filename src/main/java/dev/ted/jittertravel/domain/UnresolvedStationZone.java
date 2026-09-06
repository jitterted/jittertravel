package dev.ted.jittertravel.domain;

/**
 * A station's time zone could not be derived from what was typed, and no {@code CommonZone} was
 * picked. Carries {@link #role()} so the form knows which end of the trip is at fault, and
 * {@link #cause()} because the two causes want different fixes: a blank country is repaired by
 * typing one, while a country the curated table does not know leaves picking a zone as the only
 * way through. The boundary turns those into a sentence and an input — the domain names neither.
 *
 * <p>This is the same division of labour as {@link InvalidLocationEntry}: the domain says which
 * value is wrong, the page knows what that value is called.
 */
public class UnresolvedStationZone extends RuntimeException {

    /**
     * Why the derivation failed. Deliberately two values and not three: {@code "USA"} is a country
     * the tables know but cannot resolve alone, and it reads as {@code COUNTRY_UNRECOGNISED} — the
     * message that case gets ("no time zone known for this country") is true of it, and a third
     * value would buy a distinction the form cannot act on differently.
     */
    public enum Cause {
        /** Nothing was typed in the country box. */
        COUNTRY_MISSING,
        /** A country was typed, and no zone follows from it. */
        COUNTRY_UNRECOGNISED
    }

    private final LocationRole role;
    private final Cause cause;

    public UnresolvedStationZone(LocationRole role, Cause cause) {
        super("Could not derive a time zone for the " + role + " station: " + cause);
        this.role = role;
        this.cause = cause;
    }

    public LocationRole role() {
        return role;
    }

    public Cause cause() {
        return cause;
    }
}
