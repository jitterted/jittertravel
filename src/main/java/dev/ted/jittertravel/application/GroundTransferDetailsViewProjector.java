package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.GroundTransferCancelled;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects ground-transfer events into the {@link GroundTransferDetailsView} the cancel
 * confirmation page reads. Single-purpose, like {@link HotelDetailsViewProjector}: one view, keyed
 * by {@link GroundTransferId}, so a stale link resolves to nothing rather than to the wrong hop.
 * <p>
 * A cancelled transfer is removed outright — the page that offers cancelling must not offer it
 * twice, and there is no "cancelled" state anywhere else in the app either.
 */
public class GroundTransferDetailsViewProjector implements EventStreamConsumer {

    private final Map<GroundTransferId, GroundTransferDetailsView> viewsById = new ConcurrentHashMap<>();
    private final TransferEndpointLabel label = new TransferEndpointLabel();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                case GroundTransferPlanned e -> viewsById.put(e.groundTransferId(), toView(e));
                case GroundTransferCancelled e -> viewsById.remove(e.groundTransferId());
                default -> { /* not a ground-transfer event */ }
            }
        });
    }

    private GroundTransferDetailsView toView(GroundTransferPlanned e) {
        return new GroundTransferDetailsView(
                e.groundTransferId(),
                label.ownerLabel(e.originAirportCode(), e.originName(), e.origin()),
                label.ownerLabel(e.destinationAirportCode(), e.destinationName(), e.destination()),
                e.departsAt().localDateTime(),
                e.arrivesAt().localDateTime());
    }

    public Optional<GroundTransferDetailsView> findById(GroundTransferId id) {
        return Optional.ofNullable(viewsById.get(id));
    }
}
