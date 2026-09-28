package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one rule both conference pages ask — the dashboard and the detail page — so its boundaries
 * are pinned here rather than through either page.
 */
class CfpDeadlineViewTest {

    private static final Instant DEADLINE = Instant.parse("2026-09-12T21:59:00Z");
    private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

    private final CfpDeadlineView view = () -> new ZonedTimestamp(DEADLINE, ZONE);

    @Test
    void isOpenBeforeTheDeadline() {
        assertThat(view.cfpOpenAt(DEADLINE.minus(Duration.ofSeconds(1))))
                .as("one second before the deadline")
                .isTrue();
    }

    @Test
    void isClosedAtTheDeadlineItself() {
        assertThat(view.cfpOpenAt(DEADLINE))
                .as("at the deadline instant")
                .isFalse();
    }

    @Test
    void isClosedAfterTheDeadline() {
        assertThat(view.cfpOpenAt(DEADLINE.plus(Duration.ofDays(1))))
                .as("a day after the deadline")
                .isFalse();
    }

    @Test
    void anUnrecordedDeadlineIsNotOpen() {
        CfpDeadlineView unrecorded = () -> null;

        assertThat(unrecorded.cfpOpenAt(DEADLINE))
                .as("no deadline recorded")
                .isFalse();
    }
}
