package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ScheduleGapProjector implements EventStreamConsumer {

    private final AirportCityResolver cityResolver;
    private final Map<FlightId, TravelLeg> flightLegs = new ConcurrentHashMap<>();
    private final Map<TrainTripId, TravelLeg> trainLegs = new ConcurrentHashMap<>();
    private final Map<HotelBookingId, HotelStay> hotelStays = new ConcurrentHashMap<>();
    private final Map<ConferenceId, CityOccupancy> conferenceOccupancies = new ConcurrentHashMap<>();
    private final Map<GatheringId, GatheringOccupancy> gatheringOccupancies = new ConcurrentHashMap<>();
    private final Set<ClearedConflict> clearedConflicts = new HashSet<>();

    public ScheduleGapProjector(AirportCityResolver cityResolver) {
        this.cityResolver = cityResolver;
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
                case TrainBooked e -> trainLegs.put(e.tripId(), new TravelLeg(
                        e.departureStation().city(), e.departureDateTime(),
                        e.arrivalStation().city(), e.arrivalDateTime()));
                case TrainChanged e -> trainLegs.put(e.tripId(), new TravelLeg(
                        e.departureStation().city(), e.departureDateTime(),
                        e.arrivalStation().city(), e.arrivalDateTime()));
                case HotelBooked e -> hotelStays.put(e.hotelBookingId(), new HotelStay(
                        e.address().locationForMatching(), e.checkIn().toLocalDate(), e.checkOut().toLocalDate()));
                case HotelChanged e -> hotelStays.put(e.hotelBookingId(), new HotelStay(
                        e.address().locationForMatching(), e.checkIn().toLocalDate(), e.checkOut().toLocalDate()));
                case ConferenceTentativelyPlanned e -> conferenceOccupancies.put(e.conferenceId(),
                        new CityOccupancy(e.venueAddress().locationForMatching(),
                                e.startDate(), e.endDate(), e.name()));
                case ConferenceCancelled e -> conferenceOccupancies.remove(e.conferenceId());
                case GatheringPlanned e -> gatheringOccupancies.put(e.gatheringId(),
                        new GatheringOccupancy(e.title(), e.location().locationForMatching(),
                                e.date(), e.startTime(), e.endTime()));
                case DifferentCityConflictCleared e ->
                        clearedConflicts.add(new ClearedConflict(e.gatheringId(), e.conferenceId()));
                default -> {}
            }
        });
    }

    public List<ScheduleProblem> problems() {
        List<TravelLeg> legs = allLegs();
        Set<ScheduleProblem> rawProblems = new LinkedHashSet<>();
        detectMissingTravel(legs, rawProblems);
        detectMissingTravelToFromConferences(legs, rawProblems);
        detectMissingHotel(legs, rawProblems);
        detectGatheringConflicts(rawProblems);
        detectDifferentCityConflicts(rawProblems);

        List<ScheduleProblem> result = new ArrayList<>(deduplicateMissingTravel(rawProblems));
        rawProblems.stream()
                .filter(p -> p instanceof ScheduleProblem.MissingHotel)
                .forEach(result::add);
        rawProblems.stream()
                .filter(p -> p instanceof ScheduleProblem.SchedulingConflict)
                .forEach(result::add);
        rawProblems.stream()
                .filter(p -> p instanceof ScheduleProblem.DifferentCityConflict)
                .forEach(result::add);

        return result.stream()
                .sorted(Comparator.comparing(this::firstDate))
                .toList();
    }

    private List<ScheduleProblem.MissingTravel> deduplicateMissingTravel(Set<ScheduleProblem> rawProblems) {
        List<ScheduleProblem.MissingTravel> sorted = rawProblems.stream()
                .filter(p -> p instanceof ScheduleProblem.MissingTravel)
                .map(p -> (ScheduleProblem.MissingTravel) p)
                .sorted(Comparator.comparing((ScheduleProblem.MissingTravel t) -> t.fromCity().toLowerCase())
                        .thenComparing(t -> t.toCity().toLowerCase())
                        .thenComparing(ScheduleProblem.MissingTravel::arrivedAt))
                .toList();

        List<ScheduleProblem.MissingTravel> result = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            ScheduleProblem.MissingTravel current = sorted.get(i);
            LocalDateTime latestArrival = current.arrivedAt();
            LocalDateTime earliestNextDep = current.nextDepartureAt();

            while (i + 1 < sorted.size()) {
                ScheduleProblem.MissingTravel next = sorted.get(i + 1);
                boolean sameCities = next.fromCity().equalsIgnoreCase(current.fromCity())
                        && next.toCity().equalsIgnoreCase(current.toCity());
                boolean overlaps = next.arrivedAt().isBefore(earliestNextDep);
                if (!sameCities || !overlaps) break;
                if (next.arrivedAt().isAfter(latestArrival)) latestArrival = next.arrivedAt();
                if (next.nextDepartureAt().isBefore(earliestNextDep)) earliestNextDep = next.nextDepartureAt();
                i++;
            }
            result.add(new ScheduleProblem.MissingTravel(
                    current.fromCity(), latestArrival, current.toCity(), earliestNextDep));
            i++;
        }
        return result;
    }

    private LocalDate firstDate(ScheduleProblem p) {
        return switch (p) {
            case ScheduleProblem.MissingTravel mt -> mt.arrivedAt().toLocalDate();
            case ScheduleProblem.MissingHotel mh -> mh.checkIn();
            case ScheduleProblem.SchedulingConflict sc -> sc.date();
            case ScheduleProblem.DifferentCityConflict dc -> dc.date();
        };
    }

    private void detectMissingTravel(List<TravelLeg> legs, Set<ScheduleProblem> problems) {
        for (int i = 0; i < legs.size() - 1; i++) {
            TravelLeg current = legs.get(i);
            TravelLeg next = legs.get(i + 1);
            if (!current.toCity().equalsIgnoreCase(next.fromCity())) {
                problems.add(new ScheduleProblem.MissingTravel(
                        current.toCity(), current.arrival(),
                        next.fromCity(), next.departure()));
            }
        }
    }

    private void detectMissingTravelToFromConferences(List<TravelLeg> legs, Set<ScheduleProblem> problems) {
        for (CityOccupancy conf : conferenceOccupancies.values()) {
            // "To conference": a connecting leg exists if any leg going TO the conference city
            // departs before the conference starts (even if it arrives after it starts).
            boolean hasConnectionToConference = legs.stream()
                    .anyMatch(l -> l.toCity().equalsIgnoreCase(conf.city())
                            && l.departure().isBefore(conf.startDateTime()));
            if (!hasConnectionToConference) {
                legs.stream()
                        .filter(l -> l.arrival().isBefore(conf.startDateTime()))
                        .max(Comparator.comparing(TravelLeg::arrival))
                        .ifPresent(lastLeg -> {
                            if (!lastLeg.toCity().equalsIgnoreCase(conf.city())) {
                                problems.add(new ScheduleProblem.MissingTravel(
                                        lastLeg.toCity(), lastLeg.arrival(),
                                        conf.city(), conf.startDateTime()));
                            }
                        });
            }

            // "From conference": a connecting leg exists if any leg FROM the conference city
            // departs after the conference starts (even before it officially ends).
            boolean hasConnectionFromConference = legs.stream()
                    .anyMatch(l -> l.fromCity().equalsIgnoreCase(conf.city())
                            && l.departure().isAfter(conf.startDateTime()));
            if (!hasConnectionFromConference) {
                legs.stream()
                        .filter(l -> l.departure().isAfter(conf.endDateTime()))
                        .min(Comparator.comparing(TravelLeg::departure))
                        .ifPresent(firstLeg -> {
                            if (!conf.city().equalsIgnoreCase(firstLeg.fromCity())) {
                                problems.add(new ScheduleProblem.MissingTravel(
                                        conf.city(), conf.endDateTime(),
                                        firstLeg.fromCity(), firstLeg.departure()));
                            }
                        });
            }
        }
    }

    private void detectMissingHotel(List<TravelLeg> legs, Set<ScheduleProblem> problems) {
        Set<CityNight> neededNights = new LinkedHashSet<>();

        for (TravelLeg leg : legs) {
            String city = leg.toCity();
            LocalDate arrivalDate = leg.arrival().toLocalDate();
            nextDepartureFromCity(legs, city, leg.arrival()).ifPresent(nextDeparture -> {
                for (LocalDate night = arrivalDate; night.isBefore(nextDeparture); night = night.plusDays(1)) {
                    LocalDate finalNight = night;
                    boolean conferenceElsewhere = conferenceOccupancies.values().stream()
                            .anyMatch(occ -> !occ.city().equalsIgnoreCase(city)
                                    && !finalNight.isBefore(occ.startDate())
                                    && finalNight.isBefore(occ.endDate()));
                    boolean hotelInConferenceCity = hotelStays.values().stream()
                            .anyMatch(stay -> !stay.city().equalsIgnoreCase(city)
                                    && stay.coversNight(finalNight)
                                    && isConferenceCity(stay.city()));
                    if (!conferenceElsewhere && !hotelInConferenceCity) {
                        neededNights.add(new CityNight(city, finalNight));
                    }
                }
            });
        }

        for (CityOccupancy occ : conferenceOccupancies.values()) {
            for (LocalDate night = occ.startDate(); night.isBefore(occ.endDate()); night = night.plusDays(1)) {
                neededNights.add(new CityNight(occ.city(), night));
            }
        }

        List<CityNight> uncovered = neededNights.stream()
                .filter(cn -> hotelStays.values().stream()
                        .noneMatch(stay -> stay.city().equalsIgnoreCase(cn.city())
                                && stay.coversNight(cn.night())))
                .sorted(Comparator.comparing((CityNight cn) -> cn.city().toLowerCase())
                        .thenComparing(CityNight::night))
                .toList();

        int i = 0;
        while (i < uncovered.size()) {
            CityNight first = uncovered.get(i);
            LocalDate lastNight = first.night();
            while (i + 1 < uncovered.size()
                    && uncovered.get(i + 1).city().equalsIgnoreCase(first.city())
                    && uncovered.get(i + 1).night().equals(lastNight.plusDays(1))) {
                i++;
                lastNight = uncovered.get(i).night();
            }
            LocalDate checkIn = first.night();
            LocalDate checkOut = lastNight.plusDays(1);
            String conferenceName = conferenceNameFor(first.city(), checkIn, checkOut);
            problems.add(new ScheduleProblem.MissingHotel(first.city(), checkIn, checkOut, conferenceName));
            i++;
        }
    }

    private String conferenceNameFor(String city, LocalDate checkIn, LocalDate checkOut) {
        return conferenceOccupancies.values().stream()
                .filter(occ -> occ.city().equalsIgnoreCase(city)
                        && occ.startDate().isBefore(checkOut)
                        && occ.endDate().isAfter(checkIn))
                .map(CityOccupancy::name)
                .findFirst()
                .orElse("");
    }

    private boolean isConferenceCity(String city) {
        return conferenceOccupancies.values().stream()
                .anyMatch(occ -> occ.city().equalsIgnoreCase(city));
    }

    private Optional<LocalDate> nextDepartureFromCity(List<TravelLeg> legs, String city, LocalDateTime afterTime) {
        return legs.stream()
                .filter(l -> l.fromCity().equalsIgnoreCase(city) && l.departure().isAfter(afterTime))
                .map(l -> l.departure().toLocalDate())
                .min(Comparator.naturalOrder());
    }

    private List<TravelLeg> allLegs() {
        return Stream.concat(flightLegs.values().stream(), trainLegs.values().stream())
                .sorted(Comparator.comparing(TravelLeg::departure))
                .toList();
    }

    private TravelLeg flightLeg(AirportCode dep, LocalDateTime depDt, AirportCode arr, LocalDateTime arrDt) {
        return new TravelLeg(cityResolver.cityFor(dep.code()), depDt,
                cityResolver.cityFor(arr.code()), arrDt);
    }

    private void detectDifferentCityConflicts(Set<ScheduleProblem> problems) {
        for (Map.Entry<GatheringId, GatheringOccupancy> ge : gatheringOccupancies.entrySet()) {
            GatheringId gatheringId = ge.getKey();
            GatheringOccupancy gathering = ge.getValue();
            for (Map.Entry<ConferenceId, CityOccupancy> ce : conferenceOccupancies.entrySet()) {
                ConferenceId conferenceId = ce.getKey();
                CityOccupancy conf = ce.getValue();
                boolean gatheringDuringConference =
                        !gathering.date().isBefore(conf.startDate())
                        && gathering.date().isBefore(conf.endDate());
                boolean differentCity = !gathering.city().equalsIgnoreCase(conf.city());
                boolean alreadyCleared = clearedConflicts.contains(new ClearedConflict(gatheringId, conferenceId));
                if (gatheringDuringConference && differentCity && !alreadyCleared) {
                    problems.add(new ScheduleProblem.DifferentCityConflict(
                            gathering.name(), gathering.city(),
                            conf.name(), conf.city(),
                            gathering.date(),
                            gatheringId, conferenceId));
                }
            }
        }
    }

    private void detectGatheringConflicts(Set<ScheduleProblem> problems) {
        List<GatheringOccupancy> gatherings = new ArrayList<>(gatheringOccupancies.values());
        for (int i = 0; i < gatherings.size(); i++) {
            for (int j = i + 1; j < gatherings.size(); j++) {
                GatheringOccupancy a = gatherings.get(i);
                GatheringOccupancy b = gatherings.get(j);
                if (a.overlapsWith(b)) {
                    problems.add(new ScheduleProblem.SchedulingConflict(
                            a.name(), a.startTime(), a.endTime(),
                            b.name(), b.startTime(), b.endTime(),
                            a.date()));
                }
            }
        }
    }

    private record TravelLeg(String fromCity, LocalDateTime departure, String toCity, LocalDateTime arrival) {}

    private record HotelStay(String city, LocalDate checkIn, LocalDate checkOut) {
        boolean coversNight(LocalDate night) {
            return !night.isBefore(checkIn) && night.isBefore(checkOut);
        }
    }

    private record CityOccupancy(String city, LocalDateTime startDateTime, LocalDateTime endDateTime, String name) {
        LocalDate startDate() { return startDateTime.toLocalDate(); }
        LocalDate endDate() { return endDateTime.toLocalDate(); }
    }

    private record CityNight(String city, LocalDate night) {}

    private record ClearedConflict(GatheringId gatheringId, ConferenceId conferenceId) {}

    private record GatheringOccupancy(String name, String city, LocalDate date, LocalTime startTime, LocalTime endTime) {
        boolean overlapsWith(GatheringOccupancy other) {
            return this.date.equals(other.date)
                    && this.startTime.isBefore(other.endTime)
                    && other.startTime.isBefore(this.endTime);
        }
    }
}
