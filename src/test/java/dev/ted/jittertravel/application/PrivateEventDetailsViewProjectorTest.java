package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.PrivateEventId;
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
 * The read model behind the cancel confirmation page. Removal on cancellation lives in
 * {@link PrivateEventCancellationPropagationTest} with the rest of the lifecycle.
 */
class PrivateEventDetailsViewProjectorTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private final AtomicLong sequence = new AtomicLong();

    @Test
    void plannedEventIsFoundByItsId() {
        PrivateEventId privateEventId = PrivateEventId.random();
        PrivateEventDetailsViewProjector projector = new PrivateEventDetailsViewProjector();

        projector.handle(Stream.of(stored(planned(privateEventId))));

        assertThat(projector.findById(privateEventId))
                .contains(new PrivateEventDetailsView(
                        privateEventId, "Dinner with the Smiths", "Chez Moi", "London", "GB",
                        LocalDateTime.of(2026, 6, 1, 19, 0),
                        LocalDateTime.of(2026, 6, 1, 22, 0)));
    }

    @Test
    void timesAreTheVenueZoneWallClockRatherThanUtc() {
        // 7 PM London in June is 18:00Z; the page must say what a clock at the dinner would.
        PrivateEventId privateEventId = PrivateEventId.random();
        PrivateEventDetailsViewProjector projector = new PrivateEventDetailsViewProjector();

        projector.handle(Stream.of(stored(planned(privateEventId))));

        assertThat(projector.findById(privateEventId).orElseThrow().startsAt())
                .isEqualTo(LocalDateTime.of(2026, 6, 1, 19, 0));
    }

    @Test
    void unknownIdIsEmptyRatherThanSomeOtherEvening() {
        PrivateEventDetailsViewProjector projector = new PrivateEventDetailsViewProjector();

        projector.handle(Stream.of(stored(planned(PrivateEventId.random()))));

        assertThat(projector.findById(PrivateEventId.random()))
                .isEmpty();
    }

    private static PrivateEventPlanned planned(PrivateEventId privateEventId) {
        Address venue = new Address("1 Frith St", "London", "", "W1D 4TL", "GB", "London");
        return new PrivateEventPlanned(
                privateEventId, "Dinner with the Smiths", "Chez Moi", venue,
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, 19, 0), LONDON),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, 22, 0), LONDON));
    }

    private StoredEvent stored(Event event) {
        return new StoredEvent(sequence.incrementAndGet(), event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
