package dev.ted.jittertravel.domain;

/**
 * The place the <em>schedule</em> reasons about a private event in has been corrected — the dinner
 * is still where it always was, but for the purpose of finding missing travel it now counts as
 * being in the named city.
 * <p>
 * <strong>What happened in the world is nothing.</strong> The venue did not move; Ted decided that
 * a restaurant in Centennial, CO is not somewhere he travels to from a hotel in Lone Tree, CO under
 * four miles away. {@code Address.locationForMatching} is the field that already says this — it is
 * why a venue in Rückersbach matches a gap that says Johannesberg — and this event changes that one
 * field and nothing else.
 * <p>
 * <strong>Why it is this narrow rather than a full snapshot.</strong> Of the six read models built
 * from {@link PrivateEventPlanned}, exactly one reads {@code locationForMatching}:
 * {@code ScheduleGapProjector}, through {@code Place.of}. The other five read {@code city} and
 * {@code country} (and, on the list, {@code street}/{@code region}/{@code postalCode}). A
 * full-snapshot {@code PrivateEventChanged} would need a branch in all six to change a field five of
 * them cannot see. Naming the fact that changed is what keeps the blast radius at one (Ted,
 * 2026-09-18) — see {@code docs/PrivateEventMatchingLocationPlan.md} D1.
 * <p>
 * <strong>{@code ChangePrivateEventPlan.md} slice 2's {@code PrivateEventEditView} must fold this
 * event</strong> — R8a in {@code EventSourcingRulesHeuristics.md}, which is written against this
 * case and says why nothing compiler-forces it.
 * <p>
 * Never blank: the command refuses one on the way in, so no payload carries {@code ""}. The compact
 * constructor still normalizes null to {@code ""} rather than trusting that, because Jackson binds
 * <em>stored</em> payloads through it and the no-null-Strings rule holds for history too.
 */
public record PrivateEventMatchingLocationChanged(
        PrivateEventId privateEventId,
        String locationForMatching
) implements Event {
    public PrivateEventMatchingLocationChanged {
        locationForMatching = locationForMatching == null ? "" : locationForMatching.trim();
    }
}
