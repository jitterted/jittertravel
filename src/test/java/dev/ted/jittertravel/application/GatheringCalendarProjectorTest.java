package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GatheringCalendarProjectorTest {

    private static final ZoneId UK = ZoneId.of("Europe/London");
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
                ukTime(DATE, START), ukTime(DATE, END),
                true,
                "https://meetup.com/ljc/events/123"
        );

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries()).hasSize(1);
        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.kind()).isEqualTo(EntryKind.GATHERING);
        assertThat(entry.mainTitle()).isEqualTo("London Java Community");
        assertThat(entry.subTitle()).isEqualTo(List.of(
                new SubtitleLine.Text("Skills Matter"),
                new SubtitleLine.Text("London, GB"),
                new SubtitleLine.Range(ukTime(DATE, START), ukTime(DATE, END))));
        assertThat(entry.start()).isEqualTo(LocalDateTime.of(2026, 7, 10, 18, 0));
        assertThat(entry.end()).isEqualTo(LocalDateTime.of(2026, 7, 10, 21, 0));
        assertThat(entry.continuationTitle()).isNull();
        assertThat(entry.continuationSubTitle()).isNull();
        assertThat(entry.speaking())
                .as("speaking gathering keeps its speaking flag on the calendar entry")
                .isTrue();
    }

    @Test
    void nonSpeakingGatheringHasSpeakingFalse() {
        GatheringCalendarProjector projector = new GatheringCalendarProjector();
        GatheringPlanned event = new GatheringPlanned(
                GatheringId.random(),
                "London Java Community",
                "Skills Matter",
                new Address("1 Example St", "London", "", "EC1A 1BB", "GB", null),
                ukTime(DATE, START), ukTime(DATE, END),
                false,
                "https://meetup.com/ljc/events/123"
        );

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries().getFirst().speaking())
                .as("a gathering Ted only attends is not marked speaking")
                .isFalse();
    }

    @Test
    void gatheringChangedUpdatesTheSpeakingFlag() {
        GatheringCalendarProjector projector = new GatheringCalendarProjector();
        GatheringId gatheringId = GatheringId.random();
        // Planned as speaking, then edited down to merely attending.
        GatheringPlanned planned = new GatheringPlanned(
                gatheringId, "LJC", "Skills Matter",
                new Address("1 Example St", "London", "", "EC1A 1BB", "GB", null),
                ukTime(DATE, START), ukTime(DATE, END), true, "");
        GatheringChanged changed = new GatheringChanged(
                gatheringId, "LJC", "Skills Matter",
                new Address("1 Example St", "London", "", "EC1A 1BB", "GB", null),
                ukTime(DATE, START), ukTime(DATE, END), false, "");

        projector.handle(Stream.of(stored(planned), stored(changed)));

        assertThat(projector.entries().getFirst().speaking())
                .as("editing a gathering overwrites its speaking flag")
                .isFalse();
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
                ukTime(DATE, START), ukTime(DATE, END), false, ""
        );

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries().getFirst().subTitle())
                .isEqualTo(List.of(
                        new SubtitleLine.Text("London, GB"),
                        new SubtitleLine.Range(ukTime(DATE, START), ukTime(DATE, END))));
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

    @Test
    void gatheringChangedOverwritesThePlannedEntry() {
        GatheringCalendarProjector projector = new GatheringCalendarProjector();
        GatheringId gatheringId = GatheringId.random();
        GatheringPlanned planned = gathering(gatheringId, "Old Title", DATE, "https://old.example.com");
        GatheringChanged changed = new GatheringChanged(
                gatheringId, "New Title", "Federation House",
                new Address("2 New St", "Manchester", "", "M1 1AA", "GB", null),
                ukTime(DATE.plusWeeks(1), LocalTime.of(17, 30)), ukTime(DATE.plusWeeks(1), LocalTime.of(20, 0)),
                false, "https://new.example.com");

        projector.handle(Stream.of(stored(planned), stored(changed)));

        assertThat(projector.entries()).hasSize(1);
        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("New Title");
        assertThat(entry.subTitle()).isEqualTo(List.of(
                new SubtitleLine.Text("Federation House"),
                new SubtitleLine.Text("Manchester, GB"),
                new SubtitleLine.Range(ukTime(DATE.plusWeeks(1), LocalTime.of(17, 30)),
                                       ukTime(DATE.plusWeeks(1), LocalTime.of(20, 0)))));
        assertThat(entry.start()).isEqualTo(LocalDateTime.of(2026, 7, 17, 17, 30));
        assertThat(entry.end()).isEqualTo(LocalDateTime.of(2026, 7, 17, 20, 0));
        assertThat(entry.mapsUrl()).isEqualTo("https://new.example.com");
    }

    @Test
    void gatheringChangedToBlankInfoUrlClearsTheMapsUrl() {
        GatheringCalendarProjector projector = new GatheringCalendarProjector();
        GatheringId gatheringId = GatheringId.random();

        projector.handle(Stream.of(
                stored(gathering(gatheringId, "Meetup", DATE, "https://old.example.com")),
                stored(new GatheringChanged(gatheringId, "Meetup", "Some Venue",
                        new Address("1 Street", "London", "", "EC1A 1BB", "GB", null),
                        ukTime(DATE, START), ukTime(DATE, END), false, ""))));

        assertThat(projector.entries().getFirst().mapsUrl()).isNull();
    }

    @Test
    void calendarEntryCarriesOwnerEditPath() {
        GatheringCalendarProjector projector = new GatheringCalendarProjector();
        GatheringId id = GatheringId.random();

        projector.handle(Stream.of(stored(gathering(id, "Meetup", DATE, ""))));

        assertThat(projector.entries().getFirst().editPath())
                .isEqualTo("/planned-gatherings/" + id.id());
    }

    private static GatheringPlanned gathering(GatheringId id, String title, LocalDate date, String infoUrl) {
        return new GatheringPlanned(
                id, title, "Some Venue",
                new Address("1 Street", "London", "", "EC1A 1BB", "GB", null),
                ukTime(date, START), ukTime(date, END), false, infoUrl
        );
    }

    private static ZonedTimestamp ukTime(LocalDate date, LocalTime time) {
        return ZonedTimestamp.fromLocal(date.atTime(time), UK);
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
