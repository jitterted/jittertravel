package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Turns the event stream into the {@code /schedule-problems} read models.
 * <p>
 * Detection itself lives in {@link ScheduleTimeline}: this class only holds the current state of
 * every located thing — legs, stays, conferences, gatherings, private events — and hands it over.
 * <strong>Any entry kind that has a location must be handled here</strong>, or it becomes invisible
 * to the location trace and silently breaks every problem after it; {@code
 * LocatedEventsReachScheduleProblemsTest} enforces that. See
 * {@code docs/archived/ScheduleProblemsRewritePlan.md}.
 */
public class ScheduleGapProjector implements EventStreamConsumer {

    private final AirportCityResolver cityResolver;
    private final HomeCities homeCities;
    private final Map<FlightId, ScheduleTimeline.Movement> flightLegs = new ConcurrentHashMap<>();
    private final Map<TrainTripId, ScheduleTimeline.Movement> trainLegs = new ConcurrentHashMap<>();
    private final Map<GroundTransferId, ScheduleTimeline.Movement> groundTransfers = new ConcurrentHashMap<>();
    private final Map<HotelBookingId, ScheduleTimeline.Stay> hotelStays = new ConcurrentHashMap<>();
    private final Map<ConferenceId, ScheduleTimeline.Occupancy> conferences = new ConcurrentHashMap<>();
    private final Map<GatheringId, ScheduleTimeline.Occupancy> gatherings = new ConcurrentHashMap<>();
    private final Map<PrivateEventId, ScheduleTimeline.Occupancy> privateEvents = new ConcurrentHashMap<>();
    private final Set<ClearedConflict> clearedConflicts = new HashSet<>();

    // The read model. Problem detection depends only on event-derived state (no clock, no viewer),
    // so it is computed while events are handled and served from here — never re-derived on read.
    // volatile: handle() runs on the append/replay thread, problems() on a web thread.
    private volatile List<ScheduleProblem> cachedProblems = List.of();

    // The second read model, from the same state and the same batch: what the schedule holds,
    // which is what explains the problems above. See ScheduleContext.
    private volatile List<ScheduleContext> cachedContext = List.of();

    // The third: the days /calendar stripes as away from home. Same state, same batch, same
    // reasons — a day the band calls "away" and a night the report calls "no bed" have to be
    // talking about the same journey.
    private volatile Set<LocalDate> cachedAwayDays = Set.of();

    // The fourth: where the schedule's last word of each day left him, home or not. Same state,
    // same batch, and the necessary other half of the away band — see atHomeOn.
    private volatile NavigableMap<LocalDate, Boolean> cachedHomeByLastFact = Collections.emptyNavigableMap();

    public ScheduleGapProjector(AirportCityResolver cityResolver) {
        this(cityResolver, new HomeCities(List.of()));
    }

    public ScheduleGapProjector(AirportCityResolver cityResolver, HomeCities homeCities) {
        this.cityResolver = cityResolver;
        this.homeCities = homeCities;
    }

    @Override
    public void handle(Stream<StoredEvent> events) {
        events.forEach(stored -> {
            switch (stored.payload()) {
                case FlightBooked e -> flightLegs.put(e.flightId(), flightLeg(
                        e.departureAirport(), e.departureDateTime(),
                        e.arrivalAirport(), e.arrivalDateTime()));
                case FlightChanged e -> flightLegs.put(e.flightId(), flightLeg(
                        e.departureAirport(), e.departureDateTime(),
                        e.arrivalAirport(), e.arrivalDateTime()));
                case TrainBooked e -> trainLegs.put(e.tripId(), new ScheduleTimeline.Movement(
                        e.departureStation().city(), e.departureDateTime(),
                        e.arrivalStation().city(), e.arrivalDateTime()));
                case TrainChanged e -> trainLegs.put(e.tripId(), new ScheduleTimeline.Movement(
                        e.departureStation().city(), e.departureDateTime(),
                        e.arrivalStation().city(), e.arrivalDateTime()));
                // The whole point of a ground transfer: it is a leg like any other, so the gap it
                // fills stops being reported. Both ends compare on locationForMatching — the hotel's
                // own, or the airport's city — which is what the timeline matches conferences and
                // stays against.
                case GroundTransferPlanned e -> groundTransfers.put(e.groundTransferId(),
                        new ScheduleTimeline.Movement(
                                e.origin().locationForMatching(), e.departsAt(),
                                e.destination().locationForMatching(), e.arrivesAt()));
                // The point of cancelling: a wrong transfer must stop asserting that the hop
                // happened, or it goes on masking the missing-travel gap it was entered to close.
                case GroundTransferCancelled e -> groundTransfers.remove(e.groundTransferId());
                case HotelBooked e -> hotelStays.put(e.hotelBookingId(), new ScheduleTimeline.Stay(
                        e.hotelBookingId(), e.hotelName(), e.address().locationForMatching(),
                        e.checkIn(), e.checkOut(), e.bookingIntent()));
                case HotelChanged e -> hotelStays.put(e.hotelBookingId(), new ScheduleTimeline.Stay(
                        e.hotelBookingId(), e.hotelName(), e.address().locationForMatching(),
                        e.checkIn(), e.checkOut(), e.bookingIntent()));
                case HotelBookingCancelled e -> hotelStays.remove(e.hotelBookingId());
                case ConferencePlanned e -> conferences.put(e.conferenceId(),
                        new ScheduleTimeline.Occupancy(e.name(), e.venueAddress().locationForMatching(),
                                e.startDate(), e.endDate(), ScheduleTimeline.Occupancy.Kind.CONFERENCE));
                case ConferenceCancelled e -> conferences.remove(e.conferenceId());
                case ConferenceAttendanceDeclined e -> conferences.remove(e.conferenceId());
                case GatheringPlanned e -> gatherings.put(e.gatheringId(),
                        new ScheduleTimeline.Occupancy(e.title(), e.location().locationForMatching(),
                                e.startsAt(), e.endsAt(), ScheduleTimeline.Occupancy.Kind.GATHERING));
                case GatheringChanged e -> gatherings.put(e.gatheringId(),
                        new ScheduleTimeline.Occupancy(e.title(), e.location().locationForMatching(),
                                e.startsAt(), e.endsAt(), ScheduleTimeline.Occupancy.Kind.GATHERING));
                // A private event places Ted somewhere exactly as a gathering does; only who may
                // see it differs, and that is the redactor's problem, not this one's.
                case PrivateEventPlanned e -> privateEvents.put(e.privateEventId(),
                        new ScheduleTimeline.Occupancy(e.title(), e.location().locationForMatching(),
                                e.startsAt(), e.endsAt(), ScheduleTimeline.Occupancy.Kind.PRIVATE_EVENT));
                case DifferentCityConflictCleared e ->
                        clearedConflicts.add(new ClearedConflict(e.gatheringId(), e.conferenceId()));
                default -> {}
            }
        });
        // Recompute the read models once per handled batch (replay: once; runtime: once per
        // append), so reads are O(1) and never re-derive from the event-shaped state. Both are
        // computed from the same state in the same pass, so a problem and its context cannot
        // describe two different versions of the schedule.
        ScheduleTimeline timeline = timeline();
        cachedProblems = computeProblems(timeline);
        cachedContext = computeContext();
        cachedAwayDays = Set.copyOf(timeline.awayDays());
        cachedHomeByLastFact = Collections.unmodifiableNavigableMap(timeline.homeByLastFactOfDay());
    }

    /** The whole read model: every detected problem, past ones included. */
    public List<ScheduleProblem> problems() {
        return cachedProblems;
    }

    /**
     * The still-actionable problems as of {@code now}: the read model filtered to those not yet
     * past. {@code now} is a caller-supplied criterion (not an event), applied here on read — see
     * H8 in EventSourcingRulesHeuristics.md. This is what the views want; a problem whose window
     * has closed can no longer be fixed.
     */
    public List<ScheduleProblem> problems(Instant now) {
        return cachedProblems.stream()
                .filter(problem -> TimeView.FUTURE.includes(problem, now))
                .toList();
    }

    /**
     * Everything the schedule holds, unfiltered: conferences, gatherings, private events, booked
     * legs and booked stays, each as a run of local days. Unlike {@link #problems(Instant)} there
     * is no {@code now} cut — a caller showing context behind a problem already knows which days
     * it is drawing, and clipping to that window is its job, not this one's.
     */
    public List<ScheduleContext> context() {
        return cachedContext;
    }

    /**
     * Every day Ted is away from home, for the calendar's away band. Unfiltered by {@code now} on
     * purpose, unlike {@link #problems(Instant)}: the band's whole value is that it still shows in
     * collapsed past weeks, so a past cut would delete the feature.
     */
    public Set<LocalDate> awayDays() {
        return cachedAwayDays;
    }

    /**
     * The uncovered run of nights containing {@code date}, if there is one — where he is on a day
     * with a bed missing under it.
     * <p>
     * Deliberately answered from the {@link ScheduleProblem.MissingHotel} read model rather than
     * from a raw location lookup, so a view saying "he is in Denver and nothing is booked" is
     * saying exactly what {@code /schedule-problems} already says, with the same dates behind the
     * same fix link. It also inherits that sweep's two exclusions for free: a night at home and a
     * night spent in transit demand no bed, so neither is ever reported here.
     * <p>
     * The run is half-open, {@code [checkIn, checkOut)}: the checkout day is the morning he leaves
     * and is not itself a night without a bed.
     */
    public Optional<ScheduleProblem.MissingHotel> missingHotelOn(LocalDate date) {
        return cachedProblems.stream()
                .filter(problem -> problem instanceof ScheduleProblem.MissingHotel)
                .map(problem -> (ScheduleProblem.MissingHotel) problem)
                .filter(missing -> !date.isBefore(missing.checkIn()) && date.isBefore(missing.checkOut()))
                .findFirst();
    }

    /**
     * Whether the schedule <strong>positively places him at home</strong> on {@code date}, for a
     * view that wants to say so out loud. Two things must hold, and the second is the one that
     * matters: the day carries no away band, <em>and</em> the last fact the schedule holds on or
     * before that day left him in a home city.
     * <p>
     * Not-away is not the same as home. The band is built from nights the walk fills
     * <em>between</em> points, so a trip with no return booked yet — flown out, no hotel entered,
     * nothing after it — bands nothing, and a view trusting the band alone would announce he is
     * home while he is abroad. Asking where the last fact left him gets that case right and, with
     * no home configured or no facts at all, answers false rather than guessing.
     */
    public boolean atHomeOn(LocalDate date) {
        Map.Entry<LocalDate, Boolean> lastFact = cachedHomeByLastFact.floorEntry(date);
        return lastFact != null
               && lastFact.getValue()
               && !cachedAwayDays.contains(date);
    }

    private ScheduleTimeline timeline() {
        List<ScheduleTimeline.Occupancy> occupancies = new ArrayList<>();
        occupancies.addAll(conferences.values());
        occupancies.addAll(gatherings.values());
        occupancies.addAll(privateEvents.values());
        return new ScheduleTimeline(List.copyOf(hotelStays.values()), occupancies,
                allLegs(), homeCities);
    }

    private List<ScheduleContext> computeContext() {
        List<ScheduleContext> context = new ArrayList<>();
        for (ScheduleTimeline.Occupancy conference : conferences.values()) {
            context.add(new ScheduleContext.Conference(conference.name(), conference.city(),
                    conference.firstDay(), conference.lastDay()));
        }
        for (ScheduleTimeline.Occupancy gathering : gatherings.values()) {
            context.add(new ScheduleContext.Gathering(gathering.name(), gathering.city(),
                    gathering.firstDay(), gathering.lastDay()));
        }
        for (ScheduleTimeline.Occupancy privateEvent : privateEvents.values()) {
            context.add(new ScheduleContext.PrivateEvent(privateEvent.name(), privateEvent.city(),
                    privateEvent.firstDay(), privateEvent.lastDay()));
        }
        for (ScheduleTimeline.Stay stay : hotelStays.values()) {
            context.add(new ScheduleContext.Stay(stay.city(), stay.checkInDay(), stay.checkOutDay()));
        }
        for (ScheduleTimeline.Movement leg : allLegs()) {
            context.add(new ScheduleContext.Travel(leg.fromCity(), leg.toCity(),
                    leg.departureDay(), leg.arrivalDay()));
        }
        return context.stream()
                .sorted(Comparator.comparing(ScheduleContext::firstDay))
                .toList();
    }

    private List<ScheduleProblem> computeProblems(ScheduleTimeline timeline) {
        List<ScheduleProblem> problems = new ArrayList<>();
        problems.addAll(timeline.missingTravel());
        problems.addAll(timeline.missingHotels());
        problems.addAll(timeline.duplicateHotels());
        problems.addAll(overlappingOccupancies());
        problems.addAll(differentCityConflicts());

        return problems.stream()
                .sorted(Comparator.comparing(this::firstDate))
                .toList();
    }

    private LocalDate firstDate(ScheduleProblem problem) {
        return switch (problem) {
            case ScheduleProblem.MissingTravel mt -> mt.arrivedAt().localDateTime().toLocalDate();
            case ScheduleProblem.MissingHotel mh -> mh.checkIn();
            case ScheduleProblem.DuplicateHotel dh -> dh.firstNight();
            case ScheduleProblem.SchedulingConflict sc -> sc.first().startsAt().localDateTime().toLocalDate();
            case ScheduleProblem.DifferentCityConflict dc -> dc.date();
        };
    }

    private List<ScheduleTimeline.Movement> allLegs() {
        return Stream.of(flightLegs.values().stream(), trainLegs.values().stream(),
                         groundTransfers.values().stream())
                .flatMap(legs -> legs)
                .sorted(Comparator.comparing(leg -> leg.departure().utc()))
                .toList();
    }

    private ScheduleTimeline.Movement flightLeg(AirportCode dep, ZonedTimestamp depDt,
                                                AirportCode arr, ZonedTimestamp arrDt) {
        return new ScheduleTimeline.Movement(cityResolver.cityFor(dep.code()), depDt,
                cityResolver.cityFor(arr.code()), arrDt);
    }

    /**
     * Two things Ted has said he will attend, at the same time. Private events count: being at a
     * dinner and at a meetup at once is the same impossibility as two meetups, and Ted asked for
     * private events to be treated like gatherings throughout the schedule problems.
     */
    private List<ScheduleProblem> overlappingOccupancies() {
        List<ScheduleTimeline.Occupancy> attended = new ArrayList<>(gatherings.values());
        attended.addAll(privateEvents.values());
        List<ScheduleProblem> conflicts = new ArrayList<>();
        for (int i = 0; i < attended.size(); i++) {
            for (int j = i + 1; j < attended.size(); j++) {
                ScheduleTimeline.Occupancy first = attended.get(i);
                ScheduleTimeline.Occupancy second = attended.get(j);
                if (first.overlapsWith(second)) {
                    conflicts.add(new ScheduleProblem.SchedulingConflict(
                            conflicting(first), conflicting(second)));
                }
            }
        }
        return conflicts;
    }

    /**
     * A gathering in one city while a conference runs in another. Detection compares instants, not
     * local dates — that hid a Tokyo morning gathering overlapping the last afternoon of a Chicago
     * conference, because the local dates never lined up.
     * <p>
     * Private events are excluded here, unlike in {@link #overlappingOccupancies()}: clearing one
     * of these is keyed to a {@code GatheringId}, and a private event has no such id to record
     * against. Nothing about the detection resists them; the clearing mechanism does.
     */
    private List<ScheduleProblem> differentCityConflicts() {
        List<ScheduleProblem> conflicts = new ArrayList<>();
        for (Map.Entry<GatheringId, ScheduleTimeline.Occupancy> ge : gatherings.entrySet()) {
            GatheringId gatheringId = ge.getKey();
            ScheduleTimeline.Occupancy gathering = ge.getValue();
            for (Map.Entry<ConferenceId, ScheduleTimeline.Occupancy> ce : conferences.entrySet()) {
                ConferenceId conferenceId = ce.getKey();
                ScheduleTimeline.Occupancy conference = ce.getValue();
                boolean differentCity = !gathering.city().equalsIgnoreCase(conference.city());
                boolean alreadyCleared = clearedConflicts.contains(new ClearedConflict(gatheringId, conferenceId));
                if (gathering.overlapsWith(conference) && differentCity && !alreadyCleared) {
                    conflicts.add(new ScheduleProblem.DifferentCityConflict(
                            gathering.name(), gathering.city(),
                            conference.name(), conference.city(),
                            gathering.firstDay(),
                            gatheringId, conferenceId));
                }
            }
        }
        return conflicts;
    }

    private ScheduleProblem.ConflictingGathering conflicting(ScheduleTimeline.Occupancy occupancy) {
        return new ScheduleProblem.ConflictingGathering(occupancy.name(), occupancy.city(),
                occupancy.startsAt(), occupancy.endsAt());
    }

    private record ClearedConflict(GatheringId gatheringId, ConferenceId conferenceId) {}
}
