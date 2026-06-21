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

class BookedHotelsProjectorTest {

    private static final LocalDateTime CHECK_IN = LocalDateTime.of(2026, 6, 14, 15, 0);
    private static final LocalDateTime CHECK_OUT = LocalDateTime.of(2026, 6, 15, 11, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2020, 1, 1, 0, 0);

    @Test
    void hotelBookedAddsEntryWithTentativeStatus() {
        BookedHotelsProjector projector = new BookedHotelsProjector();
        HotelBooked event = sampleHotelBooked(BookingIntent.TENTATIVE);

        projector.handle(Stream.of(stored(event)));

        List<BookedHotelView> views = projector.views(TimeView.ALL, NOW);
        assertThat(views).hasSize(1);
        BookedHotelView view = views.getFirst();
        assertThat(view.hotelBookingId()).isEqualTo(event.hotelBookingId());
        assertThat(view.hotelName()).isEqualTo("Grand Hotel");
        assertThat(view.city()).isEqualTo("Springfield");
        assertThat(view.country()).isEqualTo("US");
        assertThat(view.checkIn()).isEqualTo(CHECK_IN);
        assertThat(view.checkOut()).isEqualTo(CHECK_OUT);
        assertThat(view.status()).isEqualTo(BookingIntent.TENTATIVE);
    }

    @Test
    void futureFilterKeepsInProgressStayButDropsCheckedOutStay() {
        BookedHotelsProjector projector = new BookedHotelsProjector();
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 12, 0);
        // checked in yesterday, checks out tomorrow -> still "upcoming" by checkOut
        HotelBooked inProgress = hotelBooked("Currently Here",
                now.minusDays(1), now.plusDays(1));
        // already checked out -> past
        HotelBooked checkedOut = hotelBooked("Already Left",
                now.minusDays(5), now.minusDays(3));

        projector.handle(Stream.of(stored(inProgress), stored(checkedOut)));

        assertThat(projector.views(TimeView.FUTURE, now))
                .extracting(BookedHotelView::hotelName)
                .containsExactly("Currently Here");
        assertThat(projector.views(TimeView.ALL, now))
                .extracting(BookedHotelView::hotelName)
                .containsExactlyInAnyOrder("Currently Here", "Already Left");
    }

    @Test
    void hotelChangedOverwritesBookingUnderSameId() {
        BookedHotelsProjector projector = new BookedHotelsProjector();
        HotelBookingId id = HotelBookingId.random();
        HotelBooked booked = new HotelBooked(id, "Grand Hotel",
                new Address("123 Main St", "Springfield", "IL", "62701", "US", null),
                CHECK_IN, CHECK_OUT, BookingIntent.TENTATIVE, null);
        HotelChanged changed = new HotelChanged(id, "Seaside Resort",
                new Address("1 Ocean Dr", "Miami", "FL", "33139", "US", null),
                CHECK_IN.plusDays(10), CHECK_OUT.plusDays(11), BookingIntent.FINAL, null);

        projector.handle(Stream.of(stored(booked), stored(changed)));

        List<BookedHotelView> views = projector.views(TimeView.ALL, NOW);
        assertThat(views)
                .hasSize(1);
        BookedHotelView view = views.getFirst();
        assertThat(view.hotelBookingId())
                .isEqualTo(id);
        assertThat(view.hotelName())
                .isEqualTo("Seaside Resort");
        assertThat(view.city())
                .isEqualTo("Miami");
        assertThat(view.checkIn())
                .isEqualTo(CHECK_IN.plusDays(10));
        assertThat(view.checkOut())
                .isEqualTo(CHECK_OUT.plusDays(11));
    }

    private static HotelBooked hotelBooked(String name, LocalDateTime checkIn, LocalDateTime checkOut) {
        return new HotelBooked(
                HotelBookingId.random(),
                name,
                new Address("123 Main St", "Springfield", "IL", "62701", "US", null),
                checkIn,
                checkOut,
                BookingIntent.TENTATIVE,
                null
        );
    }

    private static HotelBooked sampleHotelBooked(BookingIntent intent) {
        return new HotelBooked(
                HotelBookingId.random(),
                "Grand Hotel",
                new Address("123 Main St", "Springfield", "IL", "62701", "US", null),
                CHECK_IN,
                CHECK_OUT,
                intent,
                null
        );
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
