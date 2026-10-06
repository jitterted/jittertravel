package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventMatchingLocationChanged;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The schedule report and the ground-transfer form, joined: the gap
 * {@code /schedule-problems} raises, and the endpoint the fix link's form settles on, from the same
 * real events through the two real read models. Each of them is tested alone elsewhere; this is the
 * only place that says a gap <em>into</em> a gathering, a conference or a private event lands on a
 * form that can express it (Ted, 2026-10-06 — the defect the endpoint kinds exist to fix).
 * <p>
 * The scenario is the one the private-event matching feature was built for: a conference in Lone
 * Tree, and an evening in Centennial the schedule reads as a journey out to it.
 */
class EndpointsCloseScheduleGapsTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final Address LONE_TREE =
            new Address("10035 Park Meadows Dr", "Lone Tree", "CO", "80124", "US", "Lone Tree");
    private static final Address CENTENNIAL =
            new Address("7 Dry Creek Rd", "Centennial", "CO", "80112", "US", "Centennial");

    private final ScheduleGapProjector schedule =
            new ScheduleGapProjector(new StaticAirportCityResolver());
    private final TransferEndpointProjector endpoints =
            new TransferEndpointProjector(new StaticAirportCityResolver());

    @Test
    void aGapOutToAGatheringSettlesOnItsStart() {
        GatheringId dinner = GatheringId.random();
        given(conference(),
              new GatheringPlanned(dinner, "Speaker dinner", "Dry Creek Grill", CENTENNIAL,
                      at("2026-10-02 19:00"), at("2026-10-02 22:00"), false, ""));

        assertThat(destinationOfTheGap())
                .map(TransferEndpointOption::token)
                .hasValue("gathering:" + dinner.id());
    }

    @Test
    void aGapOutToAPrivateEventSettlesOnItsStart() {
        PrivateEventId dinner = PrivateEventId.random();
        given(conference(),
              new PrivateEventPlanned(dinner, "Dinner with the Smiths", "Chez Moi", CENTENNIAL,
                      at("2026-10-02 19:00"), at("2026-10-02 22:00")));

        assertThat(destinationOfTheGap())
                .map(TransferEndpointOption::token)
                .hasValue("private-event:" + dinner.id());
    }

    /** The matching city is what the schedule reports the gap in, so it is what the option must match. */
    @Test
    void aPrivateEventMatchedInAnotherCitySettlesOnItUnderThatCity() {
        PrivateEventId dinner = PrivateEventId.random();
        given(conference(),
              new PrivateEventPlanned(dinner, "Dinner with the Smiths", "Chez Moi", CENTENNIAL,
                      at("2026-10-02 19:00"), at("2026-10-02 22:00")),
              new PrivateEventMatchingLocationChanged(dinner, "Boulder"));

        assertThat(destinationOfTheGap())
                .map(TransferEndpointOption::token)
                .hasValue("private-event:" + dinner.id());
    }

    @Test
    void aGapOutToAConferenceSettlesOnItsStart() {
        ConferenceId conference = ConferenceId.random();
        given(new GatheringPlanned(GatheringId.random(), "Home dinner", "Home", LONE_TREE,
                      at("2026-09-30 19:00"), at("2026-09-30 22:00"), false, ""),
              new ConferencePlanned(conference, "Craft Conf", at("2026-10-02 08:00"),
                      at("2026-10-03 18:00"), "Dry Creek Center", CENTENNIAL,
                      ConferenceFormat.CALL_FOR_PAPERS));

        assertThat(destinationOfTheGap())
                .map(TransferEndpointOption::token)
                .hasValue("conference:" + conference.id());
    }

    /** The other direction: leaving an evening in Centennial for the conference in Lone Tree. */
    @Test
    void aGapLeavingAGatheringSettlesOnItsEnd() {
        GatheringId dinner = GatheringId.random();
        given(new GatheringPlanned(dinner, "Speaker dinner", "Dry Creek Grill", CENTENNIAL,
                      at("2026-09-30 19:00"), at("2026-09-30 22:00"), false, ""),
              conference());

        assertThat(originOfTheGap())
                .map(TransferEndpointOption::token)
                .hasValue("gathering:" + dinner.id());
    }

    @Test
    void aGapLeavingAPrivateEventSettlesOnItsEnd() {
        PrivateEventId dinner = PrivateEventId.random();
        given(new PrivateEventPlanned(dinner, "Dinner with the Smiths", "Chez Moi", CENTENNIAL,
                      at("2026-09-30 19:00"), at("2026-09-30 22:00")),
              conference());

        assertThat(originOfTheGap())
                .map(TransferEndpointOption::token)
                .hasValue("private-event:" + dinner.id());
    }

    @Test
    void aGapLeavingAConferenceSettlesOnItsEnd() {
        ConferenceId earlier = ConferenceId.random();
        given(new ConferencePlanned(earlier, "Craft Conf", at("2026-09-28 08:00"),
                      at("2026-09-30 18:00"), "Dry Creek Center", CENTENNIAL,
                      ConferenceFormat.CALL_FOR_PAPERS),
              conference());

        assertThat(originOfTheGap())
                .map(TransferEndpointOption::token)
                .hasValue("conference:" + earlier.id());
    }

    /**
     * Two candidates in the gap's city on the gap's days mean the form must ask, as it does for two
     * stays: a wrong endpoint would write a transfer that removes the very gap it was meant to close.
     */
    @Test
    void twoEventsInTheGapsCitySettleNothing() {
        given(conference(),
              new GatheringPlanned(GatheringId.random(), "Speaker dinner", "Dry Creek Grill",
                      CENTENNIAL, at("2026-10-02 19:00"), at("2026-10-02 22:00"), false, ""),
              new PrivateEventPlanned(PrivateEventId.random(), "Dinner", "Chez Moi", CENTENNIAL,
                      at("2026-10-02 18:00"), at("2026-10-02 21:00")));

        assertThat(destinationOfTheGap()).isEmpty();
    }

    private Optional<TransferEndpointOption> destinationOfTheGap() {
        return choices().destinationFor(theGap());
    }

    private Optional<TransferEndpointOption> originOfTheGap() {
        return choices().originFor(theGap());
    }

    private GroundTransferEndpointChoices choices() {
        return new GroundTransferEndpointOptions(endpoints).choicesAt(NOW);
    }

    private ScheduleProblem.MissingTravel theGap() {
        return schedule.problems().stream()
                .filter(ScheduleProblem.MissingTravel.class::isInstance)
                .map(ScheduleProblem.MissingTravel.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the scenario raised no missing-travel gap"));
    }

    private static ConferencePlanned conference() {
        return new ConferencePlanned(ConferenceId.random(), "Rocky Mountain Java",
                at("2026-10-01 08:00"), at("2026-10-03 18:00"), "Park Meadows", LONE_TREE,
                ConferenceFormat.CALL_FOR_PAPERS);
    }

    private void given(Event... events) {
        List<StoredEvent> stored = Stream.of(events).map(EndpointsCloseScheduleGapsTest::stored).toList();
        schedule.handle(stored.stream());
        endpoints.handle(stored.stream());
    }

    private static ZonedTimestamp at(String dayAndTime) {
        return ZonedTimestamp.fromLocal(
                LocalDateTime.parse(dayAndTime.replace(' ', 'T')), DENVER);
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
