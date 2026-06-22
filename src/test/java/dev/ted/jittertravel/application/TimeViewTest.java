package dev.ted.jittertravel.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TimeViewTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    private static TemporalView relevantUntil(Instant instant) {
        return () -> instant;
    }

    @Test
    void allIncludesItemsThatHaveAlreadyEnded() {
        TemporalView ended = relevantUntil(NOW.minus(Duration.ofDays(1)));

        assertThat(TimeView.ALL.includes(ended, NOW))
                .as("ALL includes a past item")
                .isTrue();
    }

    @Test
    void futureExcludesItemsThatHaveAlreadyEnded() {
        TemporalView ended = relevantUntil(NOW.minusSeconds(1));

        assertThat(TimeView.FUTURE.includes(ended, NOW))
                .as("FUTURE excludes an item whose relevantUntil is before now")
                .isFalse();
    }

    @Test
    void futureIncludesItemEndingExactlyNow() {
        TemporalView endingNow = relevantUntil(NOW);

        assertThat(TimeView.FUTURE.includes(endingNow, NOW))
                .as("FUTURE is inclusive of the boundary instant")
                .isTrue();
    }

    @Test
    void futureIncludesItemStillInTheFuture() {
        TemporalView upcoming = relevantUntil(NOW.plus(Duration.ofDays(1)));

        assertThat(TimeView.FUTURE.includes(upcoming, NOW))
                .as("FUTURE includes an item ending after now")
                .isTrue();
    }

    @Test
    void fromParamDefaultsToFutureWhenAbsentOrUnrecognized() {
        assertThat(TimeView.fromParam(null)).isEqualTo(TimeView.FUTURE);
        assertThat(TimeView.fromParam("nonsense")).isEqualTo(TimeView.FUTURE);
    }

    @Test
    void fromParamResolvesKnownValuesCaseInsensitively() {
        assertThat(TimeView.fromParam("all")).isEqualTo(TimeView.ALL);
        assertThat(TimeView.fromParam("FUTURE")).isEqualTo(TimeView.FUTURE);
    }
}
