package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TransferEndpointWindowTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");

    @Test
    void theGapIsTimeBetweenTheEarlierEndAndTheLaterStart() {
        var earlier = window("2026-09-13 15:00", "2026-09-14 11:00");
        var later = window("2026-09-15 11:00", "2026-09-15 13:00");

        assertThat(earlier.gapTo(later))
                .as("origin first")
                .isEqualTo(Duration.ofHours(24));
        assertThat(later.gapTo(earlier))
                .as("the gap is the same whichever window is asked")
                .isEqualTo(Duration.ofHours(24));
    }

    @Test
    void overlappingOrTouchingWindowsAreZeroApart() {
        var stay = window("2026-09-13 15:00", "2026-09-18 11:00");

        assertThat(stay.gapTo(window("2026-09-15 19:00", "2026-09-15 22:00")))
                .as("one inside the other")
                .isEqualTo(Duration.ZERO);
        assertThat(window("2026-09-15 19:00", "2026-09-15 22:00").gapTo(stay))
                .as("and the other way round")
                .isEqualTo(Duration.ZERO);
        assertThat(stay.gapTo(window("2026-09-18 11:00", "2026-09-18 12:00")))
                .as("touching")
                .isEqualTo(Duration.ZERO);
    }

    @Test
    void aWindowCoversEachOfItsDaysAndNoOthers() {
        var stay = window("2026-09-13 15:00", "2026-09-18 11:00");

        assertThat(stay.coversDay(LocalDate.of(2026, 9, 12)))
                .as("the day before check-in")
                .isFalse();
        assertThat(stay.coversDay(LocalDate.of(2026, 9, 13)))
                .as("check-in day")
                .isTrue();
        assertThat(stay.coversDay(LocalDate.of(2026, 9, 16)))
                .as("a middle day")
                .isTrue();
        assertThat(stay.coversDay(LocalDate.of(2026, 9, 18)))
                .as("check-out day")
                .isTrue();
        assertThat(stay.coversDay(LocalDate.of(2026, 9, 19)))
                .as("the day after check-out")
                .isFalse();
    }

    private static TransferEndpointWindow window(String start, String end) {
        return new TransferEndpointWindow(at(start), at(end));
    }

    private static ZonedTimestamp at(String dayAndTime) {
        return ZonedTimestamp.fromLocal(
                LocalDateTime.parse(dayAndTime.replace(' ', 'T')), DENVER);
    }
}
