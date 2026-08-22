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

/**
 * These cases were {@code CalendarFeedAssemblerTest}'s until the hotel deadlines moved into their
 * own {@link ICalEventSource}; the behaviour is unchanged, and they now sit against the class that
 * owns it rather than reaching through the assembler to get at it.
 */
class HotelCancelDeadlineSourceTest {

    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final UUID BOOKING_UUID = UUID.fromString("00000000-0000-0000-0000-0000000000ab");

    private final BookedHotelsProjector projector = mock(BookedHotelsProjector.class);
    private final HotelCancelDeadlineSource source = new HotelCancelDeadlineSource(projector);

    @Test
    void futureDeadlineBecomesOneVeventCarrying48h24hAnd4hAlarms() {
        Instant deadline = NOW.plus(Duration.ofDays(10));
        givenBookings(hotel("Grand Hotel", deadline, false));

        ICalEvent deadlineEvent = onlyEvent();

        assertThat(deadlineEvent.uid()).isEqualTo(BOOKING_UUID + "-cancelby@jittertravel");
        assertThat(deadlineEvent.start()).isEqualTo(deadline);
        assertThat(deadlineEvent.summary()).isEqualTo("Free-cancel deadline: Grand Hotel");
        assertThat(deadlineEvent.alarmTriggers())
                .containsExactly("-PT48H", "-PT24H", "-PT4H");
    }

    @Test
    void bookingWithoutADeadlineProducesNoDeadlineEvent() {
        givenBookings(hotel("No Deadline Inn", null, false));

        assertThat(source.events(NOW)).isEmpty();
    }

    @Test
    void cancelledBookingIsExcludedEvenWithAFutureDeadline() {
        givenBookings(hotel("Cancelled Hotel", NOW.plus(Duration.ofDays(5)), true));

        assertThat(source.events(NOW)).isEmpty();
    }

    @Test
    void pastDeadlineIsExcluded() {
        givenBookings(hotel("Yesterday Hotel", NOW.minus(Duration.ofHours(1)), false));

        assertThat(source.events(NOW)).isEmpty();
    }

    private void givenBookings(BookedHotelView... views) {
        given(projector.views(TimeView.ALL, NOW)).willReturn(List.of(views));
    }

    private ICalEvent onlyEvent() {
        List<ICalEvent> events = source.events(NOW);
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private static BookedHotelView hotel(String name, Instant cancelBy, boolean cancelled) {
        ZonedTimestamp cancelByTs = cancelBy == null ? null : new ZonedTimestamp(cancelBy, ZoneOffset.UTC);
        return new BookedHotelView(
                HotelBookingId.of(BOOKING_UUID),
                name,
                "Berlin",
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
