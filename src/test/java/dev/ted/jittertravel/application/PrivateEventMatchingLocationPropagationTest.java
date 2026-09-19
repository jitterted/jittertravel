package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.PrivateEventCancelled;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventMatchingLocationChanged;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The only guard on {@code ScheduleGapProjector}'s {@link PrivateEventMatchingLocationChanged}
 * branch, and it has to be, because the usual net does not reach here:
 * {@code LocatedEventsReachScheduleProblemsTest} walks events carrying an {@link Address}, and this
 * one carries a bare {@code String} — exactly as {@code TrainCancelled} carries no
 * {@code TrainStationAddress} and needed {@code TrainCancellationPropagationTest} for the same
 * reason. Delete the branch and nothing else in the tree fails.
 * <p>
 * The scenario is the one the feature was built for (Ted, 2026-09-18): a conference in Lone Tree,
 * CO, and a dinner with friends in Centennial, CO, under four miles away. The schedule reads the
 * drive out to dinner as a journey, and it is not one worth recording. <strong>One journey, not a
 * symmetric pair</strong> — see {@link #aDinnerInTheNextTownRaisesMissingTravelOutToIt} for why the
 * return leg is never reported. The plan's first draft assumed two; this test corrected it, and the
 * wrong version is recorded here because it is the intuitive one.
 * <p>
 * <strong>Three read models are built from that city, not one</strong>, and all three are covered
 * here deliberately: the missing-travel walk, the missing-hotel sweep, and {@code awayDays()}. The
 * hotel half is the larger effect on {@code /schedule-problems} — a dinner matched in the next town
 * splits one run of uncovered nights into two rows demanding beds in two cities. Asserting only on
 * {@code MissingTravel} would leave that unguarded, which is what this file's first version did.
 * <p>
 * {@code awayDays()} is the third, and it has the <strong>widest audience</strong>: the turquoise
 * band under a day label renders for every viewer, anonymous included. It is not a redaction
 * concern — the band says <em>when</em> and never <em>where</em> or <em>why</em>, so it discloses
 * nothing this event could leak — but it does mean an override silently adds or removes stripes on
 * the one page strangers can see, and nothing else in the tree would notice.
 */
class PrivateEventMatchingLocationPropagationTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private static final LocalDate CONFERENCE_START = LocalDate.of(2026, 10, 1);
    private static final LocalDate DINNER_DAY = LocalDate.of(2026, 10, 2);
    private static final LocalDate CONFERENCE_END = LocalDate.of(2026, 10, 3);

    private static final Address CONFERENCE_VENUE =
            new Address("10035 Park Meadows Dr", "Lone Tree", "CO", "80124", "US", "Lone Tree");
    private static final Address DINNER_VENUE =
            new Address("7 Dry Creek Rd", "Centennial", "CO", "80112", "US", "Centennial");

    private final PrivateEventId privateEventId = PrivateEventId.random();
    private final AtomicLong sequence = new AtomicLong();

    /**
     * The state of the world before the fix, and the reason the fix exists. Pinned so that if the
     * detector ever stops raising this, the test below stops proving anything.
     * <p>
     * <strong>One gap, not two</strong>, and it is worth knowing why: an occupancy contributes a
     * rank-2 {@code REQUIRE} at its start and a rank-0 {@code LEAVE} at its end
     * ({@code ScheduleTimeline:461-464}). A {@code LEAVE} asserts nothing about where Ted must be,
     * so nothing asks for Lone Tree again after the dinner and the return journey is never
     * reported. The plan's first draft assumed a symmetric pair; this test is what corrected it.
     * It is also why one fix link on the outbound gap would be enough, if the report could carry
     * one at all — see {@code ProblemCauseLinkingPlan.md}.
     */
    @Test
    void aDinnerInTheNextTownRaisesMissingTravelOutToIt() {
        ScheduleGapProjector projector = projectorWith(conference(), dinner());

        assertThat(projector.problems())
                .filteredOn(ScheduleProblem.MissingTravel.class::isInstance)
                .as("Lone Tree -> Centennial, the evening of the dinner")
                .hasSize(1)
                .extracting(problem -> ((ScheduleProblem.MissingTravel) problem).toCity())
                .containsExactly("Centennial");
    }

    @Test
    void matchingTheDinnerToTheConferenceCityRemovesTheGap() {
        ScheduleGapProjector projector =
                projectorWith(conference(), dinner(), matchedAs("Lone Tree"));

        assertThat(projector.problems())
                .as("a four-mile taxi is not a journey the schedule should demand")
                .filteredOn(ScheduleProblem.MissingTravel.class::isInstance)
                .isEmpty();
    }

    @Test
    void theOverrideMovesTheOccupancyRatherThanRemovingIt() {
        // It must not become a cancel by the back door: the evening is still on the schedule, still
        // occupying those hours, and still able to clash with something else.
        ScheduleGapProjector projector =
                projectorWith(conference(), dinner(), matchedAs("Lone Tree"));

        assertThat(projector.context())
                .as("the dinner is still on the schedule, merely matched elsewhere")
                .contains(new ScheduleContext.PrivateEvent(
                        "Dinner with the Smiths", "Lone Tree", DINNER_DAY, DINNER_DAY));
    }

    /**
     * The other half of what the override moves, and the half that is easier to forget:
     * {@code missingHotels()} splits a run of uncovered nights <em>where the city changes</em>,
     * because the row has to say where to book. A dinner matched in the next town is a city change,
     * so one conference's nights become two demands — the second of them for a town Ted is not
     * sleeping in, and carrying no conference name because a private event does not lend one.
     */
    @Test
    void aDinnerInTheNextTownSplitsTheConferencesHotelRunInTwo() {
        ScheduleGapProjector projector = projectorWith(conference(), dinner());

        assertThat(projector.problems())
                .filteredOn(ScheduleProblem.MissingHotel.class::isInstance)
                .as("the dinner's city interrupts the run of nights at the conference")
                .containsExactly(
                        new ScheduleProblem.MissingHotel(
                                "Lone Tree", CONFERENCE_START, DINNER_DAY, "Rocky Mountain Java"),
                        new ScheduleProblem.MissingHotel(
                                "Centennial", DINNER_DAY, CONFERENCE_END, ""));
    }

    @Test
    void matchingTheDinnerToTheConferenceCityMergesTheHotelRunBackIntoOne() {
        // The larger of the two effects, and the one no other test in the tree would notice: the
        // spurious "book a bed in Centennial" row goes away and the conference's own run is whole
        // again, naming the conference it belongs to.
        ScheduleGapProjector projector =
                projectorWith(conference(), dinner(), matchedAs("Lone Tree"));

        assertThat(projector.problems())
                .filteredOn(ScheduleProblem.MissingHotel.class::isInstance)
                .as("one run of nights, in the city Ted is actually sleeping in")
                .containsExactly(new ScheduleProblem.MissingHotel(
                        "Lone Tree", CONFERENCE_START, CONFERENCE_END, "Rocky Mountain Java"));
    }

    /**
     * The override reaches the public calendar's away band, and it clears <strong>two</strong> days
     * rather than the one the dinner occupies: {@code awayDays()} earns a trailing day whenever the
     * schedule still accounts for it, and the conference's closing afternoon does. So a single
     * correction removes a two-day stripe from the one page anonymous visitors can see.
     */
    @Test
    void matchingTheDinnerIntoAHomeCityClearsTheAwayBandItRaised() {
        ScheduleGapProjector before = projectorAtHomeIn("Lone Tree", conference(), dinner());
        assertThat(before.awayDays())
                .as("the dinner in the next town is what puts Ted out of town at all")
                .containsExactlyInAnyOrder(DINNER_DAY, CONFERENCE_END);

        ScheduleGapProjector after =
                projectorAtHomeIn("Lone Tree", conference(), dinner(), matchedAs("Lone Tree"));

        assertThat(after.awayDays())
                .as("re-matched into the city he is already in, no day is away any more")
                .isEmpty();
    }

    /**
     * The ordinary case, and the reason the band is not a redaction concern: between two towns
     * that are both away, the override moves nothing a viewer can see. The band says <em>when</em>,
     * never <em>where</em>.
     */
    @Test
    void anOverrideBetweenTwoCitiesAwayFromHomeLeavesTheBandAlone() {
        ScheduleGapProjector unmatched = projectorAtHomeIn("Denver", conference(), dinner());

        ScheduleGapProjector rematched =
                projectorAtHomeIn("Denver", conference(), dinner(), matchedAs("Boulder"));

        assertThat(rematched.awayDays())
                .as("Centennial and Boulder are both away, so the stripe is the same either way")
                .isEqualTo(unmatched.awayDays())
                .containsExactlyInAnyOrder(CONFERENCE_START, DINNER_DAY, CONFERENCE_END);
    }

    @Test
    void matchingToSomeThirdCityLeavesTheGapInPlaceButMovesIt() {
        // Proves the branch reads the event's value rather than merely noticing the event: an
        // override to a city that is not the conference's changes which gap is reported, not
        // whether one is. Neutralise the branch and this still fails, because the gap would still
        // say Centennial.
        ScheduleGapProjector projector =
                projectorWith(conference(), dinner(), matchedAs("Boulder"));

        assertThat(projector.problems())
                .filteredOn(ScheduleProblem.MissingTravel.class::isInstance)
                .as("still a journey, now out to Boulder")
                .hasSize(1)
                .extracting(problem -> ((ScheduleProblem.MissingTravel) problem).toCity())
                .as("Centennial has been replaced by the overridden city")
                .containsExactly("Boulder");
    }

    @Test
    void theLastOverrideWins() {
        ScheduleGapProjector projector = projectorWith(conference(), dinner(),
                matchedAs("Boulder"), matchedAs("Lone Tree"));

        assertThat(projector.problems())
                .as("the most recent correction is the one in force")
                .filteredOn(ScheduleProblem.MissingTravel.class::isInstance)
                .isEmpty();
    }

    @Test
    void anOverrideAfterCancellationDoesNotBringTheEveningBack() {
        // computeIfPresent, not put. A put would resurrect the occupancy from one field, and the
        // resurrected evening would carry no times at all.
        ScheduleGapProjector projector = projectorWith(conference(), dinner(),
                new PrivateEventCancelled(privateEventId, "Entered by mistake"),
                matchedAs("Lone Tree"));

        assertThat(projector.context())
                .as("a cancelled evening stays cancelled")
                .noneMatch(ScheduleContext.PrivateEvent.class::isInstance);
    }

    @Test
    void anOverrideForAnEveningThatWasNeverPlannedChangesNothing() {
        ScheduleGapProjector projector = projectorWith(conference(),
                new PrivateEventMatchingLocationChanged(PrivateEventId.random(), "Lone Tree"));

        assertThat(projector.context())
                .as("an override alone is not an occupancy")
                .noneMatch(ScheduleContext.PrivateEvent.class::isInstance);
    }

    private ScheduleGapProjector projectorWith(Event... events) {
        ScheduleGapProjector projector = new ScheduleGapProjector(new StaticAirportCityResolver());
        projector.handle(Stream.of(events).map(this::stored));
        return projector;
    }

    /**
     * The same projector with a home configured, which the cases above deliberately do without:
     * {@code awayDays()} returns nothing at all when none is, so a home is what makes the band
     * observable.
     */
    private ScheduleGapProjector projectorAtHomeIn(String homeCity, Event... events) {
        ScheduleGapProjector projector = new ScheduleGapProjector(
                new StaticAirportCityResolver(), new HomeCities(List.of(homeCity)));
        projector.handle(Stream.of(events).map(this::stored));
        return projector;
    }

    private static ConferencePlanned conference() {
        return new ConferencePlanned(ConferenceId.random(), "Rocky Mountain Java",
                at(CONFERENCE_START, 9, 0), at(CONFERENCE_END, 17, 0),
                "Lone Tree Conference Center", CONFERENCE_VENUE,
                ConferenceFormat.OPEN_SPACE, "");
    }

    private PrivateEventPlanned dinner() {
        return new PrivateEventPlanned(privateEventId, "Dinner with the Smiths", "Chez Moi",
                DINNER_VENUE, at(DINNER_DAY, 19, 0), at(DINNER_DAY, 22, 0));
    }

    private PrivateEventMatchingLocationChanged matchedAs(String city) {
        return new PrivateEventMatchingLocationChanged(privateEventId, city);
    }

    private static ZonedTimestamp at(LocalDate date, int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(date, LocalTime.of(hour, minute)), DENVER);
    }

    private StoredEvent stored(Event event) {
        return new StoredEvent(sequence.incrementAndGet(), event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
