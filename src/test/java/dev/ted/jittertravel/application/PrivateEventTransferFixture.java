package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.PlanGroundTransferContext;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventMatchingLocationChanged;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.PlanGroundTransferRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * A private dinner whose every private value is a distinctive string, and a ground transfer to it
 * produced by the <em>real</em> handler and command — so a redaction test asserts what a stranger
 * could read of an event that came out of the write path, never of one a test typed by hand.
 */
public final class PrivateEventTransferFixture {

    public static final String TITLE = "Dinner with the Smiths";
    public static final String VENUE = "Chez Secret";
    public static final String STREET = "12 Hidden Lane";
    public static final String POSTAL_CODE = "80299";
    /** Where the schedule matches the event, not where it is: never a public value. */
    public static final String MATCHING_CITY = "Goldenville";
    public static final String CITY = "Denver";

    private static final ZoneId DENVER = ZoneId.of("America/Denver");

    private final PrivateEventId dinnerId = PrivateEventId.random();

    public PrivateEventId dinnerId() {
        return dinnerId;
    }

    public PrivateEventPlanned dinner() {
        return new PrivateEventPlanned(dinnerId, TITLE, VENUE,
                new Address(STREET, CITY, "CO", POSTAL_CODE, "US", null),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 15, 19, 0), DENVER),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 15, 22, 0), DENVER));
    }

    public PrivateEventMatchingLocationChanged matchedInAnotherCity() {
        return new PrivateEventMatchingLocationChanged(dinnerId, MATCHING_CITY);
    }

    /** DEN to the dinner, on its day, through the real handler and the real command. */
    public GroundTransferPlanned transferFromTheAirportToTheDinner(Event... history) {
        var endpoints = new TransferEndpointProjector(new StaticAirportCityResolver());
        endpoints.handle(Stream.of(history).map(PrivateEventTransferFixture::stored));
        var handler = new PlanGroundTransferHandler(new GroundTransferEndpointResolver(
                new HotelDetailsViewProjector(), new TrainDetailsViewProjector(),
                new GatheringDetailsViewProjector(), new ConferenceProjector(),
                new StaticAirportCityResolver(), new AirportZoneResolver(),
                new LocationZoneResolver(), endpoints));
        var command = handler.handle(new PlanGroundTransferRequest(
                UUID.randomUUID().toString(), "airport:DEN",
                "private-event:" + dinnerId.id(), "Susan drives",
                LocalDate.of(2026, 9, 15), LocalTime.of(17, 0), LocalTime.of(17, 45)));
        List<GroundTransferPlanned> events = command.execute(new PlanGroundTransferContext()).toList();
        return events.getFirst();
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
