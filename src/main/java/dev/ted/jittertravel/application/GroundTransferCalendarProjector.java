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
 * The publishable form of the route ({@code DEN → Lone Tree, CO, US}) used to ride along here as a
 * field no renderer read, because the redactor could not derive a city from a hotel name.
 * {@link PublicCalendarProjector} builds it from the event itself, so this projector no longer
 * carries anything on the public calendar's behalf.
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
                e.departsAt().localDateTime(),
                e.arrivesAt().localDateTime(),
                TAXI + route(label.ownerLabel(e.originAirportCode(), e.originName(), e.origin()),
                             label.ownerLabel(e.destinationAirportCode(), e.destinationName(), e.destination())),
                List.of(new SubtitleLine.Range(e.departsAt(), e.arrivesAt())),
                // A transfer has nothing to edit — correcting one means removing it and entering it
                // again — so its owner action is cancel, and the calendar carries the link. The
                // renderer gates it on isOwner, and it is never built for the public calendar.
                new EntryDetails.GroundTransfer(
                        "/ground-transfers/" + e.groundTransferId().id() + "/cancel")
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
