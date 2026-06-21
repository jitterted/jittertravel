package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
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
                BookingIntent.TENTATIVE,
                null
        );

        projector.handle(Stream.of(stored(event)));

        List<CalendarEntry> entries = projector.entries();
        assertThat(entries).hasSize(1);
        CalendarEntry entry = entries.getFirst();
        assertThat(entry.kind()).isEqualTo(EntryKind.LODGING);
        assertThat(entry.start()).isEqualTo(CHECK_IN);
        assertThat(entry.end()).isEqualTo(CHECK_OUT);
        assertThat(entry.mainTitle()).isEqualTo("Grand Hotel");
        assertThat(entry.subTitle()).isEqualTo(List.of("Springfield, US"));
    }

    @Test
    void hotelChangedReplacesCalendarEntryUnderSameId() {
        HotelCalendarProjector projector = new HotelCalendarProjector();
        HotelBookingId id = HotelBookingId.random();
        HotelBooked booked = new HotelBooked(id, "Grand Hotel",
                new Address("123 Main St", "Springfield", "IL", "62701", "US", null),
                CHECK_IN, CHECK_OUT, BookingIntent.TENTATIVE, null);
        HotelChanged changed = new HotelChanged(id, "Seaside Resort",
                new Address("1 Ocean Dr", "Miami", "FL", "33139", "US", null),
                CHECK_IN.plusDays(10), CHECK_OUT.plusDays(11), BookingIntent.FINAL, null);

        projector.handle(Stream.of(stored(booked), stored(changed)));

        List<CalendarEntry> entries = projector.entries();
        assertThat(entries)
                .hasSize(1);
        CalendarEntry entry = entries.getFirst();
        assertThat(entry.start())
                .isEqualTo(CHECK_IN.plusDays(10));
        assertThat(entry.end())
                .isEqualTo(CHECK_OUT.plusDays(11));
        assertThat(entry.mainTitle())
                .isEqualTo("Seaside Resort");
        assertThat(entry.subTitle())
                .isEqualTo(List.of("Miami, US"));
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
