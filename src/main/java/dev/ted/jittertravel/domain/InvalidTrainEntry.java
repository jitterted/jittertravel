package dev.ted.jittertravel.domain;

import java.util.List;
import java.util.stream.Stream;

/**
 * Everything wrong with a submitted train trip, reported together.
 *
 * <p><strong>Why one exception and not one per kind of problem.</strong> A trip has two ends, and
 * they fail independently: a departure with no city and an arrival with no country is one submit
 * with two unrelated mistakes. Splitting locations and zones into separate exceptions meant the
 * first kind thrown hid the other end entirely — which is the 2026-09-06 bug wearing a new coat,
 * because fixing the reported end produced a *fresh* error rather than a booking.
 *
 * <p><strong>What the ordering rule actually is.</strong> A station that is not a place has no
 * meaningful zone question, so an end with a location problem contributes only that. The rule is
 * per <em>end</em>, never across the trip: the other end still gets asked, and still reports its
 * own zone problem in the same response.
 */
public class InvalidTrainEntry extends RuntimeException {

    private final List<InvalidLocationEntry> locations;
    private final List<UnresolvedStationZone> zones;

    public InvalidTrainEntry(List<InvalidLocationEntry> locations,
                             List<UnresolvedStationZone> zones) {
        super(Stream.concat(locations.stream(), zones.stream())
                    .map(Throwable::getMessage)
                    .reduce((a, b) -> a + " " + b)
                    .orElse(""));
        this.locations = List.copyOf(locations);
        this.zones = List.copyOf(zones);
    }

    /**
     * Ends naming something that cannot be a place. One entry per broken rule, so an end can appear
     * twice — a station with neither a name nor a city marks both of its inputs (2026-09-20). Never
     * more than once per <em>field</em>, which is what lets {@code TrainFormErrors} reject each
     * entry against its own input; see {@link EnteredLocation#problems}.
     */
    public List<InvalidLocationEntry> locations() {
        return locations;
    }

    /** Ends whose time zone could not be derived. At most one entry per end, and never an end
     *  that already appears in {@link #locations()}. */
    public List<UnresolvedStationZone> zones() {
        return zones;
    }

    /** Never thrown empty; this is what the two collections above are checked against. */
    public boolean isEmpty() {
        return locations.isEmpty() && zones.isEmpty();
    }
}
