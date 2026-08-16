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
     * "Anywhere on Earth" (UTC-12) — the westmost civil offset. Day-granularity problems
     * ({@link MissingHotel}, {@link DifferentCityConflict}) carry a bare {@link LocalDate} with no
     * zone, so their {@link #relevantUntil()} has to pick one. Anchoring the boundary here means the
     * problem stays surfaced until that date has passed <em>everywhere the owner could be</em> —
     * at home in SFO or on the road anywhere on the planet. These reports are the owner's safety
     * net (a missing bed for tonight is unrecoverable; a row that lingers a few extra hours is a
     * papercut), so the boundary errs west on purpose.
     * <p>
     * Relative to anchoring at UTC, this only ever pushes the boundary <em>later</em> (by up to
     * 12h), never earlier: it cannot drop a problem sooner than a UTC anchor would, so it cannot
     * hide an actionable one. The only cost is a moot problem lingering a little longer.
     */
    ZoneOffset ANYWHERE_ON_EARTH = ZoneOffset.ofHours(-12);

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
        // No zone survives the night-bucketing, so checkout is read at start-of-day Anywhere on
        // Earth (see ANYWHERE_ON_EARTH). The last night of the stay is checkOut-1 -> checkOut, so
        // it is over once checkout morning has arrived; anchoring at UTC-12 keeps the problem live
        // until that morning has arrived even at the westmost point on Earth — never dropping it
        // while the owner, wherever they are, still has that last night ahead of them.
        @Override
        public Instant relevantUntil() {
            return checkOut.atStartOfDay(ANYWHERE_ON_EARTH).toInstant();
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
        // Only the conflict date is carried; keep it through the end of that day, read at
        // Anywhere on Earth (see ANYWHERE_ON_EARTH) — start of the next day at UTC-12, so the
        // conflict stays live until its own date has ended everywhere the owner could be.
        @Override
        public Instant relevantUntil() {
            return date.plusDays(1).atStartOfDay(ANYWHERE_ON_EARTH).toInstant();
        }
    }
}
