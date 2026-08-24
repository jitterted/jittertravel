package dev.ted.jittertravel.domain;

/**
 * Where the schedule thinks Ted is, for one event — the single answer to "which field of this event
 * becomes a place?".
 * <p>
 * <strong>Why this is a type and not four expressions.</strong> Two readers have to agree, per kind,
 * or the app breaks quietly: {@code ScheduleGapProjector} derives a place to find a missing-travel
 * gap, and the ground-transfer form derives one to preselect the two ends of the hop that would
 * close it. Split that across two switches over the same events and
 * nothing says when they diverge — preselection silently stops matching, and a transfer Ted submits
 * fails to close the gap he entered it for. A wrong endpoint is worse than a missing one, because
 * the {@code GroundTransferPlanned} it writes <em>removes</em> the gap without fixing anything.
 * That is a missing value type, not a missing test (Ted, 2026-08-23).
 * <p>
 * <strong>Each factory is the rule for one kind</strong>, and the rules differ in ways that are easy
 * to get wrong from memory:
 * <ul>
 *   <li>an {@link Address} yields its {@code locationForMatching}, <em>not</em> its {@code city} —
 *       a venue in a hamlet is matched to the town everything else names, so a stay in Rückersbach
 *       lines up with a gap that says Johannesberg;</li>
 *   <li>a {@link TrainStationAddress} yields its {@code city}, <em>not</em> its {@code name} — the
 *       station is a building, the city is the place;</li>
 *   <li>an {@link AirportCode} yields the city the curated table names for it, so DEN is Denver and
 *       lines up with a hotel there. An unknown code falls back to the code itself, which is
 *       {@link AirportCityResolver}'s behaviour and not this type's to second-guess.</li>
 * </ul>
 * <p>
 * <strong>Compare with {@link #matches(Place)}, never with {@code equals}.</strong> The record's
 * generated {@code equals} is case-sensitive, and case is exactly what differs between an address
 * Ted typed and a curated table's spelling. {@code matches} is the comparison every call site
 * wants; {@code equals} is there because records have one, and using it is the bug.
 * <p>
 * The raw string is kept as it was written, not folded to a canonical case, because it is also what
 * {@code /schedule-problems} prints — "Denver", not "denver". Normalization here is only the house
 * rule that a domain string is never null.
 * <p>
 * <strong>What this deliberately does not do</strong> (D2 of
 * {@code docs/GroundTransferEndpointReadModelPlan.md}): it does not travel any further than the
 * derivation points. {@code ScheduleProblem}, {@code ScheduleTimeline} and the renderers keep
 * plain strings — threading {@code Place} through them touches a half-dozen more types for no
 * additional guarantee, because the thing that has to agree is *which field* becomes the place,
 * and after this that is written once.
 */
public record Place(String value) {

    public Place {
        value = value == null ? "" : value;
    }

    /** The place an address is matched in — {@code locationForMatching}, not {@code city}. */
    public static Place of(Address address) {
        return new Place(address.locationForMatching());
    }

    /** The place a station is in — its city, not the station's own name. */
    public static Place of(TrainStationAddress station) {
        return new Place(station.city());
    }

    /** The city an airport serves, per the curated table. */
    public static Place of(AirportCode airport, AirportCityResolver cities) {
        return new Place(cities.cityFor(airport.code()));
    }

    /**
     * Whether these name the same place, ignoring case — the comparison the ground-transfer form
     * and the gap report both need. A blank place matches a blank place, which is what the
     * hand-written comparison this replaced already did; the callers that care exclude blanks by
     * other means.
     */
    public boolean matches(Place other) {
        return value.equalsIgnoreCase(other.value);
    }
}
