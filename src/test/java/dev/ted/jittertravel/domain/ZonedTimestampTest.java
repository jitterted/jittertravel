package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZonedTimestampTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void fromLocalCapturesWallClockAsInstantInGivenZone() {
        // 11:00 wall-clock in Berlin (CEST, UTC+2 in June) == 09:00 UTC
        ZonedTimestamp timestamp = ZonedTimestamp.fromLocal(
                LocalDateTime.of(2026, 6, 21, 11, 0), BERLIN);

        assertThat(timestamp.utc())
                .isEqualTo(Instant.parse("2026-06-21T09:00:00Z"));
        assertThat(timestamp.zone())
                .isEqualTo(BERLIN);
    }

    @Test
    void localDateTimeRoundTripsTheEnteredWallClock() {
        LocalDateTime wallClock = LocalDateTime.of(2026, 6, 21, 11, 0);

        ZonedTimestamp timestamp = ZonedTimestamp.fromLocal(wallClock, BERLIN);

        assertThat(timestamp.localDateTime())
                .isEqualTo(wallClock);
    }

    @Test
    void atRendersTheSameInstantInAnotherZone() {
        ZonedTimestamp timestamp = ZonedTimestamp.fromLocal(
                LocalDateTime.of(2026, 6, 21, 11, 0), BERLIN);

        assertThat(timestamp.at(ZoneId.of("America/Los_Angeles")).toLocalDateTime())
                .as("09:00 UTC is 02:00 the same day in US Pacific (PDT, UTC-7)")
                .isEqualTo(LocalDateTime.of(2026, 6, 21, 2, 0));
    }

    @Test
    void springForwardGapShiftsForwardLeniently() {
        // 2026-03-29 02:30 does not exist in Berlin (clocks jump 02:00 -> 03:00).
        ZonedTimestamp timestamp = ZonedTimestamp.fromLocal(
                LocalDateTime.of(2026, 3, 29, 2, 30), BERLIN);

        assertThat(timestamp.atEntryZone().getHour())
                .as("a non-existent wall-clock shifts forward to 03:30")
                .isEqualTo(3);
    }

    @Test
    void rejectsNullComponents() {
        assertThatThrownBy(() -> new ZonedTimestamp(null, BERLIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ZonedTimestamp(Instant.EPOCH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void utcZoneIsPreservedDistinctFromOffset() {
        ZonedTimestamp timestamp = new ZonedTimestamp(Instant.EPOCH, ZoneOffset.UTC);

        assertThat(timestamp.zone())
                .isEqualTo(ZoneOffset.UTC);
    }
}
