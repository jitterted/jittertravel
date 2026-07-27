package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;

@Configuration
public class EventSourcingConfig {

    @Bean
    public LocationZoneResolver locationZoneResolver() {
        return new LocationZoneResolver();
    }

    @Bean
    public AirportZoneResolver airportZoneResolver() {
        return new AirportZoneResolver();
    }

    @Bean
    public EventPayloadUpcaster eventPayloadUpcaster(LocationZoneResolver locationZoneResolver,
                                                     AirportZoneResolver airportZoneResolver,
                                                     JsonMapper jsonMapper) {
        return new EventPayloadUpcaster(locationZoneResolver, airportZoneResolver, jsonMapper);
    }

    @Bean
    public LocationAuditProjector locationAuditProjector(EventStore eventStore) {
        LocationAuditProjector projector = new LocationAuditProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public LocationZoneAudit locationZoneAudit(LocationZoneResolver locationZoneResolver,
                                              AirportZoneResolver airportZoneResolver) {
        return new LocationZoneAudit(locationZoneResolver, airportZoneResolver);
    }

    /**
     * Pins the JsonMapper used for event/command (de)serialization to a single, version-controlled
     * config shared with the serialization tests, instead of Spring Boot's auto-configured mapper.
     * See {@link EventJsonMapperFactory}; {@code EventJsonMapperEquivalenceTest} proves this bean
     * is byte-for-byte equivalent to the previously auto-configured one.
     */
    @Bean
    public JsonMapper jsonMapper() {
        return EventJsonMapperFactory.create();
    }

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
    public FlightBooking flightBookingApplicationService(CommandExecutor commandExecutor,
                                                         AirportZoneResolver airportZoneResolver) {
        return new FlightBooking(commandExecutor, airportZoneResolver);
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
    public ChangeFlight changeFlightApplicationService(CommandExecutor commandExecutor,
                                                       FlightDetailsViewProjector flightDetailsViewProjector,
                                                       AirportZoneResolver airportZoneResolver) {
        return new ChangeFlight(commandExecutor, flightDetailsViewProjector, airportZoneResolver);
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
    public HotelBooking hotelBookingApplicationService(CommandExecutor commandExecutor,
                                                       LocationZoneResolver locationZoneResolver) {
        return new HotelBooking(commandExecutor, locationZoneResolver);
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

    @Bean
    public HotelDetailsViewProjector hotelDetailsViewProjector(EventStore eventStore) {
        HotelDetailsViewProjector projector = new HotelDetailsViewProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public ChangeHotel changeHotelApplicationService(CommandExecutor commandExecutor,
                                                     HotelDetailsViewProjector hotelDetailsViewProjector,
                                                     LocationZoneResolver locationZoneResolver) {
        return new ChangeHotel(commandExecutor, hotelDetailsViewProjector, locationZoneResolver);
    }

    @Bean
    public BookedTrainsProjector bookedTrainsProjector(EventStore eventStore) {
        BookedTrainsProjector projector = new BookedTrainsProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public TrainCalendarProjector trainCalendarProjector(EventStore eventStore) {
        TrainCalendarProjector projector = new TrainCalendarProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public TrainDetailsViewProjector trainDetailsViewProjector(EventStore eventStore) {
        TrainDetailsViewProjector projector = new TrainDetailsViewProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public ChangeTrain changeTrainApplicationService(CommandExecutor commandExecutor,
                                                     TrainDetailsViewProjector trainDetailsViewProjector,
                                                     LocationZoneResolver locationZoneResolver) {
        return new ChangeTrain(commandExecutor, trainDetailsViewProjector, locationZoneResolver);
    }

    @Bean
    public TrainBooking trainBookingApplicationService(CommandExecutor commandExecutor,
                                                       LocationZoneResolver locationZoneResolver) {
        return new TrainBooking(commandExecutor, locationZoneResolver);
    }

    @Bean
    public ItineraryProjector itineraryProjector(EventStore eventStore) {
        ItineraryProjector projector = new ItineraryProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public ScheduleGapProjector scheduleGapProjector(EventStore eventStore,
                                                     @Value("${jittertravel.home-cities:}") List<String> homeCityNames) {
        ScheduleGapProjector projector = new ScheduleGapProjector(new StaticAirportCityResolver(),
                                                                 new HomeCities(homeCityNames));
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public GatheringPlanning gatheringPlanningApplicationService(CommandExecutor commandExecutor) {
        return new GatheringPlanning(commandExecutor);
    }

    @Bean
    public PlannedGatheringsProjector plannedGatheringsProjector(EventStore eventStore) {
        PlannedGatheringsProjector projector = new PlannedGatheringsProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public GatheringDetailsViewProjector gatheringDetailsViewProjector(EventStore eventStore) {
        GatheringDetailsViewProjector projector = new GatheringDetailsViewProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public ChangeGathering changeGatheringApplicationService(
            CommandExecutor commandExecutor, GatheringDetailsViewProjector gatheringDetailsViewProjector) {
        return new ChangeGathering(commandExecutor, gatheringDetailsViewProjector);
    }

    @Bean
    public GatheringCalendarProjector gatheringCalendarProjector(EventStore eventStore) {
        GatheringCalendarProjector projector = new GatheringCalendarProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public ConferenceMigrationService conferenceMigrationService(
            TentativeConferenceProjector tentativeConferenceProjector, CommandExecutor commandExecutor) {
        return new ConferenceMigrationService(tentativeConferenceProjector, commandExecutor);
    }

    @Bean
    public CalendarAggregator calendarAggregator(ConferenceCalendarProjector conferenceCalendarProjector,
                                                 FlightCalendarProjector flightCalendarProjector,
                                                 TrainCalendarProjector trainCalendarProjector,
                                                 HotelCalendarProjector hotelCalendarProjector,
                                                 GatheringCalendarProjector gatheringCalendarProjector) {
        return new CalendarAggregator(conferenceCalendarProjector, flightCalendarProjector,
                trainCalendarProjector, hotelCalendarProjector, gatheringCalendarProjector);
    }
}
