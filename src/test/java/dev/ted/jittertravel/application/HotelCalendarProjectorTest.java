package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class HotelCalendarProjectorTest {

    private static final LocalDateTime CHECK_IN = LocalDateTime.of(2026, 6, 14, 15, 0);
    private static final LocalDateTime CHECK_OUT = LocalDateTime.of(2026, 6, 15, 11, 0);

    @Test
    void hotelBookedAddsLodgingEntryToCalendarWithTentativeKind() {
        HotelCalendarProjector projector = new HotelCalendarProjector();
        HotelBooked event = new HotelBooked(
                HotelBookingId.random(),
                "Grand Hotel",
                new Address("123 Main St", "Springfield", "IL", "62701", "US", null),
                CHECK_IN,
                CHECK_OUT,
                BookingIntent.TENTATIVE
        );

        projector.handle(Stream.of(stored(event)));

        List<CalendarEntry> entries = projector.entries();
        assertThat(entries).hasSize(1);
        CalendarEntry entry = entries.getFirst();
        assertThat(entry.kind()).isEqualTo(EntryKind.LODGING);
        assertThat(entry.start()).isEqualTo(CHECK_IN);
        assertThat(entry.end()).isEqualTo(CHECK_OUT);
        assertThat(entry.mainTitle()).isEqualTo("Grand Hotel");
        assertThat(entry.subTitle()).isEqualTo("Springfield, US");
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
