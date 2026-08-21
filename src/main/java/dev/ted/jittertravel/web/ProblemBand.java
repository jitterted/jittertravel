package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One {@link ScheduleProblem} placed on the problem calendar: a run of days ({@code firstDay}
 * through {@code lastDay}, both inclusive) wearing one {@link Marker}, carrying display-ready text.
 * <p>
 * This is the problem calendar's own view type. It deliberately shares nothing with
 * {@code CalendarEntry}, which is shaped for the public calendar and is about to be split by the
 * S2+E2 refactor — see {@code docs/ProblemCalendarPlan.md}.
 * <p>
 * {@code fixes} come from {@link ProblemFix#forProblem}, the same mapping the list cards read, so
 * the two views can never offer different answers to the same problem.
 */
public record ProblemBand(Marker marker, LocalDate firstDay, LocalDate lastDay, String title, String detail,
                          List<ProblemFix> fixes) {

    public ProblemBand {
        fixes = List.copyOf(fixes);
    }

    /**
     * A band with no fixes — {@code SchedulingConflict}, whose sides carry no ids. Unlike a
     * card, a band with nothing to offer simply is not an anchor: there is no slot vocabulary on
     * the calendar to keep consistent (F6).
     */
    ProblemBand(Marker marker, LocalDate firstDay, LocalDate lastDay, String title, String detail) {
        this(marker, firstDay, lastDay, title, detail, List.of());
    }

    /** Which row-stack this band is packed into; the marker decides. */
    public Lane lane() {
        return marker.lane();
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter ZONE_ABBREVIATION = DateTimeFormatter.ofPattern("z", Locale.ENGLISH);

    /**
     * The lanes stacked down each week, in this order. A band occupies its lane's sub-rows;
     * overlapping bands in the same lane stack into extra sub-rows.
     */
    public enum Lane {
        BED,
        DUPLICATE,
        TRAVEL,
        CLASH
    }

    /**
     * What a band looks like, and which {@link Lane} it is packed into. Usually one marker per
     * lane — {@link Lane#CLASH} has two, because a city clash and a scheduling clash are one
     * concern (two things at once) that the list view already colours apart, violet and red.
     * Sharing the lane is what lets them share its sub-rows on a day that has both.
     */
    public enum Marker {
        BED(Lane.BED),
        DUPLICATE(Lane.DUPLICATE),
        TRAVEL(Lane.TRAVEL),
        CLASH_CITY(Lane.CLASH),
        CLASH_SCHEDULING(Lane.CLASH);

        private final Lane lane;

        Marker(Lane lane) {
            this.lane = lane;
        }

        public Lane lane() {
            return lane;
        }

        /** The band's CSS modifier: {@code pc-band--bed}, {@code pc-band--clash-city}. */
        public String cssModifier() {
            return name().toLowerCase(Locale.ENGLISH).replace('_', '-');
        }
    }

    /**
     * The problem-to-band mapping, as an exhaustive switch over the sealed {@link ScheduleProblem}:
     * a new problem type cannot be added without deciding here how it lands on the calendar.
     */
    public static ProblemBand from(ScheduleProblem problem) {
        return switch (problem) {
            case ScheduleProblem.MissingHotel missingHotel -> bedBand(missingHotel);
            case ScheduleProblem.MissingTravel missingTravel -> travelBand(missingTravel);
            case ScheduleProblem.DuplicateHotel duplicateHotel -> duplicateBand(duplicateHotel);
            case ScheduleProblem.SchedulingConflict overlap -> schedulingClashBand(overlap);
            case ScheduleProblem.DifferentCityConflict cityConflict -> cityClashBand(cityConflict);
        };
    }

    /**
     * A missing stay covers <em>nights</em>, so the band ends on the night before checkout: a
     * check-in on the 3rd and checkout on the 6th is three uncovered nights (3rd, 4th, 5th), and
     * painting the 6th would claim a bed was needed on a day the traveller has left.
     */
    private static ProblemBand bedBand(ScheduleProblem.MissingHotel missingHotel) {
        LocalDate checkIn = missingHotel.checkIn();
        LocalDate lastNight = missingHotel.checkOut().isAfter(checkIn)
                ? missingHotel.checkOut().minusDays(1)
                : checkIn;
        return new ProblemBand(Marker.BED, checkIn, lastNight,
                "No hotel — " + missingHotel.city(),
                detail(nightsBetween(checkIn, lastNight), missingHotel.conferenceName()),
                ProblemFix.forProblem(missingHotel));
    }

    /**
     * Doubly-booked nights are already a run of nights, so the band is that run exactly — first
     * night through last, with no checkout day to trim off.
     */
    private static ProblemBand duplicateBand(ScheduleProblem.DuplicateHotel duplicateHotel) {
        String hotels = duplicateHotel.stays().stream()
                .map(ScheduleProblem.DuplicateStay::hotelName)
                .collect(Collectors.joining(" · "));
        return new ProblemBand(Marker.DUPLICATE, duplicateHotel.firstNight(), duplicateHotel.lastNight(),
                duplicateHotel.stays().size() + " hotels — " + hotels,
                nightsBetween(duplicateHotel.firstNight(), duplicateHotel.lastNight())
                + " nights booked twice",
                ProblemFix.forProblem(duplicateHotel));
    }

    /**
     * A gap between two cities covers the days from the arrival that stranded you to the departure
     * you have to make. Each end is read in <em>its own</em> zone, because that is the day the
     * traveller is living at that end.
     * <p>
     * The two ends can come out in the wrong order — a Tokyo morning arrival and a San Francisco
     * evening departure are ordered one way as instants and the other way as local dates — so the
     * band is placed across the span of both rather than from one to the other, which would render
     * as nothing at all.
     */
    private static ProblemBand travelBand(ScheduleProblem.MissingTravel missingTravel) {
        LocalDate arrivalDay = missingTravel.arrivedAt().localDateTime().toLocalDate();
        LocalDate departureDay = missingTravel.nextDepartureAt().localDateTime().toLocalDate();
        LocalDate firstDay = arrivalDay.isBefore(departureDay) ? arrivalDay : departureDay;
        LocalDate lastDay = arrivalDay.isAfter(departureDay) ? arrivalDay : departureDay;
        // A gap out of home has no stranded stretch: its window is the single moment he has to be
        // somewhere else, so the band is that one day and naming the same time twice reads as an
        // error. See ScheduleTimeline.gapLeaving.
        String detail = missingTravel.arrivedAt().equals(missingTravel.nextDepartureAt())
                ? "Nothing booked · needed by " + zonedTime(missingTravel.nextDepartureAt())
                : "Arrive " + zonedTime(missingTravel.arrivedAt())
                  + " · depart " + zonedTime(missingTravel.nextDepartureAt());
        return new ProblemBand(Marker.TRAVEL, firstDay, lastDay,
                "No travel — " + missingTravel.fromCity() + " → " + missingTravel.toCity(),
                detail,
                ProblemFix.forProblem(missingTravel));
    }

    /**
     * A gathering somewhere the conference of the day is not. The conflict record carries a single
     * {@link LocalDate}, so this is a one-day band; the two cities — the whole of what is wrong —
     * go in the detail.
     */
    private static ProblemBand cityClashBand(ScheduleProblem.DifferentCityConflict conflict) {
        return new ProblemBand(Marker.CLASH_CITY, conflict.date(), conflict.date(),
                "City clash — " + conflict.gatheringName() + " · " + conflict.conferenceName(),
                conflict.gatheringCity() + " vs " + conflict.conferenceCity(),
                ProblemFix.forProblem(conflict));
    }

    /**
     * Two gatherings whose instants overlap. There is no single date to place it on: each side
     * carries its own zone, and a San Francisco evening overlaps a Tokyo morning that falls on the
     * next local date — so the band spans every local date either side touches, min through max.
     * Taking one side's dates, or subtracting one from the other, is the same inversion bug the
     * travel band has (a band from the later day to the earlier one renders as nothing at all).
     * <p>
     * The detail names each side's start <em>with its zone</em>, for the same reason: "6:30 PM ·
     * 10:00 AM" invites the reader to subtract two numbers off different clocks.
     */
    private static ProblemBand schedulingClashBand(ScheduleProblem.SchedulingConflict overlap) {
        List<LocalDate> days = Stream.of(
                        overlap.first().startsAt(), overlap.first().endsAt(),
                        overlap.second().startsAt(), overlap.second().endsAt())
                .map(moment -> moment.localDateTime().toLocalDate())
                .sorted()
                .toList();
        // No fixes: neither side carries an id, so there is nothing to link to (F6). Unlike a
        // card, a band with nothing to offer is simply not an anchor.
        return new ProblemBand(Marker.CLASH_SCHEDULING, days.getFirst(), days.getLast(),
                "Clash — " + overlap.first().name() + " · " + overlap.second().name(),
                zonedTime(overlap.first().startsAt()) + " · " + zonedTime(overlap.second().startsAt()));
    }

    /**
     * The wall-clock at one end, with its zone named. The two ends of a gap are usually in
     * different zones, so a bare "2:30 PM … 9:00 AM" would invite the reader to subtract two
     * numbers that do not belong to the same clock.
     */
    private static String zonedTime(ZonedTimestamp moment) {
        return TIME.format(moment.atEntryZone()) + " " + ZONE_ABBREVIATION.format(moment.atEntryZone());
    }

    private static long nightsBetween(LocalDate checkIn, LocalDate lastNight) {
        return ChronoUnit.DAYS.between(checkIn, lastNight) + 1;
    }

    private static String detail(long nights, String conferenceName) {
        String nightCount = nights + (nights == 1 ? " night" : " nights");
        return conferenceName.isEmpty() ? nightCount : nightCount + " — " + conferenceName;
    }
}
