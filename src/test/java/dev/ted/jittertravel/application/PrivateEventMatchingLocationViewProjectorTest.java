package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.PrivateEventCancelled;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventMatchingLocationChanged;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The read model behind the "match location" page — the second projector in the tree to apply
 * {@link PrivateEventMatchingLocationChanged}, the first being {@link ScheduleGapProjector}.
 */
class PrivateEventMatchingLocationViewProjectorTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private final AtomicLong sequence = new AtomicLong();

    @Test
    void plannedEventIsFoundByItsIdWithTheMatchingLocationItWasEnteredWith() {
        PrivateEventId privateEventId = PrivateEventId.random();
        PrivateEventMatchingLocationViewProjector projector =
                new PrivateEventMatchingLocationViewProjector();

        projector.handle(Stream.of(stored(planned(privateEventId))));

        assertThat(projector.findById(privateEventId))
                .as("Planned private event " + privateEventId)
                .contains(new PrivateEventMatchingLocationView(
                        privateEventId, "Dinner with the Smiths", "Chez Moi",
                        "Centennial", "US", "Centennial",
                        LocalDateTime.of(2026, 10, 1, 19, 0),
                        LocalDateTime.of(2026, 10, 1, 22, 0)));
    }

    @Test
    void anOverrideReplacesOnlyTheMatchingLocation() {
        PrivateEventId privateEventId = PrivateEventId.random();
        PrivateEventMatchingLocationViewProjector projector =
                new PrivateEventMatchingLocationViewProjector();

        projector.handle(Stream.of(
                stored(planned(privateEventId)),
                stored(new PrivateEventMatchingLocationChanged(privateEventId, "Lone Tree"))));

        assertThat(projector.findById(privateEventId))
                .as("Private event after re-matching")
                .contains(new PrivateEventMatchingLocationView(
                        privateEventId, "Dinner with the Smiths", "Chez Moi",
                        "Centennial", "US", "Lone Tree",
                        LocalDateTime.of(2026, 10, 1, 19, 0),
                        LocalDateTime.of(2026, 10, 1, 22, 0)));
    }

    @Test
    void theFormOffersTheValueAlreadyInForceRatherThanTheOneOriginallyTyped() {
        // The reason this projector applies the event at all: a form prefilled from
        // PrivateEventPlanned would offer "Centennial" again and quietly undo the correction on
        // the next submit.
        PrivateEventId privateEventId = PrivateEventId.random();
        PrivateEventMatchingLocationViewProjector projector =
                new PrivateEventMatchingLocationViewProjector();

        projector.handle(Stream.of(
                stored(planned(privateEventId)),
                stored(new PrivateEventMatchingLocationChanged(privateEventId, "Lone Tree"))));

        assertThat(projector.findById(privateEventId))
                .as("Private event " + privateEventId)
                .isPresent()
                .map(PrivateEventMatchingLocationView::locationForMatching)
                .as("Should offer the matching location already in force")
                .hasValue("Lone Tree");
    }

    @Test
    void theLastOverrideWins() {
        PrivateEventId privateEventId = PrivateEventId.random();
        PrivateEventMatchingLocationViewProjector projector =
                new PrivateEventMatchingLocationViewProjector();

        projector.handle(Stream.of(
                stored(planned(privateEventId)),
                stored(new PrivateEventMatchingLocationChanged(privateEventId, "Lone Tree")),
                stored(new PrivateEventMatchingLocationChanged(privateEventId, "Greenwood Village"))));

        assertThat(projector.findById(privateEventId))
                .as("Private event " + privateEventId)
                .isPresent()
                .map(PrivateEventMatchingLocationView::locationForMatching)
                .as("The most recent override is the one in force")
                .hasValue("Greenwood Village");
    }

    @Test
    void anOverrideForAnUnknownEventCreatesNothing() {
        // computeIfPresent, not put: one field is not enough to build a view out of, and a
        // cancelled evening must not come back because an override arrived late.
        PrivateEventMatchingLocationViewProjector projector =
                new PrivateEventMatchingLocationViewProjector();
        PrivateEventId neverPlanned = PrivateEventId.random();

        projector.handle(Stream.of(
                stored(new PrivateEventMatchingLocationChanged(neverPlanned, "Lone Tree"))));

        assertThat(projector.findById(neverPlanned))
                .as("An override alone is not a private event")
                .isEmpty();
    }

    @Test
    void anOverrideAfterCancellationDoesNotResurrectTheEvent() {
        PrivateEventId privateEventId = PrivateEventId.random();
        PrivateEventMatchingLocationViewProjector projector =
                new PrivateEventMatchingLocationViewProjector();

        projector.handle(Stream.of(
                stored(planned(privateEventId)),
                stored(new PrivateEventCancelled(privateEventId, "Cancelled")),
                stored(new PrivateEventMatchingLocationChanged(privateEventId, "Lone Tree"))));

        assertThat(projector.findById(privateEventId))
                .as("A cancelled evening stays gone")
                .isEmpty();
    }

    @Test
    void cancellationRemovesAnEventThatHadBeenRematched() {
        PrivateEventId privateEventId = PrivateEventId.random();
        PrivateEventMatchingLocationViewProjector projector =
                new PrivateEventMatchingLocationViewProjector();

        projector.handle(Stream.of(
                stored(planned(privateEventId)),
                stored(new PrivateEventMatchingLocationChanged(privateEventId, "Lone Tree")),
                stored(new PrivateEventCancelled(privateEventId, "Entered by mistake"))));

        assertThat(projector.findById(privateEventId))
                .isEmpty();
    }

    @Test
    void anOverrideForOneEveningLeavesAnotherAlone() {
        PrivateEventId rematched = PrivateEventId.random();
        PrivateEventId untouched = PrivateEventId.random();
        PrivateEventMatchingLocationViewProjector projector =
                new PrivateEventMatchingLocationViewProjector();

        projector.handle(Stream.of(
                stored(planned(rematched)),
                stored(planned(untouched)),
                stored(new PrivateEventMatchingLocationChanged(rematched, "Lone Tree"))));

        assertThat(projector.findById(untouched))
                .as("Private event " + untouched)
                .isPresent()
                .map(PrivateEventMatchingLocationView::locationForMatching)
                .as("The other evening keeps the location it was entered with")
                .hasValue("Centennial");
    }

    @Test
    void unknownIdIsEmptyRatherThanSomeOtherEvening() {
        PrivateEventMatchingLocationViewProjector projector =
                new PrivateEventMatchingLocationViewProjector();

        projector.handle(Stream.of(stored(planned(PrivateEventId.random()))));

        assertThat(projector.findById(PrivateEventId.random()))
                .isEmpty();
    }

    @Test
    void anEventMatchedInItsOwnCityIsNotOverridden() {
        PrivateEventId privateEventId = PrivateEventId.random();
        PrivateEventMatchingLocationViewProjector projector =
                new PrivateEventMatchingLocationViewProjector();

        projector.handle(Stream.of(stored(planned(privateEventId))));

        assertThat(projector.findById(privateEventId))
                .as("Private event " + privateEventId)
                .isPresent()
                .map(PrivateEventMatchingLocationView::isOverridden)
                .as("Centennial matched as Centennial is the ordinary case")
                .hasValue(false);
    }

    @Test
    void anEventMatchedElsewhereIsOverridden() {
        PrivateEventId privateEventId = PrivateEventId.random();
        PrivateEventMatchingLocationViewProjector projector =
                new PrivateEventMatchingLocationViewProjector();

        projector.handle(Stream.of(
                stored(planned(privateEventId)),
                stored(new PrivateEventMatchingLocationChanged(privateEventId, "Lone Tree"))));

        assertThat(projector.findById(privateEventId))
                .as("Private event " + privateEventId)
                .isPresent()
                .map(PrivateEventMatchingLocationView::isOverridden)
                .as("Centennial matched as Lone Tree is the whole point of the page")
                .hasValue(true);
    }

    @Test
    void aDifferentlyCasedSameCityIsNotAnOverride() {
        // Place.matches compares case-insensitively, so the page must not claim a difference the
        // schedule does not see.
        PrivateEventId privateEventId = PrivateEventId.random();
        PrivateEventMatchingLocationViewProjector projector =
                new PrivateEventMatchingLocationViewProjector();

        projector.handle(Stream.of(
                stored(planned(privateEventId)),
                stored(new PrivateEventMatchingLocationChanged(privateEventId, "centennial"))));

        assertThat(projector.findById(privateEventId))
                .as("Private event " + privateEventId)
                .isPresent()
                .map(PrivateEventMatchingLocationView::isOverridden)
                .as("Case alone is not a different place")
                .hasValue(false);
    }

    private static PrivateEventPlanned planned(PrivateEventId privateEventId) {
        Address venue = new Address("7 Dry Creek Rd", "Centennial", "CO", "80112", "US",
                                    "Centennial");
        return new PrivateEventPlanned(
                privateEventId, "Dinner with the Smiths", "Chez Moi", venue,
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 1, 19, 0), DENVER),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 1, 22, 0), DENVER));
    }

    private StoredEvent stored(Event event) {
        return new StoredEvent(sequence.incrementAndGet(), event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
