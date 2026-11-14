package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancellationCause;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.FlightItineraryRebooked;
import dev.ted.jittertravel.domain.ItineraryChangeKind;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryChangeOutcomesProjectorTest {

    private static final Instant NOW = Instant.parse("2026-11-14T15:00:00Z");
    private static final FlightItineraryId HE80PP = FlightItineraryId.of(UUID.randomUUID());

    private final ItineraryChangeOutcomesProjector projector = new ItineraryChangeOutcomesProjector();

    @Test
    void aRebookingCountsTheLegsItsOwnAppendAddedMovedAndDropped() {
        UUID rebooking = UUID.randomUUID();
        FlightId added = FlightId.random();
        FlightId moved = FlightId.random();
        FlightId droppedOne = FlightId.random();
        FlightId droppedTwo = FlightId.random();

        projector.handle(Stream.of(
                stored(booked(added), rebooking),
                stored(changed(moved), rebooking),
                stored(new FlightCancelled(droppedOne, "Rebooked", NOW, FlightCancellationCause.REBOOKED), rebooking),
                stored(new FlightCancelled(droppedTwo, "Rebooked", NOW, FlightCancellationCause.REBOOKED), rebooking),
                stored(new FlightItineraryRebooked(HE80PP, List.of(added, moved, droppedOne, droppedTwo),
                        "Rebooked", NOW), rebooking)));

        assertThat(projector.lastChange(HE80PP))
                .as("HE80PP's last change")
                .hasValue(new ItineraryChangeOutcomesProjector.Outcome(ItineraryChangeKind.REBOOKING, 1, 1, 2));
    }

    @Test
    void anotherCommandsLegsAndAHandCancellationAreNotCounted() {
        UUID change = UUID.randomUUID();
        FlightId elsewhere = FlightId.random();

        projector.handle(Stream.of(
                stored(booked(elsewhere), UUID.randomUUID()),
                stored(new FlightCancelled(FlightId.random(), "", NOW), change),
                stored(changed(FlightId.random()), change),
                stored(new FlightItineraryChanged(HE80PP, List.of(), "Airline schedule change", NOW), change)));

        assertThat(projector.lastChange(HE80PP))
                .as("a hand cancellation is not a drop; another command's booking is not this change's")
                .hasValue(new ItineraryChangeOutcomesProjector.Outcome(ItineraryChangeKind.SCHEDULE_CHANGE, 0, 1, 0));
    }

    @Test
    void theLatestChangeReplacesTheOneBefore() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        projector.handle(Stream.of(
                stored(changed(FlightId.random()), first),
                stored(new FlightItineraryChanged(HE80PP, List.of(), "Airline schedule change", NOW), first),
                stored(new FlightCancelled(FlightId.random(), "Rebooked", NOW, FlightCancellationCause.REBOOKED), second),
                stored(new FlightItineraryRebooked(HE80PP, List.of(), "Rebooked", NOW), second)));

        assertThat(projector.lastChange(HE80PP))
                .hasValue(new ItineraryChangeOutcomesProjector.Outcome(ItineraryChangeKind.REBOOKING, 0, 0, 1));
    }

    @Test
    void anItineraryNeverChangedHasNoOutcome() {
        assertThat(projector.lastChange(HE80PP))
                .as("Itinerary never changed")
                .isEmpty();
    }

    private static FlightBooked booked(FlightId id) {
        return new FlightBooked(id, "United Airlines", "UA59", AirportCode.of("FRA"), at(13),
                AirportCode.of("SFO"), at(22));
    }

    private static FlightChanged changed(FlightId id) {
        return new FlightChanged(id, "United Airlines", "UA59", AirportCode.of("FRA"), at(14),
                AirportCode.of("SFO"), at(23), "Rebooked");
    }

    private static ZonedTimestamp at(int hour) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 26, hour, 0), ZoneId.of("Europe/Berlin"));
    }

    private static StoredEvent stored(Event event, UUID commandId) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), NOW, event, commandId);
    }
}
