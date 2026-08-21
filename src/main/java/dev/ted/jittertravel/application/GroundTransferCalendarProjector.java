package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.GroundTransferCancelled;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects {@link GroundTransferPlanned} into the owner's full {@link CalendarEntry}: the route as
 * Ted knows it ({@code DEN → Marriott Lone Tree}), and the times. That is the whole owner view —
 * naming the same journey a second time as cities was noise on the entry (Ted, 2026-08-20).
 * <p>
 * The publishable form of the route ({@code DEN → Lone Tree, CO, US}) still has to exist, because
 * {@link CalendarEntryRedactor} cannot derive a city from a hotel name. It rides in
 * {@code CalendarEntry.publicRoute}, which no renderer reads — the redactor's GROUND_TRANSFER
 * branch is its only consumer.
 */
public class GroundTransferCalendarProjector implements EventStreamConsumer {

    /**
     * Leads the title, as the plane leads a flight's and the train a train's. Only the title: the
     * publishable route is a subtitle line, and flights and trains put no icon on theirs either.
     */
    private static final String TAXI = "🚕 ";

    private final Map<GroundTransferId, CalendarEntry> entries = new ConcurrentHashMap<>();
    private final TransferEndpointLabel label = new TransferEndpointLabel();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case GroundTransferPlanned e -> entries.put(e.groundTransferId(), toEntry(e));
                // A hard removal: a cancelled transfer leaves the calendar entirely, for every
                // viewer. There is no "cancelled" rendering to keep.
                case GroundTransferCancelled e -> entries.remove(e.groundTransferId());
                default -> { /* not a ground-transfer event */ }
            }
        });
    }

    private CalendarEntry toEntry(GroundTransferPlanned e) {
        // Bucketed on the transfer-zone local day; both ends share one zone, so a transfer is
        // normally a single day column.
        return new CalendarEntry(
                EntryKind.GROUND_TRANSFER,
                e.departsAt().localDateTime(),
                e.arrivesAt().localDateTime(),
                TAXI + route(label.ownerLabel(e.originAirportCode(), e.originName(), e.origin()),
                             label.ownerLabel(e.destinationAirportCode(), e.destinationName(), e.destination())),
                List.of(new SubtitleLine.Range(e.departsAt(), e.arrivesAt())),
                null,
                null,
                null,
                false,
                null,
                null,
                route(label.publicLabel(e.originAirportCode(), e.origin()),
                      label.publicLabel(e.destinationAirportCode(), e.destination())),
                // A transfer has nothing to edit — correcting one means removing it and entering
                // it again — so its owner action is cancel, and the calendar carries the link. The
                // renderer gates it on isOwner and the redactor drops it.
                "/ground-transfers/" + e.groundTransferId().id() + "/cancel"
        );
    }

    /** A spaced arrow, so the browser can wrap a long route rather than widening the day column. */
    private String route(String from, String to) {
        return from + " → " + to;
    }

    public List<CalendarEntry> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
