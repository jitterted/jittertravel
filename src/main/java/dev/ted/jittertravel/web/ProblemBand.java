package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One {@link ScheduleProblem} placed on the problem calendar: a run of days ({@code firstDay}
 * through {@code lastDay}, both inclusive) in one {@link Lane}, carrying display-ready text.
 * <p>
 * This is the problem calendar's own view type. It deliberately shares nothing with
 * {@code CalendarEntry}, which is shaped for the public calendar and is about to be split by the
 * S2+E2 refactor — see {@code docs/ProblemCalendarPlan.md}.
 */
public record ProblemBand(Lane lane, LocalDate firstDay, LocalDate lastDay, String title, String detail) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter ZONE_ABBREVIATION = DateTimeFormatter.ofPattern("z", Locale.ENGLISH);

    /**
     * The lanes stacked down each week, in this order. A band occupies its lane's sub-rows;
     * overlapping bands in the same lane stack into extra sub-rows.
     * <p>
     * {@code CLASH} arrives with slice 3.
     */
    public enum Lane {
        BED,
        DUPLICATE,
        TRAVEL
    }

    /**
     * The problem-to-band mapping, as an exhaustive switch over the sealed {@link ScheduleProblem}:
     * a new problem type cannot be added without deciding here how it lands on the calendar.
     * Problems whose lane is not built yet return empty, so the calendar shows the lanes it has
     * rather than nothing.
     */
    public static Optional<ProblemBand> from(ScheduleProblem problem) {
        return switch (problem) {
            case ScheduleProblem.MissingHotel missingHotel -> Optional.of(bedBand(missingHotel));
            case ScheduleProblem.MissingTravel missingTravel -> Optional.of(travelBand(missingTravel));
            case ScheduleProblem.DuplicateHotel duplicateHotel -> Optional.of(duplicateBand(duplicateHotel));
            case ScheduleProblem.SchedulingConflict ignored -> Optional.empty();     // slice 3
            case ScheduleProblem.DifferentCityConflict ignored -> Optional.empty();  // slice 3
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
        return new ProblemBand(Lane.BED, checkIn, lastNight,
                "No hotel — " + missingHotel.city(),
                detail(nightsBetween(checkIn, lastNight), missingHotel.conferenceName()));
    }

    /**
     * Doubly-booked nights are already a run of nights, so the band is that run exactly — first
     * night through last, with no checkout day to trim off.
     */
    private static ProblemBand duplicateBand(ScheduleProblem.DuplicateHotel duplicateHotel) {
        String hotels = duplicateHotel.stays().stream()
                .map(ScheduleProblem.DuplicateStay::hotelName)
                .collect(Collectors.joining(" · "));
        return new ProblemBand(Lane.DUPLICATE, duplicateHotel.firstNight(), duplicateHotel.lastNight(),
                duplicateHotel.stays().size() + " hotels — " + hotels,
                nightsBetween(duplicateHotel.firstNight(), duplicateHotel.lastNight())
                + " nights booked twice");
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
        return new ProblemBand(Lane.TRAVEL, firstDay, lastDay,
                "No travel — " + missingTravel.fromCity() + " → " + missingTravel.toCity(),
                detail);
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
