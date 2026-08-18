package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedHotelView;
import dev.ted.jittertravel.application.BookedHotelsProjector;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CalendarFeedAssemblerTest {

    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final UUID BOOKING_UUID = UUID.fromString("00000000-0000-0000-0000-0000000000ab");

    private final BookedHotelsProjector projector = mock(BookedHotelsProjector.class);
    private final CalendarFeedAssembler assembler = new CalendarFeedAssembler(projector);

    @Test
    void futureDeadlineBecomesOneVeventCarrying48h24hAnd4hAlarms() {
        Instant deadline = NOW.plus(Duration.ofDays(10));
        givenBookings(hotel("Grand Hotel", deadline, false));

        ICalEvent deadlineEvent = onlyDeadlineEvent();

        assertThat(deadlineEvent.uid()).isEqualTo(BOOKING_UUID + "-cancelby@jittertravel");
        assertThat(deadlineEvent.start()).isEqualTo(deadline);
        assertThat(deadlineEvent.summary()).isEqualTo("Free-cancel deadline: Grand Hotel");
        assertThat(deadlineEvent.alarmTriggers())
                .containsExactly("-PT48H", "-PT24H", "-PT4H");
    }

    @Test
    void bookingWithoutADeadlineProducesNoDeadlineEvent() {
        givenBookings(hotel("No Deadline Inn", null, false));

        assertThat(deadlineEvents()).isEmpty();
    }

    @Test
    void cancelledBookingIsExcludedEvenWithAFutureDeadline() {
        givenBookings(hotel("Cancelled Hotel", NOW.plus(Duration.ofDays(5)), true));

        assertThat(deadlineEvents()).isEmpty();
    }

    @Test
    void pastDeadlineIsExcluded() {
        givenBookings(hotel("Yesterday Hotel", NOW.minus(Duration.ofHours(1)), false));

        assertThat(deadlineEvents()).isEmpty();
    }

    @Test
    void feedAlwaysContainsTheLivenessHeartbeatEvenWithNoBookings() {
        givenBookings();

        List<ICalEvent> feed = assembler.feed(NOW);

        assertThat(feed).hasSize(1);
        ICalEvent heartbeat = feed.get(0);
        assertThat(heartbeat.uid()).startsWith("heartbeat-").endsWith("@jittertravel");
        assertThat(heartbeat.alarmTriggers()).containsExactly("-PT5M");
    }

    @Test
    void heartbeatIsStrictlyInTheFutureAndAlignedToTheWeeklyCadence() {
        ICalEvent heartbeat = assembler.heartbeatEvent(NOW);

        assertThat(heartbeat.start()).isAfter(NOW);
        // Anchored to a fixed Monday 17:00 UTC, so its offset from the anchor is a whole number of weeks.
        Instant anchor = Instant.parse("2024-01-01T17:00:00Z");
        long secondsFromAnchor = heartbeat.start().getEpochSecond() - anchor.getEpochSecond();
        assertThat(secondsFromAnchor % Duration.ofDays(7).getSeconds()).isZero();
    }

    @Test
    void probeEventIsTenMinutesOutWithAShortAlarmAndAStableUid() {
        ICalEvent probe = assembler.probeEvent(NOW);

        assertThat(probe.uid()).isEqualTo("probe@jittertravel");
        assertThat(probe.start()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(probe.alarmTriggers()).containsExactly("-PT5M");
    }

    private void givenBookings(BookedHotelView... views) {
        given(projector.views(TimeView.ALL, NOW)).willReturn(List.of(views));
    }

    private List<ICalEvent> deadlineEvents() {
        return assembler.feed(NOW).stream()
                .filter(event -> event.uid().endsWith("-cancelby@jittertravel"))
                .toList();
    }

    private ICalEvent onlyDeadlineEvent() {
        List<ICalEvent> events = deadlineEvents();
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    private static BookedHotelView hotel(String name, Instant cancelBy, boolean cancelled) {
        ZonedTimestamp cancelByTs = cancelBy == null ? null : new ZonedTimestamp(cancelBy, ZoneOffset.UTC);
        return new BookedHotelView(
                HotelBookingId.of(BOOKING_UUID),
                name,
                "Berlin",
                "Germany",
                new ZonedTimestamp(Instant.parse("2026-08-01T14:00:00Z"), ZoneOffset.UTC),
                new ZonedTimestamp(Instant.parse("2026-08-03T10:00:00Z"), ZoneOffset.UTC),
                BookingIntent.FINAL,
                "https://maps.example.com/grand",
                cancelByTs,
                false,
                cancelled,
                "");
    }
}
