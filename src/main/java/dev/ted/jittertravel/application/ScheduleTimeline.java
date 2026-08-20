package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where Ted is, in order. Every source of schedule facts — booked legs, hotel stays, conferences,
 * gatherings, private events — contributes a <em>presence fact</em>, and every detector reads this
 * one sequence.
 * <p>
 * This replaces a detector that knew only about flights and trains, with conferences bolted on: it
 * could not see that a hotel checkout puts you somewhere, so a checkout could never be one end of a
 * travel gap and never required a bed for the following night. See
 * {@code docs/ScheduleProblemsRewritePlan.md}.
 * <p>
 * <strong>Ordering is by local day, then by role — never by stored clock time alone.</strong> A
 * single day routinely holds a checkout, two legs, and a check-in, and hotel check-in times are
 * whatever was typed into the form: sorting those four by instant would let a check-in recorded at
 * midnight sort ahead of the flight that gets you there, and invent a gap. Within a day the legs,
 * whose times are real, establish the sequence: you leave a stay, you travel, you enter the next
 * one.
 */
class ScheduleTimeline {

    /**
     * How long the schedule may go quiet before last-known-location stops applying. Ted's trips are
     * dense — a booked leg, a stay, a conference, a gathering every day or two — so a fortnight
     * with nothing recorded anywhere means the trip ended and the next one has not started.
     */
    private static final int TRIP_BREAK_NIGHTS = 14;

    private final HomeCities homeCities;
    private final List<Stay> stays;
    private final List<Movement> movements;
    private final List<Point> points;

    ScheduleTimeline(List<Stay> stays,
                     List<Occupancy> occupancies,
                     List<Movement> movements,
                     HomeCities homeCities) {
        this.homeCities = homeCities;
        this.stays = List.copyOf(stays);
        this.movements = List.copyOf(movements);
        this.points = orderedPoints(stays, occupancies, movements);
    }

    /**
     * Gaps where the schedule moves Ted between cities with nothing booked to carry him: one
     * problem per adjacent pair of locations, so two gatherings in the same city raise one gap
     * before them and not two.
     */
    List<ScheduleProblem.MissingTravel> missingTravel() {
        return walk().gaps();
    }

    /**
     * Nights away from home with no bed. Hotel stays must be contiguous whenever Ted is away, so
     * this sweeps <em>nights</em>, not legs: a night belongs to wherever the timeline last placed
     * him, and if nothing covers it, it is missing.
     * <p>
     * Consecutive uncovered nights merge into one run and split where the <em>city</em> changes,
     * because the row has to say where to book. A conference ending does not split a run — that
     * would not split the booking either.
     */
    List<ScheduleProblem.MissingHotel> missingHotels() {
        Map<LocalDate, String> locationByNight = walk().locationByNight();
        List<CityNight> uncovered = new ArrayList<>();
        for (Map.Entry<LocalDate, String> night : locationByNight.entrySet()) {
            String city = night.getValue();
            if (homeCities.includes(city) || inTransitOvernight(night.getKey())) {
                continue;
            }
            boolean covered = stays.stream()
                    .anyMatch(stay -> homeCities.sameLocation(stay.city(), city)
                                      && stay.coversNight(night.getKey()));
            if (!covered) {
                uncovered.add(new CityNight(city, night.getKey()));
            }
        }
        uncovered.sort(Comparator.comparing(CityNight::night));

        List<ScheduleProblem.MissingHotel> runs = new ArrayList<>();
        int index = 0;
        while (index < uncovered.size()) {
            CityNight first = uncovered.get(index);
            LocalDate lastNight = first.night();
            while (index + 1 < uncovered.size()
                   && homeCities.sameLocation(uncovered.get(index + 1).city(), first.city())
                   && uncovered.get(index + 1).night().equals(lastNight.plusDays(1))) {
                index++;
                lastNight = uncovered.get(index).night();
            }
            runs.add(new ScheduleProblem.MissingHotel(first.city(), first.night(),
                    lastNight.plusDays(1), conferenceNameFor(first.city(), first.night(), lastNight)));
            index++;
        }
        return runs;
    }

    /**
     * Nights covered by more than one stay. Ted can only sleep in one of them and is usually still
     * paying for the other, so two rooms in one city and two rooms in two cities are the same
     * mistake. {@link BookingIntent} is not consulted: a tentative reservation is a reservation
     * until it is cancelled.
     */
    List<ScheduleProblem.DuplicateHotel> duplicateHotels() {
        Map<LocalDate, Set<Stay>> doubledNights = new HashMap<>();
        for (LocalDate night : allStayNights()) {
            Set<Stay> covering = new LinkedHashSet<>(stays.stream()
                    .filter(stay -> stay.coversNight(night))
                    .toList());
            if (covering.size() > 1) {
                doubledNights.put(night, covering);
            }
        }

        List<LocalDate> nights = new ArrayList<>(doubledNights.keySet());
        nights.sort(Comparator.naturalOrder());

        List<ScheduleProblem.DuplicateHotel> duplicates = new ArrayList<>();
        int index = 0;
        while (index < nights.size()) {
            LocalDate firstNight = nights.get(index);
            Set<Stay> culprits = doubledNights.get(firstNight);
            LocalDate lastNight = firstNight;
            // A run holds only while the *same* stays are the ones overlapping: a third booking
            // joining on the 4th is a different problem from the pair that started on the 1st.
            while (index + 1 < nights.size()
                   && nights.get(index + 1).equals(lastNight.plusDays(1))
                   && doubledNights.get(nights.get(index + 1)).equals(culprits)) {
                index++;
                lastNight = nights.get(index);
            }
            duplicates.add(new ScheduleProblem.DuplicateHotel(firstNight, lastNight,
                    culprits.stream().map(Stay::asDuplicate).toList()));
            index++;
        }
        return duplicates;
    }

    private Set<LocalDate> allStayNights() {
        Set<LocalDate> nights = new LinkedHashSet<>();
        for (Stay stay : stays) {
            for (LocalDate night = stay.checkInDay(); night.isBefore(stay.checkOutDay()); night = night.plusDays(1)) {
                nights.add(night);
            }
        }
        return nights;
    }

    /** A night spent in the air or on a train needs no bed — the leg is where he is. */
    private boolean inTransitOvernight(LocalDate night) {
        return movements.stream()
                .anyMatch(movement -> !movement.departureDay().isAfter(night)
                                      && movement.arrivalDay().isAfter(night));
    }

    private String conferenceNameFor(String city, LocalDate firstNight, LocalDate lastNight) {
        return points.stream()
                .filter(point -> point.conferenceName() != null
                                 && homeCities.sameLocation(point.city(), city)
                                 && !point.day().isAfter(lastNight)
                                 && !point.day().isBefore(firstNight))
                .map(Point::conferenceName)
                .findFirst()
                .orElse("");
    }

    /**
     * One pass over the ordered points, producing both read models. They come from the same walk
     * on purpose: a gap and the nights around it must describe the same journey.
     */
    private Walk walk() {
        List<ScheduleProblem.MissingTravel> gaps = new ArrayList<>();
        Map<LocalDate, String> locationByNight = new LinkedHashMap<>();
        if (points.isEmpty()) {
            return new Walk(gaps, locationByNight);
        }

        String currentCity = points.getFirst().city();
        ZonedTimestamp lastMoment = points.getFirst().moment();
        LocalDate currentDay = points.getFirst().day();

        for (Point point : points) {
            fillNights(locationByNight, currentDay, point.day(), currentCity);
            currentDay = point.day();
            switch (point.role()) {
                case LEAVE -> {
                    // A stay or event ending never opens a gap; it only records that he was still
                    // there that morning, which is what a later gap reports as its start.
                    if (homeCities.sameLocation(point.city(), currentCity)) {
                        lastMoment = point.moment();
                    }
                }
                case ARRIVE -> {
                    // A booked leg put him here, so there is nothing missing.
                    currentCity = point.city();
                    lastMoment = point.moment();
                }
                case REQUIRE -> {
                    if (!homeCities.sameLocation(point.city(), currentCity)) {
                        gaps.add(gapLeaving(currentCity, lastMoment, point));
                    }
                    currentCity = point.city();
                    lastMoment = point.moment();
                }
            }
        }
        // Nothing is demanded for the night after the last fact the schedule holds. Inside a trip
        // a following fact is what claims a night — the conference ending on the 13th needs a bed
        // that night because the flight home leaves on the 14th — but past the last fact there is
        // no trip left to be on, and the alternative is demanding a bed every night forever.
        return new Walk(gaps, locationByNight);
    }

    /**
     * The window a missing journey occupies — and <strong>home does not strand him</strong>.
     * <p>
     * Away from home, a gap runs from the moment he was last accounted for to the moment he has to
     * be somewhere else, and every day in between is part of the problem: checked out of Hamburg on
     * the 7th with a gathering in Aachen on the 8th, both days are the gap. Leaving home is not
     * like that. Landing at SJC on October 15th with a conference in North Gower on the 19th, the
     * four days at home are not a problem to solve — the problem is one journey, on the 19th. Read
     * the other way the gap spanned every day since he got home, which on a longer stay was eleven
     * days of amber for a single missing flight.
     * <p>
     * The window is the span of the <em>problem</em>, and time at home is not part of any problem —
     * he is home, indefinitely, by choice. That is the whole of it. (Not that his presence at home
     * goes unrecorded: landing at SJC records it precisely, and is what put him there.) So a gap
     * whose origin is home is anchored at the away end.
     * <p>
     * This also keeps the problem actionable longer, since {@code relevantUntil} is the far end of
     * the window: the missing flight stays on the report until the day he needed to have taken it.
     */
    private ScheduleProblem.MissingTravel gapLeaving(String fromCity, ZonedTimestamp lastMoment, Point arrival) {
        ZonedTimestamp windowStart = homeCities.includes(fromCity) ? arrival.moment() : lastMoment;
        return new ScheduleProblem.MissingTravel(fromCity, windowStart, arrival.city(), arrival.moment());
    }

    /**
     * Fills {@code [from, until)} with the city he was last in — and stops early when the schedule
     * goes quiet for longer than {@link #TRIP_BREAK_NIGHTS}.
     * <p>
     * Last-known-location is right inside a trip: checked out of Hamburg with a gathering in Aachen
     * two days later, he is in Hamburg that night and Aachen after. Carried far enough it becomes
     * absurd — a conference in January and the next one in December would demand eleven months of
     * hotel rooms in Oslo. A stretch this long with nothing at all recorded is not a trip in
     * progress; it is the gap before the next trip, spent at home. The travel gap between the two
     * is still reported, which is the real problem in that schedule.
     */
    private static void fillNights(Map<LocalDate, String> locationByNight,
                                   LocalDate from, LocalDate until, String city) {
        if (ChronoUnit.DAYS.between(from, until) > TRIP_BREAK_NIGHTS) {
            locationByNight.put(from, city);
            return;
        }
        for (LocalDate night = from; night.isBefore(until); night = night.plusDays(1)) {
            locationByNight.put(night, city);
        }
    }

    private static List<Point> orderedPoints(List<Stay> stays,
                                             List<Occupancy> occupancies,
                                             List<Movement> movements) {
        List<Point> points = new ArrayList<>();
        for (Stay stay : stays) {
            points.add(Point.enters(stay.city(), stay.checkIn()));
            points.add(Point.leaves(stay.city(), stay.checkOut()));
        }
        for (Occupancy occupancy : occupancies) {
            points.add(Point.enters(occupancy.city(), occupancy.startsAt(), occupancy.conferenceName()));
            points.add(Point.leaves(occupancy.city(), occupancy.endsAt(), occupancy.conferenceName()));
        }
        for (Movement movement : movements) {
            points.add(Point.departs(movement.fromCity(), movement.departure()));
            points.add(Point.arrives(movement.toCity(), movement.arrival()));
        }
        points.sort(Comparator.comparing((Point point) -> point.day())
                .thenComparingInt(Point::rank)
                .thenComparing(point -> point.moment().utc())
                .thenComparing(Point::city));
        return List.copyOf(points);
    }

    private record Walk(List<ScheduleProblem.MissingTravel> gaps, Map<LocalDate, String> locationByNight) {}

    private record CityNight(String city, LocalDate night) {}

    /**
     * What happens at one moment on the timeline. {@code rank} orders the roles within a day: a
     * stay or event ends in the morning, legs run during the day in their own real order, and the
     * next stay or event begins in the evening.
     */
    private record Point(LocalDate day, int rank, Role role, String city,
                         ZonedTimestamp moment, String conferenceName) {

        static Point leaves(String city, ZonedTimestamp moment) {
            return leaves(city, moment, null);
        }

        static Point leaves(String city, ZonedTimestamp moment, String conferenceName) {
            return new Point(day(moment), 0, Role.LEAVE, city, moment, conferenceName);
        }

        static Point departs(String city, ZonedTimestamp moment) {
            return new Point(day(moment), 1, Role.REQUIRE, city, moment, null);
        }

        static Point arrives(String city, ZonedTimestamp moment) {
            return new Point(day(moment), 1, Role.ARRIVE, city, moment, null);
        }

        static Point enters(String city, ZonedTimestamp moment) {
            return enters(city, moment, null);
        }

        static Point enters(String city, ZonedTimestamp moment, String conferenceName) {
            return new Point(day(moment), 2, Role.REQUIRE, city, moment, conferenceName);
        }

        private static LocalDate day(ZonedTimestamp moment) {
            return moment.localDateTime().toLocalDate();
        }
    }

    private enum Role {
        /** A stay or event ends: he was still here this morning. */
        LEAVE,
        /** He must already be here — a leg departs, or a stay or event begins. */
        REQUIRE,
        /** A booked leg has just put him here. */
        ARRIVE
    }

    /** A booked leg, flight or train alike: the timeline cares that it moves him, not how. */
    record Movement(String fromCity, ZonedTimestamp departure, String toCity, ZonedTimestamp arrival) {
        LocalDate departureDay() {
            return departure.localDateTime().toLocalDate();
        }

        LocalDate arrivalDay() {
            return arrival.localDateTime().toLocalDate();
        }
    }

    /** A booked hotel stay: a presence fact <em>and</em> the coverage a missing-bed night wants. */
    record Stay(HotelBookingId bookingId, String hotelName, String city,
                ZonedTimestamp checkIn, ZonedTimestamp checkOut, BookingIntent bookingIntent) {

        LocalDate checkInDay() {
            return checkIn.localDateTime().toLocalDate();
        }

        LocalDate checkOutDay() {
            return checkOut.localDateTime().toLocalDate();
        }

        boolean coversNight(LocalDate night) {
            return !night.isBefore(checkInDay()) && night.isBefore(checkOutDay());
        }

        ScheduleProblem.DuplicateStay asDuplicate() {
            return new ScheduleProblem.DuplicateStay(bookingId, hotelName, city, bookingIntent);
        }
    }

    /**
     * Somewhere Ted is because something is happening there: a conference, a gathering, a private
     * event. Private events are gatherings as far as the schedule is concerned — the difference is
     * who may see them, which is the redactor's problem, not this one's.
     */
    record Occupancy(String name, String city, ZonedTimestamp startsAt, ZonedTimestamp endsAt, Kind kind) {

        enum Kind { CONFERENCE, GATHERING, PRIVATE_EVENT }

        /** Only a conference lends its name to a missing-hotel row. */
        String conferenceName() {
            return kind == Kind.CONFERENCE ? name : null;
        }

        LocalDate firstDay() {
            return startsAt.localDateTime().toLocalDate();
        }

        LocalDate lastDay() {
            return endsAt.localDateTime().toLocalDate();
        }

        /**
         * A real overlap in time, not "one's local date falls in the other's local date range":
         * two things in different zones can overlap in real time while falling on different local
         * dates, which a same-date test could never see.
         */
        boolean overlapsWith(Occupancy other) {
            return this.startsAt.utc().isBefore(other.endsAt.utc())
                   && other.startsAt.utc().isBefore(this.endsAt.utc());
        }
    }

}
