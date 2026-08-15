package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * A detected gap or clash in the schedule. It is {@link TemporalView} so a read can drop the
 * problems that are already in the past — an unbookable gap or an unresolvable clash that has
 * already happened is not actionable. {@link #relevantUntil()} is the instant after which the
 * problem is moot: the close of the window in which you could still have done something about it.
 */
public sealed interface ScheduleProblem extends TemporalView
        permits ScheduleProblem.MissingTravel, ScheduleProblem.MissingHotel,
                ScheduleProblem.SchedulingConflict, ScheduleProblem.DifferentCityConflict {

    /**
     * The two endpoints are in different cities and therefore usually different zones, so they are
     * {@link ZonedTimestamp}s: the gap between them is only meaningful as a comparison of instants.
     * Renderers show {@code localDateTime()} — the wall-clock at each end.
     */
    record MissingTravel(
            String fromCity,
            ZonedTimestamp arrivedAt,
            String toCity,
            ZonedTimestamp nextDepartureAt
    ) implements ScheduleProblem {
        // The window to insert the missing leg closes when the next leg departs.
        @Override
        public Instant relevantUntil() {
            return nextDepartureAt.utc();
        }
    }

    record MissingHotel(
            String city,
            LocalDate checkIn,
            LocalDate checkOut,
            String conferenceName
    ) implements ScheduleProblem {
        // No zone survives the night-bucketing, so checkout is read at start-of-day UTC — a
        // documented day-granularity stopgap (see TemporalView). Once checkout has passed, every
        // night the stay would have covered is behind us.
        @Override
        public Instant relevantUntil() {
            return checkOut.atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }

    /**
     * Two gatherings whose instants overlap. Each side carries its <em>own</em>
     * {@link ZonedTimestamp}s and city: overlapping gatherings in different zones can fall on
     * different local dates (a San Francisco evening and a Tokyo morning), so there is no single
     * date to report — showing one gathering's date beside the other's times reads as wrong
     * exactly when the instant-based detection has done its job.
     */
    record SchedulingConflict(
            ConflictingGathering first,
            ConflictingGathering second
    ) implements ScheduleProblem {
        // The clash matters until both gatherings have ended.
        @Override
        public Instant relevantUntil() {
            Instant firstEnd = first.endsAt().utc();
            Instant secondEnd = second.endsAt().utc();
            return firstEnd.isAfter(secondEnd) ? firstEnd : secondEnd;
        }
    }

    record ConflictingGathering(
            String name,
            String city,
            ZonedTimestamp startsAt,
            ZonedTimestamp endsAt
    ) {}

    record DifferentCityConflict(
            String gatheringName,
            String gatheringCity,
            String conferenceName,
            String conferenceCity,
            LocalDate date,
            GatheringId gatheringId,
            ConferenceId conferenceId
    ) implements ScheduleProblem {
        // Only the conflict date is carried; keep it through the end of that day (start-of-next-day
        // UTC — the same day-granularity stopgap as MissingHotel).
        @Override
        public Instant relevantUntil() {
            return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }
}
