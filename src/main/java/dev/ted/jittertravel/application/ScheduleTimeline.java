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
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Where Ted is, in order. Every source of schedule facts — booked legs, hotel stays, conferences,
 * gatherings, private events — contributes a <em>presence fact</em>, and every detector reads this
 * one sequence.
 * <p>
 * This replaces a detector that knew only about flights and trains, with conferences bolted on: it
 * could not see that a hotel checkout puts you somewhere, so a checkout could never be one end of a
 * travel gap and never required a bed for the following night. See
 * {@code docs/archived/ScheduleProblemsRewritePlan.md}.
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
     * <p>
     * Read only through {@link #wentQuiet}, by both detectors that care.
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
     * before them and not two — except across a break long enough to have been a trip home, which
     * raises the two journeys it really is. See {@link #gapsLeaving}.
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
     * The days the calendar stripes as "away from home" — the reading of the same nights
     * {@link #missingHotels()} sweeps, from the other side.
     * <p>
     * <strong>Away means sleeping away from home.</strong> A night is away when the walk places it
     * in a city that is not home, or when it is spent in transit; a day is away because of the
     * nights around it, never on its own. So a night away bands its own day, and it also bands the
     * <em>following</em> day whenever the schedule still {@linkplain #accountedForOn accounts for
     * him} then — the day he travels home is part of the trip, and so is the closing afternoon of
     * a conference he has not booked a flight out of. The day after he lands is not.
     * <p>
     * The trailing day is earned rather than assumed, and a day the schedule says nothing about is
     * where the band stops. A trip whose return is not booked ends on the last day something
     * places him anywhere; banding on would invent both a homecoming and a whereabouts.
     * <p>
     * That test reads the <em>points</em> and not {@code locationByNight}, because {@code walk()}
     * fills nights only <em>between</em> points: the night after a trip's last fact is never
     * filled, and a trip's last fact is usually the flight home. Asking the map would quietly lose
     * the return day of every trip that ends the schedule.
     * <p>
     * Deliberately a set of days and not a list of trips: nothing reads a trip's boundaries, and
     * the day-label cells ask one question each. See {@code docs/archived/CalendarAwayBandPlan.md}.
     * <p>
     * Note the asymmetry with {@link #missingHotels()}, which is chosen (Ted, 2026-08-20): there,
     * "in transit" only <em>suppresses</em> a demand for a bed, while here it <em>asserts</em>
     * that he is away. A leg crossing local midnight with home at both ends — the late taxi from
     * the airport — therefore bands both days. One shared notion of transit is worth more than
     * the occasional extra stripe.
     */
    Set<LocalDate> awayDays() {
        // With no home configured, includes() is false for every city, which would read as "away,
        // always" and stripe the whole calendar. Silence is the right failure for that.
        if (homeCities.isEmpty()) {
            return Set.of();
        }
        Set<LocalDate> days = new LinkedHashSet<>();
        for (Map.Entry<LocalDate, String> night : walk().locationByNight().entrySet()) {
            if (homeCities.includes(night.getValue()) && !inTransitOvernight(night.getKey())) {
                continue;
            }
            days.add(night.getKey());
            LocalDate dayAfter = night.getKey().plusDays(1);
            if (accountedForOn(dayAfter)) {
                days.add(dayAfter);
            }
        }
        return days;
    }

    /**
     * The schedule still says where he is this day — whichever way it says it. Waking up after a
     * night away, the day belongs to the trip if <em>anything</em> is recorded: a flight home (he
     * returned, and the day he travelled is the trip's last), or a conference's closing afternoon
     * and a hotel checkout (he is still there). Only a day the schedule is silent about is left
     * unbanded, because nothing then says he was anywhere at all.
     */
    private boolean accountedForOn(LocalDate day) {
        return points.stream().anyMatch(point -> point.day().equals(day));
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

    /**
     * Two booked legs carrying Ted at once — he can only be on one of them.
     * <p>
     * The direct analogue of {@link #duplicateHotels()}, whose reasoning is <em>"Ted can only sleep
     * in one of them"</em>. Nothing here looked at legs against each other before, which is why an
     * overlapping pair raised no problem at all: the location walk sorts a leg's departure
     * ({@code REQUIRE}) and arrival ({@code ARRIVE}) as separate points, so when two legs overlap
     * both departures land before both arrivals, every departure agrees with the city the walk is
     * holding, and the contradiction disappears. (Two legs that do <em>not</em> overlap are caught
     * by the walk instead — as a {@code MissingTravel} back to where the second one starts.)
     * <p>
     * Kind-agnostic on purpose: a flight overlapping a train is the same impossibility as two
     * trains.
     * <p>
     * One problem per pair, over a list in a stable order, so the pair reported is the same pair
     * from one recompute to the next — {@code ProblemKey} is derived from that order.
     */
    List<ScheduleProblem.OverlappingTravel> overlappingTravel() {
        List<ScheduleProblem.OverlappingTravel> clashes = new ArrayList<>();
        for (int i = 0; i < movements.size(); i++) {
            for (int j = i + 1; j < movements.size(); j++) {
                Movement first = movements.get(i);
                Movement second = movements.get(j);
                if (first.overlapsWith(second)) {
                    clashes.add(new ScheduleProblem.OverlappingTravel(
                            overlapping(first), overlapping(second)));
                }
            }
        }
        return List.copyOf(clashes);
    }

    private static ScheduleProblem.OverlappingLeg overlapping(Movement movement) {
        return new ScheduleProblem.OverlappingLeg(movement.leg(), movement.fromCity(),
                movement.toCity(), movement.departure(), movement.arrival());
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
            boolean tripEnded = wentQuiet(currentDay, point.day());
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
                        gaps.addAll(gapsLeaving(currentCity, lastMoment, point, tripEnded));
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
    /**
     * Whether the schedule's <em>last word</em> on each day put him in a home city, keyed by that
     * day so a reader can ask about any date by looking back to the most recent day that has an
     * answer.
     * <p>
     * This exists because "he is home" needs <strong>positive evidence</strong>, and the absence
     * of an away band is not evidence of anything. {@link #awayDays()} fills nights only between
     * points, so a trip whose return is not booked yet — flown out, no hotel, nothing after —
     * bands nothing at all, and reading that silence as "home" would say he is in his own bed
     * while he is in Lisbon. Asking instead where the last recorded fact left him gets that case
     * right: it left him in Lisbon, so nothing is claimed until something says otherwise.
     * <p>
     * There is deliberately no expiry on the answer. An old fact that put him <em>home</em> stays
     * good indefinitely — he is home by choice, and no news is the normal state of being there
     * (the same reasoning {@link #gapLeaving} rests on). An old fact that put him <em>away</em>
     * likewise keeps claiming nothing, which is the safe direction.
     * <p>
     * The last point of a day wins, and the ordering makes that the right one: the day he flies
     * home holds a checkout and a departure from the away city before the arrival that lands him,
     * and it is the landing that says where he sleeps.
     */
    NavigableMap<LocalDate, Boolean> homeByLastFactOfDay() {
        TreeMap<LocalDate, Boolean> homeByDay = new TreeMap<>();
        for (Point point : points) {
            homeByDay.put(point.day(), homeCities.includes(point.city()));
        }
        return homeByDay;
    }

    /**
     * The missing journeys between two points the schedule does not connect — <strong>usually one,
     * but two when the schedule went quiet for a trip's length in between</strong>.
     * <p>
     * A conference in Potsdam ending in November and one in Stockholm starting in February are not
     * one flight apart. {@link #fillNights} already reads a stretch that long as <em>"the trip
     * ended and the next one has not started"</em> — that is what stops it demanding a Potsdam
     * hotel every night until February — and this is the same reading applied to travel. Under it
     * there are two missing journeys, Potsdam→home and home→Stockholm, and reporting them as one
     * was wrong three ways: no single booking fixes it, the fix link prefilled a Potsdam→Stockholm
     * flight nobody would buy, and the window spanned twelve weeks of amber (Ted, 2026-09-08).
     * <p>
     * <strong>Both halves are reported, deliberately.</strong> The flight home is as missing as the
     * flight out, and dropping it would leave the report saying he is still in Potsdam in January.
     * <p>
     * Each half is anchored at its <em>away</em> end and so is a single moment wide, which is
     * {@link #gapLeaving}'s rule — time at home is not part of any problem — pointing in both
     * directions for the first time: the home half is literally {@code gapLeaving} with home as the
     * origin, and the outbound half is its mirror.
     * <p>
     * It takes a configured home to split at, and it must not fire where one end is already home:
     * that is the single gap {@code gapLeaving} handles, and splitting it would report a journey
     * from home to home.
     */
    private List<ScheduleProblem.MissingTravel> gapsLeaving(String fromCity, ZonedTimestamp lastMoment,
                                                            Point arrival, boolean tripEnded) {
        if (!tripEnded || homeCities.isEmpty()
            || homeCities.includes(fromCity) || homeCities.includes(arrival.city())) {
            return List.of(gapLeaving(fromCity, lastMoment, arrival));
        }
        String home = homeCities.primaryCity();
        return List.of(
                new ScheduleProblem.MissingTravel(fromCity, lastMoment, home, lastMoment),
                gapLeaving(home, lastMoment, arrival));
    }

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
     * progress; it is the gap before the next trip, spent at home. The travel between the two is
     * still reported, as the two journeys that reading implies — see {@link #gapsLeaving}.
     */
    private static void fillNights(Map<LocalDate, String> locationByNight,
                                   LocalDate from, LocalDate until, String city) {
        if (wentQuiet(from, until)) {
            locationByNight.put(from, city);
            return;
        }
        for (LocalDate night = from; night.isBefore(until); night = night.plusDays(1)) {
            locationByNight.put(night, city);
        }
    }

    /**
     * Whether the schedule says nothing at all for longer than {@link #TRIP_BREAK_NIGHTS} — the one
     * definition of "the trip ended", read by {@link #fillNights} for the nights it stops claiming
     * and by {@link #gapsLeaving} for the journey it splits in two. One definition on purpose: the
     * night after which he is no longer in Potsdam and the flight that got him out of Potsdam are
     * the same fact seen twice, and two constants would let them disagree.
     */
    private static boolean wentQuiet(LocalDate from, LocalDate until) {
        return ChronoUnit.DAYS.between(from, until) > TRIP_BREAK_NIGHTS;
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

    /**
     * A booked leg, flight or train alike: the timeline cares that it moves him, not how.
     * <p>
     * It carries a {@link TravelLeg} because {@link #overlappingTravel()} has to <em>name</em> the
     * two legs it reports and link each to its page. Every other detector here reports cities and
     * dates, which the walk already has; this is the first that reports the bookings themselves.
     */
    record Movement(TravelLeg leg, String fromCity, ZonedTimestamp departure,
                    String toCity, ZonedTimestamp arrival) {
        LocalDate departureDay() {
            return departure.localDateTime().toLocalDate();
        }

        LocalDate arrivalDay() {
            return arrival.localDateTime().toLocalDate();
        }

        /**
         * A real overlap in time — the same predicate {@link Occupancy#overlapsWith} uses, and
         * half-open for the reason that matters here: <strong>a connection is not a conflict</strong>.
         * Arriving at 11:00 and departing at 11:00 is the ordinary shape of a journey, and a closed
         * comparison would report every one of them.
         */
        boolean overlapsWith(Movement other) {
            return this.departure.utc().isBefore(other.arrival.utc())
                   && other.departure.utc().isBefore(this.arrival.utc());
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

        /**
         * The same occupancy, reasoned about in a different city — what a
         * {@code PrivateEventMatchingLocationChanged} does to one. A copy-with rather than five
         * positional arguments at the call site: two of the five components are
         * {@link ZonedTimestamp}s, and transposed they would compile and be wrong.
         */
        Occupancy inCity(String otherCity) {
            return new Occupancy(name, otherCity, startsAt, endsAt, kind);
        }

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
