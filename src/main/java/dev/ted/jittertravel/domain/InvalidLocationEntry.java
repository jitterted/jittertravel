package dev.ted.jittertravel.domain;

/**
 * A booking names a place that cannot be one: a missing station or hotel name, a missing city, or a
 * city that is really the building's name pasted twice. Carries {@link #role()} and {@link #field()}
 * so the controller can attach the message to the one input at fault — the domain says <em>which
 * value</em> is wrong, the boundary knows what that value is called on the page.
 */
public class InvalidLocationEntry extends RuntimeException {

    private final LocationRole role;
    private final LocationField field;

    public InvalidLocationEntry(LocationRole role, LocationField field, String message) {
        super(message);
        this.role = role;
        this.field = field;
    }

    public LocationRole role() {
        return role;
    }

    public LocationField field() {
        return field;
    }
}
