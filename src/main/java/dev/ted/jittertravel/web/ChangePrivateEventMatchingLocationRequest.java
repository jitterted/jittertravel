package dev.ted.jittertravel.web;

import java.util.UUID;

/**
 * Form bean for the "match location" page. A mutable bean rather than a record because Thymeleaf
 * binds it with {@code th:object}/{@code th:field}, which needs a no-arg constructor and setters —
 * the same shape {@link PlanPrivateEventRequest} has, and unlike {@link CancelPrivateEventRequest},
 * whose one value arrives as a plain request parameter.
 * <p>
 * The id is set by the controller from the path, never bound from the form: a hidden field holding
 * it would let a submit re-target a different evening.
 * <p>
 * {@code locationForMatching} arrives already trimmed — {@code TrimTypedTextAdvice} registers a
 * {@code StringTrimmerEditor} for every bound String — so {@code " "} reaches the command as
 * {@code ""} and is refused as blank rather than becoming a place name made of whitespace.
 */
public class ChangePrivateEventMatchingLocationRequest {

    private UUID privateEventId;
    private String locationForMatching;

    public UUID privateEventId() {
        return privateEventId;
    }

    /**
     * Present so the command log records <em>which</em> evening was re-matched: {@code saveCommand}
     * Jackson-serializes this bean, and Jackson reads {@code getXxx} — the accessor above is
     * invisible to it. Without this the stored payload is {@code {"locationForMatching":"..."}} and
     * a FAILED row names no target at all.
     */
    public UUID getPrivateEventId() {
        return privateEventId;
    }

    public void setPrivateEventId(UUID privateEventId) {
        this.privateEventId = privateEventId;
    }

    public String locationForMatching() {
        return locationForMatching;
    }

    public String getLocationForMatching() {
        return locationForMatching;
    }

    public void setLocationForMatching(String locationForMatching) {
        this.locationForMatching = locationForMatching;
    }
}
