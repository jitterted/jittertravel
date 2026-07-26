package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GatheringDetailsViewProjectorTest {

    private static final LocalDate JUN_20 = LocalDate.of(2026, 6, 20);
    private static final LocalDate JUL_04 = LocalDate.of(2026, 7, 4);
    private static final LocalTime START = LocalTime.of(18, 0);
    private static final LocalTime END = LocalTime.of(21, 0);
    private static final Address LONDON = new Address("1 Example St", "London", "", "EC1A 1BB", "GB", null);
    private static final Address MANCHESTER = new Address("2 New St", "Manchester", "", "M1 1AA", "GB", null);

    @Test
    void unknownGatheringIdIsEmpty() {
        GatheringDetailsViewProjector projector = new GatheringDetailsViewProjector();

        projector.handle(Stream.empty());

        assertThat(projector.findById(GatheringId.random()))
                .isEmpty();
    }

    @Test
    void gatheringPlannedEventCreatesDetailsView() {
        GatheringDetailsViewProjector projector = new GatheringDetailsViewProjector();
        GatheringId gatheringId = GatheringId.random();

        projector.handle(Stream.of(stored(new GatheringPlanned(
                gatheringId, "London Java Community", "Skills Matter", LONDON,
                JUN_20, START, END, true, "https://meetup.com/ljc/events/123"))));

        Optional<GatheringDetailsView> found = projector.findById(gatheringId);
        assertThat(found)
                .isPresent();
        GatheringDetailsView view = found.get();
        assertThat(view.title())
                .isEqualTo("London Java Community");
        assertThat(view.venueName())
                .isEqualTo("Skills Matter");
        assertThat(view.location())
                .isEqualTo(LONDON);
        assertThat(view.date())
                .isEqualTo(JUN_20);
        assertThat(view.startTime())
                .isEqualTo(START);
        assertThat(view.endTime())
                .isEqualTo(END);
        assertThat(view.speaking())
                .as("speaking flag from the planned event")
                .isTrue();
        assertThat(view.infoUrl())
                .isEqualTo("https://meetup.com/ljc/events/123");
    }

    @Test
    void gatheringChangedOverwritesEveryFieldOfThePlannedView() {
        GatheringDetailsViewProjector projector = new GatheringDetailsViewProjector();
        GatheringId gatheringId = GatheringId.random();

        projector.handle(Stream.of(
                stored(new GatheringPlanned(gatheringId, "Old Title", "Skills Matter", LONDON,
                        JUN_20, START, END, true, "https://old.example.com")),
                stored(new GatheringChanged(gatheringId, "New Title", "Federation House", MANCHESTER,
                        JUL_04, LocalTime.of(17, 30), LocalTime.of(20, 0), false, "https://new.example.com"))));

        GatheringDetailsView view = projector.findById(gatheringId).orElseThrow();
        assertThat(view.title())
                .isEqualTo("New Title");
        assertThat(view.venueName())
                .isEqualTo("Federation House");
        assertThat(view.location())
                .isEqualTo(MANCHESTER);
        assertThat(view.date())
                .isEqualTo(JUL_04);
        assertThat(view.startTime())
                .isEqualTo(LocalTime.of(17, 30));
        assertThat(view.endTime())
                .isEqualTo(LocalTime.of(20, 0));
        assertThat(view.speaking())
                .as("speaking flag should be overwritten by the change")
                .isFalse();
        assertThat(view.infoUrl())
                .isEqualTo("https://new.example.com");
    }

    @Test
    void changingOneGatheringLeavesOthersUntouched() {
        GatheringDetailsViewProjector projector = new GatheringDetailsViewProjector();
        GatheringId changed = GatheringId.random();
        GatheringId untouched = GatheringId.random();

        projector.handle(Stream.of(
                stored(new GatheringPlanned(changed, "Changed Meetup", "Skills Matter", LONDON,
                        JUN_20, START, END, false, "")),
                stored(new GatheringPlanned(untouched, "Other Meetup", "Skills Matter", LONDON,
                        JUN_20, START, END, false, "")),
                stored(new GatheringChanged(changed, "Changed Meetup v2", "Federation House", MANCHESTER,
                        JUL_04, START, END, false, ""))));

        assertThat(projector.findById(changed).orElseThrow().title())
                .isEqualTo("Changed Meetup v2");
        assertThat(projector.findById(untouched).orElseThrow().title())
                .isEqualTo("Other Meetup");
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
