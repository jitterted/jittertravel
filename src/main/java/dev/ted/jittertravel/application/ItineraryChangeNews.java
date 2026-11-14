package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCityResolver;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.domain.Journey;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Whether a change to an itinerary is news to family: the rule in
 * {@code docs/FlightItineraryRebookingPlan.md} D5 and D7. <strong>One rule with two callers</strong>,
 * as the plan asks: the change page's preview says whether family will be emailed and why, and
 * {@link NotifyFamily} decides whether to email them, from this same comparison.
 * <p>
 * It compares <em>where and when travel starts and ends</em>, journey by journey, and nothing else.
 * Each journey after the change is paired with the journeys before it that overlap it in time; the
 * first of those gives the start it is compared with, the last gives the end. So a connection's
 * times and airports never count, and two journeys merging into one (or one splitting in two) is
 * no news while its outer ends stay put (D7). It is news when:
 * <ul>
 *   <li>a journey starts or ends in a different place: a different city, where any two home
 *       cities are the same place, so returning to OAK instead of SFO is no change;</li>
 *   <li>a journey's first departure or last arrival moves by more than {@link #MATERIAL} (2 hours);</li>
 *   <li>a journey appears that overlaps none before, or one before is overlapped by none after.</li>
 * </ul>
 * Examples: UA 512 moving from 08:00 to 08:40 is not news. MD7LKB's return moving from YOW to YYZ
 * is. HE80PP's return, which used to start in Berlin at 10:40 and now starts in Frankfurt at 13:30,
 * is.
 */
public class ItineraryChangeNews {

    static final Duration MATERIAL = Duration.ofHours(2);

    /** What is different, for the preview to say. Only the first difference found is reported. */
    public enum Kind { STARTS, ENDS, DEPARTS, ARRIVES, ADDED, DROPPED }

    /**
     * One difference. {@code journey} is the journey's 1-based place among {@code journeys} after
     * the change (for {@code DROPPED}, among the journeys before it). The airports and times are the
     * ones that differ; for {@code ADDED} and {@code DROPPED} they are the journey's own two ends.
     */
    public record Difference(Kind kind, int journey, int journeys,
                             AirportCode was, AirportCode now,
                             ZonedTimestamp wasAt, ZonedTimestamp nowAt) {
    }

    private final AirportCityResolver cities;
    private final HomeCities homeCities;

    public ItineraryChangeNews(AirportCityResolver cities, HomeCities homeCities) {
        this.cities = cities;
        this.homeCities = homeCities;
    }

    /**
     * The first difference family would care about between the legs still flying before the change
     * and after it, or empty when there is none.
     */
    public Optional<Difference> between(List<ItineraryLeg> before, List<ItineraryLeg> after) {
        List<Journey> was = journeys(before);
        List<Journey> now = journeys(after);
        for (int i = 0; i < now.size(); i++) {
            Journey journey = now.get(i);
            List<Journey> overlapping = was.stream().filter(earlier -> overlap(earlier, journey)).toList();
            Optional<Difference> difference = overlapping.isEmpty()
                    ? Optional.of(new Difference(Kind.ADDED, i + 1, now.size(), journey.from(), journey.to(),
                            journey.departs(), journey.arrives()))
                    : compared(i + 1, now.size(), overlapping.getFirst(), overlapping.getLast(), journey);
            if (difference.isPresent()) {
                return difference;
            }
        }
        for (int i = 0; i < was.size(); i++) {
            Journey journey = was.get(i);
            if (now.stream().noneMatch(later -> overlap(journey, later))) {
                return Optional.of(new Difference(Kind.DROPPED, i + 1, was.size(), journey.from(), journey.to(),
                        journey.departs(), journey.arrives()));
            }
        }
        return Optional.empty();
    }

    private Optional<Difference> compared(int index, int count, Journey first, Journey last, Journey journey) {
        if (!samePlace(first.from(), journey.from())) {
            return Optional.of(new Difference(Kind.STARTS, index, count, first.from(), journey.from(),
                    first.departs(), journey.departs()));
        }
        if (!samePlace(last.to(), journey.to())) {
            return Optional.of(new Difference(Kind.ENDS, index, count, last.to(), journey.to(),
                    last.arrives(), journey.arrives()));
        }
        if (movedMuch(first.departs(), journey.departs())) {
            return Optional.of(new Difference(Kind.DEPARTS, index, count, first.from(), journey.from(),
                    first.departs(), journey.departs()));
        }
        if (movedMuch(last.arrives(), journey.arrives())) {
            return Optional.of(new Difference(Kind.ARRIVES, index, count, last.to(), journey.to(),
                    last.arrives(), journey.arrives()));
        }
        return Optional.empty();
    }

    private List<Journey> journeys(List<ItineraryLeg> legs) {
        if (legs.isEmpty()) {
            return List.of();
        }
        return Journey.of(legs.stream().sorted(Comparator.comparing(leg -> leg.departureDateTime().utc())).toList());
    }

    /** Closed intervals: a journey that ends as the other begins still touches it. */
    private boolean overlap(Journey a, Journey b) {
        return !a.arrives().utc().isBefore(b.departs().utc()) && !b.arrives().utc().isBefore(a.departs().utc());
    }

    private boolean samePlace(AirportCode a, AirportCode b) {
        return a.equals(b) || homeCities.sameLocation(cities.cityFor(a.code()), cities.cityFor(b.code()));
    }

    private boolean movedMuch(ZonedTimestamp was, ZonedTimestamp now) {
        return Duration.between(was.utc(), now.utc()).abs().compareTo(MATERIAL) > 0;
    }
}
