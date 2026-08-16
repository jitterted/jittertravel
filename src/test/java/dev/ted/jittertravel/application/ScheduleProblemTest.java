package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary coverage for {@link ScheduleProblem#relevantUntil()} — the instant after which a
 * problem is dropped from the owner's FUTURE report.
 * <p>
 * The two day-granularity variants ({@link ScheduleProblem.MissingHotel},
 * {@link ScheduleProblem.DifferentCityConflict}) carry a bare {@link LocalDate}. They anchor their
 * boundary at "Anywhere on Earth" (UTC-12) rather than UTC so that a problem stays surfaced until
 * its date has passed <em>everywhere the owner could be</em> — critically, west of UTC, where the
 * owner actually lives (SFO) and travels (the Americas). A UTC anchor drops such a problem during
 * the previous local afternoon; the AoE anchor never drops one earlier than a UTC anchor would.
 */
class ScheduleProblemTest {

    // The owner is realistically somewhere between Hawaii and Tokyo. August => US on PDT (UTC-7).
    private static final ZoneId SFO = ZoneId.of("America/Los_Angeles");
    private static final ZoneId HAWAII = ZoneId.of("Pacific/Honolulu"); // UTC-10, no DST
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");        // UTC+9, no DST
    private static final ZoneOffset ANYWHERE_ON_EARTH = ZoneOffset.ofHours(-12);

    private static final LocalDate AUG_16 = LocalDate.of(2026, 8, 16);

    @Nested
    class MissingHotelBoundary {

        // checkOut Aug 16 => stay's last night is Aug 15 -> Aug 16; the boundary is the start of
        // Aug 16 at UTC-12, i.e. Aug 16 12:00 UTC.
        private final ScheduleProblem.MissingHotel problem =
                new ScheduleProblem.MissingHotel("San Francisco", LocalDate.of(2026, 8, 14), AUG_16, "");

        @Test
        void anchorsAtStartOfCheckoutDayAnywhereOnEarth() {
            assertThat(problem.relevantUntil())
                    .isEqualTo(Instant.parse("2026-08-16T12:00:00Z"));
        }

        @Test
        void neverEarlierThanAUtcAnchorWouldHaveDroppedIt() {
            // The whole point of the fix: AoE pushes the boundary 12h later than a UTC anchor,
            // never earlier — so it cannot hide a problem the old UTC anchor would have shown.
            Instant utcAnchor = AUG_16.atStartOfDay(ZoneOffset.UTC).toInstant();

            assertThat(problem.relevantUntil())
                    .as("AoE boundary is 12h after the UTC-midnight anchor")
                    .isEqualTo(utcAnchor.plusSeconds(12 * 3600))
                    .isAfter(utcAnchor);
        }

        @Test
        void stillSurfacedLateOnTheLastNightInSanFrancisco() {
            // 11pm on the last night the owner needs the room. A UTC anchor (Aug 16 00:00Z =
            // Aug 15 17:00 PDT) has already dropped it by now; the AoE anchor keeps it.
            Instant elevenPmLastNightSfo = LocalDateTime.of(2026, 8, 15, 23, 0).atZone(SFO).toInstant();

            assertThat(AUG_16.atStartOfDay(ZoneOffset.UTC).toInstant().isBefore(elevenPmLastNightSfo))
                    .as("a UTC anchor would already have dropped it by 11pm PDT")
                    .isTrue();
            assertThat(TimeView.FUTURE.includes(problem, elevenPmLastNightSfo))
                    .as("the AoE anchor still surfaces the missing hotel on its last night")
                    .isTrue();
        }

        @Test
        void stillSurfacedLateOnTheLastNightInHawaii() {
            // Westmost inhabited case the owner could plausibly reach: 11pm HST (UTC-10).
            Instant elevenPmLastNightHawaii = LocalDateTime.of(2026, 8, 15, 23, 0).atZone(HAWAII).toInstant();

            assertThat(TimeView.FUTURE.includes(problem, elevenPmLastNightHawaii))
                    .as("still surfaced at 11pm Hawaii time on the last night")
                    .isTrue();
        }

        @Test
        void includesTheExactBoundaryInstantAndExcludesOneSecondLater() {
            Instant boundary = Instant.parse("2026-08-16T12:00:00Z");

            assertThat(TimeView.FUTURE.includes(problem, boundary))
                    .as("FUTURE is inclusive of the boundary instant (checkout morning at UTC-12)")
                    .isTrue();
            assertThat(TimeView.FUTURE.includes(problem, boundary.plusSeconds(1)))
                    .as("dropped one second past the westmost checkout morning")
                    .isFalse();
        }

        @Test
        void lingersHarmlesslyIntoCheckoutDayEastOfUtc() {
            // The documented cost: east of UTC the problem lingers past when the last night ended.
            // 5pm Tokyo on checkout day is still shown (boundary is 9pm JST). Harmless over-surfacing.
            Instant fivePmCheckoutDayTokyo = LocalDateTime.of(2026, 8, 16, 17, 0).atZone(TOKYO).toInstant();

            assertThat(TimeView.FUTURE.includes(problem, fivePmCheckoutDayTokyo))
                    .as("a moot problem lingering into checkout-day evening in Tokyo is the accepted papercut")
                    .isTrue();
        }
    }

    @Nested
    class DifferentCityConflictBoundary {

        // Conflict on Aug 16; kept through the end of that day, read at UTC-12: start of Aug 17 at
        // UTC-12 = Aug 17 12:00 UTC.
        private final ScheduleProblem.DifferentCityConflict problem =
                new ScheduleProblem.DifferentCityConflict(
                        "Dinner", "San Francisco", "QCon", "New York",
                        AUG_16, GatheringId.random(), ConferenceId.random());

        @Test
        void anchorsAtStartOfTheDayAfterTheConflictAnywhereOnEarth() {
            assertThat(problem.relevantUntil())
                    .isEqualTo(Instant.parse("2026-08-17T12:00:00Z"));
        }

        @Test
        void neverEarlierThanAUtcAnchorWouldHaveDroppedIt() {
            Instant utcAnchor = AUG_16.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

            assertThat(problem.relevantUntil())
                    .as("AoE boundary is 12h after the UTC end-of-day anchor")
                    .isEqualTo(utcAnchor.plusSeconds(12 * 3600))
                    .isAfter(utcAnchor);
        }

        @Test
        void stillSurfacedThroughTheConflictEveningInSanFrancisco() {
            // 11pm on the conflict day itself. A UTC anchor (Aug 17 00:00Z = Aug 16 17:00 PDT) has
            // dropped it hours earlier; the AoE anchor keeps it through the whole conflict day.
            Instant elevenPmConflictDaySfo = LocalDateTime.of(2026, 8, 16, 23, 0).atZone(SFO).toInstant();

            assertThat(AUG_16.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().isBefore(elevenPmConflictDaySfo))
                    .as("a UTC anchor would already have dropped it by 11pm PDT on the conflict day")
                    .isTrue();
            assertThat(TimeView.FUTURE.includes(problem, elevenPmConflictDaySfo))
                    .as("the AoE anchor still surfaces the conflict on its own evening")
                    .isTrue();
        }

        @Test
        void includesTheExactBoundaryInstantAndExcludesOneSecondLater() {
            Instant boundary = Instant.parse("2026-08-17T12:00:00Z");

            assertThat(TimeView.FUTURE.includes(problem, boundary))
                    .as("FUTURE is inclusive of the boundary instant (end of conflict day at UTC-12)")
                    .isTrue();
            assertThat(TimeView.FUTURE.includes(problem, boundary.plusSeconds(1)))
                    .as("dropped one second past the westmost end of the conflict day")
                    .isFalse();
        }
    }

    /**
     * The two variants backed by {@link ZonedTimestamp}s already anchor to true instants, so they
     * are correct regardless of zone and are deliberately untouched. These guard that they keep
     * reading the endpoint instant directly rather than drifting to a day boundary.
     */
    @Nested
    class InstantBackedVariantsAnchorToTheirEndpointInstant {

        @Test
        void missingTravelIsRelevantUntilTheNextDeparture() {
            Instant nextDeparture = Instant.parse("2026-08-16T09:30:00Z");
            ScheduleProblem.MissingTravel problem = new ScheduleProblem.MissingTravel(
                    "Lisbon", at("2026-08-16T04:00:00Z"),
                    "Casablanca", new ZonedTimestamp(nextDeparture, ZoneId.of("Africa/Casablanca")));

            assertThat(problem.relevantUntil())
                    .isEqualTo(nextDeparture);
        }

        @Test
        void schedulingConflictIsRelevantUntilTheLaterOfTheTwoEnds() {
            Instant earlierEnd = Instant.parse("2026-08-16T10:00:00Z");
            Instant laterEnd = Instant.parse("2026-08-16T14:00:00Z");

            ScheduleProblem.SchedulingConflict secondEndsLater = new ScheduleProblem.SchedulingConflict(
                    conflicting("Talk", at("2026-08-16T09:00:00Z"), new ZonedTimestamp(earlierEnd, ZoneOffset.UTC)),
                    conflicting("Panel", at("2026-08-16T12:00:00Z"), new ZonedTimestamp(laterEnd, ZoneOffset.UTC)));
            ScheduleProblem.SchedulingConflict firstEndsLater = new ScheduleProblem.SchedulingConflict(
                    conflicting("Panel", at("2026-08-16T12:00:00Z"), new ZonedTimestamp(laterEnd, ZoneOffset.UTC)),
                    conflicting("Talk", at("2026-08-16T09:00:00Z"), new ZonedTimestamp(earlierEnd, ZoneOffset.UTC)));

            assertThat(secondEndsLater.relevantUntil())
                    .as("later end wins when it is the second gathering")
                    .isEqualTo(laterEnd);
            assertThat(firstEndsLater.relevantUntil())
                    .as("later end wins when it is the first gathering")
                    .isEqualTo(laterEnd);
        }

        private static ScheduleProblem.ConflictingGathering conflicting(String name, ZonedTimestamp start, ZonedTimestamp end) {
            return new ScheduleProblem.ConflictingGathering(name, "London", start, end);
        }
    }

    private static ZonedTimestamp at(String instant) {
        return new ZonedTimestamp(Instant.parse(instant), ZoneOffset.UTC);
    }
}
