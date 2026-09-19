package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.*;
import dev.ted.jittertravel.domain.AirportCityResolver;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    /**
     * One instance, four injection sites (the gap projector, both ground-transfer collaborators,
     * and {@code BookFlightController}, which needs {@code soleAirportFor} to prefill a fix link).
     * It was constructed inline at each of them until the fix-link slice gave it a fourth reader.
     */
    @Bean
    public AirportCityResolver airportCityResolver() {
        return new StaticAirportCityResolver();
    }

    @Bean
    public ViewerZonePolicy viewerZonePolicy() {
        return new ViewerZonePolicy();
    }

    @Bean
    public ViewerTodayZone viewerTodayZone(
            @Value("${jittertravel.today.fallback-zone:America/Los_Angeles}") String fallbackZone) {
        return new ViewerTodayZone(ZoneId.of(fallbackZone));
    }

    @Bean
    public EventPayloadUpcaster eventPayloadUpcaster(LocationZoneResolver locationZoneResolver,
                                                     AirportZoneResolver airportZoneResolver,
                                                     JsonMapper jsonMapper) {
        return EventPayloadUpcaster.standard(locationZoneResolver, airportZoneResolver, jsonMapper);
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
     * See {@link EventJsonMapperFactory} for why the pin exists and what proves it today (the
     * equivalence test against the auto-configured mapper was retired 2026-09-07, its one-time
     * migration proof spent).
     */
    @Bean
    public JsonMapper jsonMapper() {
        return EventJsonMapperFactory.create();
    }

    @Bean
    public EventStore eventStore(MeterRegistry meterRegistry,
                                 PostgresPersister persister,
                                 Clock clock,
                                 ExecutorService eventReactorExecutor) {
        return new EventStore(meterRegistry, persister, clock, eventReactorExecutor);
    }

    /**
     * The one thread every {@link EventReactor} runs on. Bounded, single-threaded, and it drops
     * rather than blocks — each of the three is load-bearing, and
     * {@code docs/FamilyEmailNotificationsPlan.md} §4.1 has the full reasoning.
     *
     * <p><strong>One thread is a correctness property, not a performance choice.</strong> It is what
     * makes events reach every reactor in log order, and what lets a reactor read the log to decide
     * whether it has already acted without a second trigger for the same subject racing it.
     * Thread-per-task — including a virtual-thread executor — loses both.
     *
     * <p><strong>{@code AbortPolicy}, never {@code CallerRunsPolicy}.</strong> Caller-runs would run
     * the reactor on the append thread, inside {@code append}, holding {@code transactionLock}; its
     * own append would then interleave sequence assignment with the batch still being written. A
     * dropped notification is a counted loss, and that is silent corruption.
     *
     * <p><strong>{@code destroyMethod} must be stated, because Spring's inferred default is wrong
     * here.</strong> Spring infers {@code shutdown()} for an {@code ExecutorService} bean, and
     * {@code shutdown()} <em>drains</em> the queue — every queued task would run against a closing
     * datasource. {@code shutdownNow()} makes the drop at shutdown the real behaviour. Note what it
     * also does: it interrupts the task already running, so a reactor stopped between an external
     * call and its own append is the sent-but-unrecorded case. That is the accepted trade — one
     * in-flight task rather than the whole queue — and the command row is what makes it visible.
     */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService eventReactorExecutor() {
        return new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(REACTOR_QUEUE_CAPACITY),
                EventSourcingConfig::reactorThread,
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static final int REACTOR_QUEUE_CAPACITY = 100;

    private static Thread reactorThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "event-reactor");
        thread.setDaemon(true);
        return thread;
    }

    @Bean
    public ConferenceProjector conferenceProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new ConferenceProjector());
    }

    @Bean
    public OneOffTaskProjector oneOffTaskProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new OneOffTaskProjector());
    }

    /** The declared tasks live in the registry's own code, so there is nothing to configure here. */
    @Bean
    public OneOffTaskRegistry oneOffTaskRegistry() {
        return new OneOffTaskRegistry();
    }

    @Bean
    public OneOffTasks oneOffTasksApplicationService(OneOffTaskRegistry registry,
                                                     OneOffTaskProjector projector,
                                                     CommandExecutor commandExecutor) {
        return new OneOffTasks(registry, projector, commandExecutor);
    }

    /**
     * Takes {@link OpenCfp} because the plan form can carry a CFP: one submit, two commands, plan
     * first (see {@link ConferencePlanning}). Delegating rather than building the second command
     * here is what reuses that service's open-space refusal instead of writing it a second time.
     */
    @Bean
    public ConferencePlanning conferenceApplicationService(CommandExecutor commandExecutor,
                                                          LocationZoneResolver locationZoneResolver,
                                                          OpenCfp openCfp) {
        return new ConferencePlanning(commandExecutor, locationZoneResolver, openCfp);
    }

    /**
     * No projector dependency for the write path: {@link DeclineConference} folds its decision fact
     * from the event stream (R1), so the executor is all it needs. (The controller separately reads
     * {@link ConferenceProjector} to render the confirmation page.)
     */
    @Bean
    public DeclineConference declineConferenceApplicationService(CommandExecutor commandExecutor) {
        return new DeclineConference(commandExecutor);
    }

    /** Same shape as {@link DeclineConference}: the decision fact is folded from the stream (R1). */
    @Bean
    public ConfirmConferenceAttendance confirmConferenceAttendanceApplicationService(
            CommandExecutor commandExecutor) {
        return new ConfirmConferenceAttendance(commandExecutor);
    }

    /**
     * Recording a CFP needs two facts, both off the conference's own plan: that it is still live,
     * and that it forms its program through a call for papers at all.
     */
    @Bean
    public OpenCfp openCfpApplicationService(CommandExecutor commandExecutor) {
        return new OpenCfp(commandExecutor);
    }

    /**
     * The whole speaking axis in one service: the five moves share a state machine and a fold, so
     * they share a service rather than getting one each ({@link TalkTracking}).
     */
    @Bean
    public TalkTracking talkTrackingApplicationService(CommandExecutor commandExecutor) {
        return new TalkTracking(commandExecutor);
    }

    @Bean
    public FlightBooking flightBookingApplicationService(CommandExecutor commandExecutor,
                                                         AirportZoneResolver airportZoneResolver,
                                                         LiveScheduledLegs liveScheduledLegs) {
        return new FlightBooking(commandExecutor, airportZoneResolver, liveScheduledLegs);
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
                                                       AirportZoneResolver airportZoneResolver,
                                                       LiveScheduledLegs liveScheduledLegs) {
        return new ChangeFlight(commandExecutor, flightDetailsViewProjector, airportZoneResolver,
                liveScheduledLegs);
    }

    @Bean
    public BackupService backupService(PostgresPersister persister, CommandExecutor commandExecutor,
                                       EventPayloadUpcaster eventPayloadUpcaster, JsonMapper jsonMapper) {
        return new BackupService(persister, commandExecutor, eventPayloadUpcaster, jsonMapper);
    }

    @Bean
    public LegacyEventMigration legacyEventMigration(PostgresPersister persister,
                                                     EventPayloadUpcaster eventPayloadUpcaster,
                                                     JsonMapper jsonMapper, CommandExecutor commandExecutor) {
        return new LegacyEventMigration(persister, eventPayloadUpcaster, jsonMapper, commandExecutor);
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

    /**
     * One fold shared by all four write paths that refuse an overlapping journey — book and change,
     * train and flight. Reads the event stream through {@link CommandExecutor}, never a projector
     * (R1), and applies cancellations so a cancelled trip cannot block a booking.
     */
    @Bean
    public LiveScheduledLegs liveScheduledLegs(CommandExecutor commandExecutor) {
        return new LiveScheduledLegs(commandExecutor);
    }

    @Bean
    public ChangeTrain changeTrainApplicationService(CommandExecutor commandExecutor,
                                                     TrainDetailsViewProjector trainDetailsViewProjector,
                                                     LocationZoneResolver locationZoneResolver,
                                                     LiveScheduledLegs liveScheduledLegs) {
        return new ChangeTrain(commandExecutor, trainDetailsViewProjector, locationZoneResolver,
                liveScheduledLegs);
    }

    @Bean
    public TrainBooking trainBookingApplicationService(CommandExecutor commandExecutor,
                                                       LocationZoneResolver locationZoneResolver,
                                                       LiveScheduledLegs liveScheduledLegs) {
        return new TrainBooking(commandExecutor, locationZoneResolver, liveScheduledLegs);
    }

    @Bean
    public ItineraryProjector itineraryProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new ItineraryProjector());
    }

    @Bean
    public ScheduleGapProjector scheduleGapProjector(ProjectorBootstrapper bootstrapper,
                                                     AirportCityResolver airportCityResolver,
                                                     @Value("${jittertravel.home-cities:}") List<String> homeCityNames) {
        return bootstrapper.register(new ScheduleGapProjector(airportCityResolver,
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

    /**
     * The whole calendar an anonymous visitor may see, built from events on its own rather than
     * derived from the owner's read model. It is deliberately a peer of the seven owner calendar
     * projectors and not downstream of them: see the class comment, and decision S2 in
     * {@code docs/RendererVsProjectorResponsibilities.md}.
     */
    @Bean
    public PublicCalendarProjector publicCalendarProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new PublicCalendarProjector());
    }

    @Bean
    public PrivateEventPlanning privateEventPlanningApplicationService(CommandExecutor commandExecutor,
                                                                       LocationZoneResolver locationZoneResolver) {
        return new PrivateEventPlanning(commandExecutor, locationZoneResolver);
    }

    @Bean
    public PrivateEventCalendarProjector privateEventCalendarProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new PrivateEventCalendarProjector());
    }

    /** The cancel confirmation page's read model: one event, by id, gone once it is cancelled. */
    @Bean
    public PrivateEventDetailsViewProjector privateEventDetailsViewProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new PrivateEventDetailsViewProjector());
    }

    /** The /planned-private-events list's read model: every live event, address included. */
    @Bean
    public PlannedPrivateEventsProjector plannedPrivateEventsProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new PlannedPrivateEventsProjector());
    }

    /**
     * The matching-location page's read model. The second projector in the tree to apply
     * {@code PrivateEventMatchingLocationChanged} — the first being {@link ScheduleGapProjector},
     * which is what the override exists for. This one applies it so the form offers the value
     * already in force.
     */
    @Bean
    public PrivateEventMatchingLocationViewProjector privateEventMatchingLocationViewProjector(
            ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new PrivateEventMatchingLocationViewProjector());
    }

    /**
     * No projector dependency, like the cancels around it: {@link ChangePrivateEventMatchingLocation}
     * folds its one decision fact — does this evening still exist — from the event stream (R1), not
     * from the read model above. No {@code now}: re-matching is not time-gated, and a past evening
     * is the one still shaping away days with the wrong city.
     */
    @Bean
    public ChangePrivateEventMatchingLocation changePrivateEventMatchingLocationApplicationService(
            CommandExecutor commandExecutor) {
        return new ChangePrivateEventMatchingLocation(commandExecutor);
    }

    /**
     * No projector dependency, for the same reason as {@link CancelGroundTransfer} below:
     * {@link CancelPrivateEvent} folds its one decision fact from the event stream (R1), not from a
     * read model. No {@code now} either — cancelling is not time-gated.
     */
    @Bean
    public CancelPrivateEvent cancelPrivateEventApplicationService(CommandExecutor commandExecutor) {
        return new CancelPrivateEvent(commandExecutor);
    }

    /**
     * No projector dependency, like the three cancels around it: {@link CancelTrain} folds its one
     * decision fact from the event stream (R1), not from a read model — deliberately unlike its
     * sibling {@link ChangeTrain}, which reads existence from the details projector. No {@code now}
     * either; cancelling is not time-gated.
     */
    @Bean
    public CancelTrain cancelTrainApplicationService(CommandExecutor commandExecutor) {
        return new CancelTrain(commandExecutor);
    }

    /**
     * The endpoint resolver holds every lookup a transfer endpoint token can need: the hotel's
     * address (a snapshot source), and the airport city/zone tables. Ted cleared the dependency
     * gate for it (D8).
     */
    @Bean
    public GroundTransferEndpointResolver groundTransferEndpointResolver(
            HotelDetailsViewProjector hotelDetailsViewProjector,
            TrainDetailsViewProjector trainDetailsViewProjector,
            AirportCityResolver airportCityResolver,
            AirportZoneResolver airportZoneResolver,
            LocationZoneResolver locationZoneResolver) {
        return new GroundTransferEndpointResolver(hotelDetailsViewProjector,
                trainDetailsViewProjector, airportCityResolver, airportZoneResolver,
                locationZoneResolver);
    }

    /**
     * The ground-transfer form's own read model — endpoints, not flights and stays converted into
     * endpoints. It resolves an airport's city as it reads, which is why it takes the table.
     */
    @Bean
    public TransferEndpointProjector transferEndpointProjector(ProjectorBootstrapper bootstrapper,
                                                               AirportCityResolver airportCityResolver) {
        return bootstrapper.register(new TransferEndpointProjector(airportCityResolver));
    }

    @Bean
    public GroundTransferEndpointOptions groundTransferEndpointOptions(
            TransferEndpointProjector transferEndpointProjector) {
        return new GroundTransferEndpointOptions(transferEndpointProjector);
    }

    /** No {@code now} anywhere on this path: a ground transfer has no future-date rule (D6). */
    @Bean
    public GroundTransferPlanning groundTransferPlanningApplicationService(
            CommandExecutor commandExecutor, GroundTransferEndpointResolver endpointResolver) {
        return new GroundTransferPlanning(commandExecutor, endpointResolver);
    }

    @Bean
    public GroundTransferCalendarProjector groundTransferCalendarProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new GroundTransferCalendarProjector());
    }

    @Bean
    public GroundTransferDetailsViewProjector groundTransferDetailsViewProjector(ProjectorBootstrapper bootstrapper) {
        return bootstrapper.register(new GroundTransferDetailsViewProjector());
    }

    /**
     * No projector dependency: like {@link CancelHotel}, {@link CancelGroundTransfer} folds its one
     * decision fact from the event stream (R1), not from a read model.
     */
    @Bean
    public CancelGroundTransfer cancelGroundTransferApplicationService(CommandExecutor commandExecutor) {
        return new CancelGroundTransfer(commandExecutor);
    }

    @Bean
    public CalendarAggregator calendarAggregator(ConferenceCalendarProjector conferenceCalendarProjector,
                                                 FlightCalendarProjector flightCalendarProjector,
                                                 TrainCalendarProjector trainCalendarProjector,
                                                 HotelCalendarProjector hotelCalendarProjector,
                                                 GatheringCalendarProjector gatheringCalendarProjector,
                                                 PrivateEventCalendarProjector privateEventCalendarProjector,
                                                 GroundTransferCalendarProjector groundTransferCalendarProjector) {
        return new CalendarAggregator(conferenceCalendarProjector, flightCalendarProjector,
                trainCalendarProjector, hotelCalendarProjector, gatheringCalendarProjector,
                privateEventCalendarProjector, groundTransferCalendarProjector);
    }
}
