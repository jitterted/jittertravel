package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
public class EventSourcingConfig {

    @Bean
    public EventStore eventStore(MeterRegistry meterRegistry, PostgresPersister persister) {
        return new EventStore(meterRegistry, persister);
    }

    @Bean
    public TentativeConferenceProjector tentativeConferenceProjector(EventStore eventStore) {
        TentativeConferenceProjector projector = new TentativeConferenceProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public ConferencePlanning conferenceApplicationService(EventStore eventStore, PostgresPersister persister) {
        return new ConferencePlanning(eventStore, persister);
    }

    @Bean
    public FlightBooking flightBookingApplicationService(EventStore eventStore, PostgresPersister persister) {
        return new FlightBooking(eventStore, persister);
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public ConferenceCalendarProjector conferenceCalendarProjector(EventStore eventStore) {
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public FlightCalendarProjector flightCalendarProjector(EventStore eventStore) {
        FlightCalendarProjector projector = new FlightCalendarProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public BookedFlightsProjector bookedFlightsProjector(EventStore eventStore) {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public FlightDetailsViewProjector flightDetailsViewProjector(EventStore eventStore) {
        FlightDetailsViewProjector projector = new FlightDetailsViewProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public ChangeFlight changeFlightApplicationService(EventStore eventStore, PostgresPersister persister) {
        return new ChangeFlight(eventStore, persister);
    }

    @Bean
    public CommandImporter commandImporter(PostgresPersister persister, EventStore eventStore,
                                          tools.jackson.databind.json.JsonMapper jsonMapper) {
        return new CommandImporter(persister, eventStore, jsonMapper);
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public CommandExecutor commandExecutor(PostgresPersister persister, EventStore eventStore) {
        return new CommandExecutor(persister, eventStore);
    }

    @Bean
    public HotelBooking hotelBookingApplicationService(CommandExecutor commandExecutor, Clock clock) {
        return new HotelBooking(commandExecutor, clock);
    }

    @Bean
    public BookedHotelsProjector bookedHotelsProjector(EventStore eventStore) {
        BookedHotelsProjector projector = new BookedHotelsProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public TentativeHotelBookingsProjector tentativeHotelBookingsProjector(EventStore eventStore) {
        TentativeHotelBookingsProjector projector = new TentativeHotelBookingsProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public TentativeHotelBookingProjector tentativeHotelBookingProjector(EventStore eventStore) {
        TentativeHotelBookingProjector projector = new TentativeHotelBookingProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public HotelCalendarProjector hotelCalendarProjector(EventStore eventStore) {
        HotelCalendarProjector projector = new HotelCalendarProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }
}
