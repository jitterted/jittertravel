package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConferenceProjectorTest {

    // ALL ignores now; any instant works for those cases.
    private static final Instant NOW = Instant.parse("2020-01-01T00:00:00Z");
    // The test JVM is pinned to UTC (pom.xml), so fixtures name a venue zone explicitly —
    // otherwise "is it over?" would accidentally agree with the server and prove nothing.
    private static final ZoneId VENUE_ZONE = ZoneId.of("America/Los_Angeles");

    @Test
    void projectorCreatesViewFromEvents() {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        Address address = new Address("123 Venue Street", "Venue City", "Venue State", "Venue Postal Code", "Venue Country", null);
        ConferencePlanned event = new ConferencePlanned(
                conferenceId,
                "Conference Name",
                zt(LocalDateTime.of(2026, 6, 1, 9, 0)),
                zt(LocalDateTime.of(2026, 6, 3, 17, 0)),
                "Venue",
                address
        );
        StoredEvent storedEvent = new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());

        projector.handle(Stream.of(storedEvent));

        assertThat(projector.views(TimeView.ALL, NOW))
                .hasSize(1);
        assertThat(projector.views(TimeView.ALL, NOW).getFirst().conferenceId())
                .isEqualTo(conferenceId);
        assertThat(projector.views(TimeView.ALL, NOW).getFirst().name())
                .isEqualTo("Conference Name");
        assertThat(projector.views(TimeView.ALL, NOW).getFirst().city())
                .isEqualTo("Venue City");
    }

    @Test
    void projectedViewsAreSortedAscendingByStartDate() {
        ConferenceProjector projector = new ConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal Code", "Country", null);

        ConferencePlanned laterEvent = new ConferencePlanned(
                ConferenceId.random(),
                "Later Conference",
                zt(LocalDateTime.of(2026, 7, 1, 9, 0)),
                zt(LocalDateTime.of(2026, 7, 3, 17, 0)),
                "Later Venue",
                address
        );
        ConferencePlanned earlierEvent = new ConferencePlanned(
                ConferenceId.random(),
                "Earlier Conference",
                zt(LocalDateTime.of(2026, 6, 28, 9, 0)),
                zt(LocalDateTime.of(2026, 6, 30, 17, 0)),
                "Earlier Venue",
                address
        );

        projector.handle(Stream.of(
                new StoredEvent(1, laterEvent.getClass(), UUID.randomUUID(), Instant.now(), laterEvent, UUID.randomUUID()),
                new StoredEvent(2, earlierEvent.getClass(), UUID.randomUUID(), Instant.now(), earlierEvent, UUID.randomUUID())
        ));

        assertThat(projector.views(TimeView.ALL, NOW))
                .hasSize(2)
                .extracting(ConferenceView::name)
                .containsExactly("Earlier Conference", "Later Conference");
    }

    @Test
    void migratableViewsExcludesMultiDayConferences() {
        ConferenceProjector projector = new ConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal", "Country", null);

        handle(projector, 1, "Single-day", LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 1, 17, 0), address);
        handle(projector, 2, "Multi-day",  LocalDateTime.of(2026, 6, 2, 9, 0), LocalDateTime.of(2026, 6, 4, 17, 0), address);

        assertThat(projector.migratableViews())
                .hasSize(1)
                .extracting(ConferenceView::name)
                .containsExactly("Single-day");
    }

    @Test
    void migratableViewsIncludesSingleDayConferences() {
        ConferenceProjector projector = new ConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal", "Country", null);

        handle(projector, 1, "All Day", LocalDateTime.of(2026, 6, 1, 0, 0), LocalDateTime.of(2026, 6, 1, 23, 59), address);

        assertThat(projector.migratableViews())
                .hasSize(1)
                .extracting(ConferenceView::name)
                .containsExactly("All Day");
    }

    @Test
    void migratableViewsAreSortedAscendingByStartDate() {
        ConferenceProjector projector = new ConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal", "Country", null);

        handle(projector, 1, "Later Single-day",   LocalDateTime.of(2026, 7, 5, 9, 0), LocalDateTime.of(2026, 7, 5, 17, 0), address);
        handle(projector, 2, "Multi-day (skipped)", LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 3, 17, 0), address);
        handle(projector, 3, "Earlier Single-day",  LocalDateTime.of(2026, 6, 10, 9, 0), LocalDateTime.of(2026, 6, 10, 17, 0), address);

        assertThat(projector.migratableViews())
                .extracting(ConferenceView::name)
                .containsExactly("Earlier Single-day", "Later Single-day");
    }

    @Test
    void futureFilterKeepsInProgressConferenceButDropsFinishedOne() {
        ConferenceProjector projector = new ConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal", "Country", null);
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 12, 0);
        // started yesterday, ends tomorrow -> still "upcoming" by endDate
        handle(projector, 1, "In Progress",
                now.minusDays(1), now.plusDays(1), address);
        // ended last week -> past
        handle(projector, 2, "Finished",
                now.minusDays(10), now.minusDays(8), address);

        // "Now" is a moment, read against the venue's own zone — not the server's.
        Instant nowInstant = now.atZone(VENUE_ZONE).toInstant();
        assertThat(projector.views(TimeView.FUTURE, nowInstant))
                .extracting(ConferenceView::name)
                .containsExactly("In Progress");
        assertThat(projector.views(TimeView.ALL, nowInstant))
                .extracting(ConferenceView::name)
                .containsExactlyInAnyOrder("In Progress", "Finished");
    }

    @Test
    void decliningAttendanceRemovesTheConferenceFromTheList() {
        ConferenceProjector projector = new ConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal", "Country", null);
        ConferenceId conferenceId = ConferenceId.random();
        ConferencePlanned planned = new ConferencePlanned(
                conferenceId, "Devoxx Morocco",
                zt(LocalDateTime.of(2026, 10, 7, 9, 0)), zt(LocalDateTime.of(2026, 10, 9, 17, 0)),
                "Venue", address);
        projector.handle(Stream.of(new StoredEvent(
                1, planned.getClass(), UUID.randomUUID(), Instant.now(), planned, UUID.randomUUID())));
        assertThat(projector.views(TimeView.ALL, NOW))
                .hasSize(1);

        ConferenceAttendanceDeclined declined = new ConferenceAttendanceDeclined(
                conferenceId, "Schedule clash", Instant.parse("2026-08-16T18:30:00Z"));
        projector.handle(Stream.of(new StoredEvent(
                2, declined.getClass(), UUID.randomUUID(), Instant.now(), declined, UUID.randomUUID())));

        assertThat(projector.views(TimeView.ALL, NOW))
                .as("a declined conference leaves the conferences list, like a cancelled one")
                .isEmpty();
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, VENUE_ZONE);
    }

    private static void handle(ConferenceProjector projector, long seq, String name,
                               LocalDateTime start, LocalDateTime end, Address address) {
        ConferencePlanned event = new ConferencePlanned(
                ConferenceId.random(), name, zt(start), zt(end), "Venue", address);
        projector.handle(Stream.of(
                new StoredEvent(seq, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID())));
    }
}
