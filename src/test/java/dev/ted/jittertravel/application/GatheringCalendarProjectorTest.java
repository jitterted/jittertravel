package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GatheringCalendarProjectorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 10);
    private static final LocalTime START = LocalTime.of(18, 0);
    private static final LocalTime END = LocalTime.of(21, 0);

    @Test
    void buildsCalendarEntryFromGatheringPlanned() {
        GatheringCalendarProjector projector = new GatheringCalendarProjector();
        GatheringPlanned event = new GatheringPlanned(
                GatheringId.random(),
                "London Java Community",
                "Skills Matter",
                new Address("1 Example St", "London", "", "EC1A 1BB", "GB", null),
                DATE, START, END,
                true,
                "https://meetup.com/ljc/events/123"
        );

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries()).hasSize(1);
        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.kind()).isEqualTo(EntryKind.GATHERING);
        assertThat(entry.mainTitle()).isEqualTo("London Java Community");
        assertThat(entry.subTitle()).isEqualTo(List.of("Skills Matter", "London, GB"));
        assertThat(entry.start()).isEqualTo(LocalDateTime.of(2026, 7, 10, 18, 0));
        assertThat(entry.end()).isEqualTo(LocalDateTime.of(2026, 7, 10, 21, 0));
        assertThat(entry.continuationTitle()).isNull();
        assertThat(entry.continuationSubTitle()).isNull();
    }

    @Test
    void gatheringWithInfoUrlSetsItAsMapsUrl() {
        GatheringCalendarProjector projector = new GatheringCalendarProjector();

        projector.handle(Stream.of(stored(gathering(GatheringId.random(), "Meetup",
                DATE, "https://meetup.com/events/123"))));

        assertThat(projector.entries().getFirst().mapsUrl())
                .isEqualTo("https://meetup.com/events/123");
    }

    @Test
    void gatheringWithBlankInfoUrlHasNullMapsUrl() {
        GatheringCalendarProjector projector = new GatheringCalendarProjector();

        projector.handle(Stream.of(stored(gathering(GatheringId.random(), "Meetup", DATE, ""))));

        assertThat(projector.entries().getFirst().mapsUrl()).isNull();
    }

    @Test
    void gatheringWithBlankVenueNameShowsOnlyCityAndCountry() {
        GatheringCalendarProjector projector = new GatheringCalendarProjector();
        GatheringPlanned event = new GatheringPlanned(
                GatheringId.random(), "Meetup", "",
                new Address("", "London", "", "", "GB", null),
                DATE, START, END, false, ""
        );

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries().getFirst().subTitle()).isEqualTo(List.of("London, GB"));
    }

    @Test
    void replayingTheSameEventIsIdempotent() {
        GatheringCalendarProjector projector = new GatheringCalendarProjector();
        GatheringPlanned event = gathering(GatheringId.random(), "Meetup", DATE, "");

        projector.handle(Stream.of(stored(event)));
        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries()).hasSize(1);
    }

    @Test
    void entriesAreSortedByStart() {
        GatheringCalendarProjector projector = new GatheringCalendarProjector();
        GatheringPlanned later = gathering(GatheringId.random(), "Later", DATE.plusWeeks(1), "");
        GatheringPlanned earlier = gathering(GatheringId.random(), "Earlier", DATE, "");

        projector.handle(Stream.of(stored(later), stored(earlier)));

        assertThat(projector.entries())
                .extracting(CalendarEntry::mainTitle)
                .containsExactly("Earlier", "Later");
    }

    private static GatheringPlanned gathering(GatheringId id, String title, LocalDate date, String infoUrl) {
        return new GatheringPlanned(
                id, title, "Some Venue",
                new Address("1 Street", "London", "", "EC1A 1BB", "GB", null),
                date, START, END, false, infoUrl
        );
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
