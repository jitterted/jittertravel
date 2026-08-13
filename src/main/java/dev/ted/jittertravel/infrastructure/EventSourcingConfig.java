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
    public ViewerZonePolicy viewerZonePolicy() {
        return new ViewerZonePolicy();
    }

    @Bean
    public EventPayloadUpcaster eventPayloadUpcaster(LocationZoneResolver locationZoneResolver,
                                                     AirportZoneResolver airportZoneResolver,
                                                     JsonMapper jsonMapper) {
        return new EventPayloadUpcaster(locationZoneResolver, airportZoneResolver, jsonMapper);
    }

    /**
     * Subscribes a projector to the {@link EventStore} and replays history into it before it is
     * returned as a bean. Every projector bean below is one {@code bootstrapper.register(...)} call
     * instead of the old {@code new / subscribe / handle(findAll())} triple.
     */
    @Bean
    public ProjectorBootstrapper projectorBootstrapper(EventStore eventStore) {
        return new ProjectorBootstrapper(eventStore);
    }

    @Bean
    public LocationAuditProjector locationAuditProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new LocationAuditProjector());
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
    public TentativeConferenceProjector tentativeConferenceProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new TentativeConferenceProjector());
    }

    @Bean
    public ConferencePlanning conferenceApplicationService(CommandExecutor commandExecutor,
                                                          LocationZoneResolver locationZoneResolver) {
        return new ConferencePlanning(commandExecutor, locationZoneResolver);
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
    public ConferenceCalendarProjector conferenceCalendarProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new ConferenceCalendarProjector());
    }

    @Bean
    public FlightCalendarProjector flightCalendarProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new FlightCalendarProjector());
    }

    @Bean
    public BookedFlightsProjector bookedFlightsProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new BookedFlightsProjector());
    }

    @Bean
    public FlightDetailsViewProjector flightDetailsViewProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new FlightDetailsViewProjector());
    }

    @Bean
    public ChangeFlight changeFlightApplicationService(CommandExecutor commandExecutor,
                                                       FlightDetailsViewProjector flightDetailsViewProjector,
                                                       AirportZoneResolver airportZoneResolver) {
        return new ChangeFlight(commandExecutor, flightDetailsViewProjector, airportZoneResolver);
    }

    @Bean
    public BackupService backupService(PostgresPersister persister, CommandExecutor commandExecutor,
                                       EventPayloadUpcaster eventPayloadUpcaster, JsonMapper jsonMapper) {
        return new BackupService(persister, commandExecutor, eventPayloadUpcaster, jsonMapper);
    }

    /**
     * Labels backups as {@code production} or {@code local}. Railway injects
     * {@code RAILWAY_ENVIRONMENT_NAME} on the hosted service; locally it is absent, so the marker
     * defaults to empty and {@link BackupSource} resolves to {@code local}.
     */
    @Bean
    public BackupSource backupSource(@Value("${RAILWAY_ENVIRONMENT_NAME:}") String railwayEnvironmentName) {
        return new BackupSource(railwayEnvironmentName);
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
    public BookedHotelsProjector bookedHotelsProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new BookedHotelsProjector());
    }

    @Bean
    public TentativeHotelBookingsProjector tentativeHotelBookingsProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new TentativeHotelBookingsProjector());
    }

    @Bean
    public TentativeHotelBookingProjector tentativeHotelBookingProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new TentativeHotelBookingProjector());
    }

    @Bean
    public HotelCalendarProjector hotelCalendarProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new HotelCalendarProjector());
    }

    @Bean
    public HotelDetailsViewProjector hotelDetailsViewProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new HotelDetailsViewProjector());
    }

    @Bean
    public ChangeHotel changeHotelApplicationService(CommandExecutor commandExecutor,
                                                     HotelDetailsViewProjector hotelDetailsViewProjector,
                                                     LocationZoneResolver locationZoneResolver) {
        return new ChangeHotel(commandExecutor, hotelDetailsViewProjector, locationZoneResolver);
    }

    /**
     * No projector dependency: {@link CancelHotel} folds its decision facts from the event stream
     * (R1), so the executor is all it needs.
     */
    @Bean
    public CancelHotel cancelHotelApplicationService(CommandExecutor commandExecutor) {
        return new CancelHotel(commandExecutor);
    }

    @Bean
    public BookedTrainsProjector bookedTrainsProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new BookedTrainsProjector());
    }

    @Bean
    public TrainCalendarProjector trainCalendarProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new TrainCalendarProjector());
    }

    @Bean
    public TrainDetailsViewProjector trainDetailsViewProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new TrainDetailsViewProjector());
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
    public ItineraryProjector itineraryProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new ItineraryProjector());
    }

    @Bean
    public ScheduleGapProjector scheduleGapProjector(ProjectorBootstrapper bootstrapper,
                                                     @Value("${jittertravel.home-cities:}") List<String> homeCityNames) {
        return bootstrapper.register(new ScheduleGapProjector(new StaticAirportCityResolver(),
                                                              new HomeCities(homeCityNames)));
    }

    @Bean
    public GatheringPlanning gatheringPlanningApplicationService(CommandExecutor commandExecutor,
                                                                 LocationZoneResolver locationZoneResolver) {
        return new GatheringPlanning(commandExecutor, locationZoneResolver);
    }

    @Bean
    public PlannedGatheringsProjector plannedGatheringsProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new PlannedGatheringsProjector());
    }

    @Bean
    public GatheringDetailsViewProjector gatheringDetailsViewProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new GatheringDetailsViewProjector());
    }

    @Bean
    public ChangeGathering changeGatheringApplicationService(
            CommandExecutor commandExecutor,
            GatheringDetailsViewProjector gatheringDetailsViewProjector,
            LocationZoneResolver locationZoneResolver) {
        return new ChangeGathering(commandExecutor, gatheringDetailsViewProjector, locationZoneResolver);
    }

    @Bean
    public GatheringCalendarProjector gatheringCalendarProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new GatheringCalendarProjector());
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
