package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.CfpOpened;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.DifferentCityConflictCleared;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.InvitedToSpeak;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.TalkSubmitted;
import dev.ted.jittertravel.domain.TalkWithdrawn;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lifecycle guard: every conference event reaches every read model that folds the conference state
 * machine — the dashboard, the owner calendar, the public calendar, the itinerary and the schedule.
 * <p>
 * <strong>Why this exists.</strong> Each of the five writes its own nine-arm switch over the same
 * events and hands them to {@link ConferenceProgress}. Until 2026-09-09 the itinerary read three of
 * the nine, so family saw less than a stranger and kept a conference Ted had been rejected from, and
 * nothing failed. The sibling {@code *CancellationPropagationTest}s guard hotels, trains, ground
 * transfers and private events the same way; conferences had none.
 * <p>
 * <strong>How it catches a forgotten arm.</strong> Each scenario ends in the event it is named for,
 * arranged so that the outcome <em>depends</em> on that event: drop the arm from any projector and
 * that projector reports the outcome of the scenario minus its last event, which differs from the
 * expected one. A scenario whose last event changed nothing would prove nothing, which is why, for
 * example, {@link TalkSubmitted} follows a speaking confirmation (submitting is what turns speaking
 * <em>off</em>) rather than a bare plan.
 * <p>
 * <strong>The schedule sees less, honestly.</strong> {@link ScheduleGapProjector} holds progress
 * only to answer {@link ConferenceProgress#dropped()}, so it can observe only the three events that
 * change whether a conference is there at all. A forgotten arm for the other six changes nothing it
 * reports, and this test does not pretend otherwise.
 * <p>
 * <strong>A new conference event fails {@link #everyConferenceEventHasAScenarioOrADeclaredReason}</strong>
 * until someone writes its scenario here or says why it is not part of the lifecycle — the same
 * forcing function {@code CalendarDayMenuTest} uses for {@code EntryKind}.
 */
class ConferenceLifecyclePropagationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final Address BERLIN =
            new Address("Alexanderplatz 1", "Berlin", "", "10178", "Germany", "Berlin");
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 7, 1);
    private static final Instant DECIDED_ON = Instant.parse("2026-05-01T00:00:00Z");

    /**
     * Conference-scoped events that are deliberately not part of the commitment/speaking lifecycle,
     * each with its reason. Adding to this list is a decision, not a way to make the guard pass.
     */
    private static final Set<Class<? extends Event>> NOT_LIFECYCLE_EVENTS = Set.of(
            // A property of the conference, not a move: it opens a window and commits nothing.
            CfpOpened.class,
            // An acknowledgement of a schedule conflict between a gathering and a conference; it
            // moves neither axis.
            DifferentCityConflictCleared.class);

    private final AtomicLong sequence = new AtomicLong();

    // --- The outcomes a scenario can end in ---

    private sealed interface Outcome {
    }

    /** Still on every surface, at this commitment, with or without the speaking badge. */
    private record Showing(AttendanceCommitment commitment, boolean speaking) implements Outcome {
    }

    /** Declined, or rejected where acceptance was the way in: gone from every calendar, kept on the dashboard. */
    private record Dropped() implements Outcome {
    }

    /** The organizers called it off: gone from everything, the dashboard included. */
    private record Gone() implements Outcome {
    }

    private record Scenario(Class<? extends Event> endsWith,
                            ConferenceFormat format,
                            Function<ConferenceId, List<Event>> afterPlanning,
                            Outcome expected) {
        @Override
        public String toString() {
            return endsWith.getSimpleName() + " → " + expected;
        }
    }

    static Stream<Scenario> scenarios() {
        return Stream.of(
                // Watching → going, and the basis names speaking with no talk in the stream.
                new Scenario(ConferenceAttendanceConfirmed.class, ConferenceFormat.CALL_FOR_PAPERS,
                        id -> List.of(confirmed(id, AttendanceBasis.SPEAKING_INVITED)),
                        new Showing(AttendanceCommitment.GOING, true)),
                new Scenario(ConferenceAttendanceDeclined.class, ConferenceFormat.CALL_FOR_PAPERS,
                        id -> List.of(new ConferenceAttendanceDeclined(id, "Too far", DECIDED_ON)),
                        new Dropped()),
                new Scenario(ConferenceCancelled.class, ConferenceFormat.CALL_FOR_PAPERS,
                        id -> List.of(new ConferenceCancelled(id, "Organizers called it off")),
                        new Gone()),
                // The auto-commit: going and speaking with no confirmation event.
                new Scenario(TalkAccepted.class, ConferenceFormat.CALL_FOR_PAPERS,
                        id -> List.of(new TalkAccepted(id, DECIDED_ON)),
                        new Showing(AttendanceCommitment.GOING, true)),
                // Submitting after a speaking confirmation is what turns speaking OFF: the stream
                // has spoken and says "waiting to hear". Without the arm the badge stays on.
                new Scenario(TalkSubmitted.class, ConferenceFormat.CALL_FOR_PAPERS,
                        id -> List.of(confirmed(id, AttendanceBasis.SPEAKING_INVITED),
                                      new TalkSubmitted(id, DECIDED_ON)),
                        new Showing(AttendanceCommitment.GOING, false)),
                // The auto-drop, which only happens where acceptance was the way in.
                new Scenario(TalkRejected.class, ConferenceFormat.ACCEPTANCE_REQUIRED,
                        id -> List.of(new TalkSubmitted(id, DECIDED_ON),
                                      new TalkRejected(id, DECIDED_ON)),
                        new Dropped()),
                // Pulling an accepted talk keeps the commitment and loses the badge.
                new Scenario(TalkWithdrawn.class, ConferenceFormat.CALL_FOR_PAPERS,
                        id -> List.of(new TalkAccepted(id, DECIDED_ON),
                                      new TalkWithdrawn(id, DECIDED_ON)),
                        new Showing(AttendanceCommitment.GOING, false)),
                // An invitation after a submission, on a speaking confirmation, turns the badge
                // back ON — without the arm the submission's "no talk yet" still stands.
                new Scenario(InvitedToSpeak.class, ConferenceFormat.CALL_FOR_PAPERS,
                        id -> List.of(confirmed(id, AttendanceBasis.SPEAKING_INVITED),
                                      new TalkSubmitted(id, DECIDED_ON),
                                      new InvitedToSpeak(id, DECIDED_ON)),
                        new Showing(AttendanceCommitment.GOING, true)));
    }

    @Test
    void everyConferenceEventHasAScenarioOrADeclaredReason() throws IOException {
        Set<Class<? extends Event>> accountedFor = scenarios()
                .map(Scenario::endsWith)
                .collect(Collectors.toCollection(HashSet::new));
        accountedFor.add(ConferencePlanned.class);
        accountedFor.addAll(NOT_LIFECYCLE_EVENTS);

        assertThat(conferenceScopedEvents())
                .as("every domain event carrying a ConferenceId needs a scenario above, "
                    + "or an entry in NOT_LIFECYCLE_EVENTS saying why it moves nothing")
                .containsExactlyInAnyOrderElementsOf(accountedFor);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void conferenceDashboard(Scenario scenario) {
        ConferenceProjector projector = new ConferenceProjector();
        ConferenceId id = ConferenceId.random();

        projector.handle(play(id, scenario));

        switch (scenario.expected()) {
            case Showing(AttendanceCommitment commitment, boolean speaking) ->
                    assertThat(projector.findById(id))
                            .get()
                            .extracting(ConferenceView::commitment, ConferenceView::speaking)
                            .containsExactly(commitment, speaking);
            // The dashboard keeps a dropped row as a record, behind ?dropped=show.
            case Dropped() -> assertThat(projector.findById(id))
                    .get()
                    .extracting(ConferenceView::commitment)
                    .isEqualTo(AttendanceCommitment.NOT_GOING);
            case Gone() -> assertThat(projector.findById(id))
                    .isEmpty();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void ownerCalendar(Scenario scenario) {
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();

        projector.handle(play(ConferenceId.random(), scenario));

        List<EntryDetails> details = projector.entries().stream()
                .map(CalendarEntry::details)
                .toList();
        switch (scenario.expected()) {
            case Showing(AttendanceCommitment commitment, boolean speaking) ->
                    assertThat(details)
                            .singleElement()
                            .isInstanceOfSatisfying(EntryDetails.Conference.class, conference ->
                                    assertThat(conference)
                                            .extracting(EntryDetails.Conference::commitment,
                                                        EntryDetails.Conference::speaking)
                                            .containsExactly(commitment, speaking));
            case Dropped(), Gone() -> assertThat(details)
                    .isEmpty();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void publicCalendar(Scenario scenario) {
        PublicCalendarProjector projector = new PublicCalendarProjector();

        projector.handle(play(ConferenceId.random(), scenario));

        List<EntryDetails> details = projector.entries().stream()
                .map(CalendarEntry::details)
                .toList();
        switch (scenario.expected()) {
            case Showing(AttendanceCommitment commitment, boolean speaking) ->
                    assertThat(details)
                            .singleElement()
                            .isInstanceOfSatisfying(EntryDetails.PublicConference.class, conference ->
                                    assertThat(conference)
                                            .extracting(EntryDetails.PublicConference::commitment,
                                                        EntryDetails.PublicConference::speaking)
                                            .containsExactly(commitment, speaking));
            case Dropped(), Gone() -> assertThat(details)
                    .isEmpty();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void itinerary(Scenario scenario) {
        ItineraryProjector projector = new ItineraryProjector();

        projector.handle(play(ConferenceId.random(), scenario));

        List<ItineraryEntry> firstDay = projector.entriesForDate(FIRST_DAY);
        switch (scenario.expected()) {
            case Showing(AttendanceCommitment commitment, boolean speaking) ->
                    assertThat(firstDay)
                            .singleElement()
                            .isInstanceOfSatisfying(ConferenceItineraryEntry.class, entry ->
                                    assertThat(entry)
                                            .extracting(ConferenceItineraryEntry::commitment,
                                                        ConferenceItineraryEntry::speaking)
                                            .containsExactly(commitment, speaking));
            case Dropped(), Gone() -> assertThat(firstDay)
                    .isEmpty();
        }
    }

    /**
     * Presence only, observed as the missing hotel an unaccompanied Berlin conference produces — see
     * the class comment on why the schedule cannot see the other six events.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void schedule(Scenario scenario) {
        ScheduleGapProjector projector = new ScheduleGapProjector(new StaticAirportCityResolver());

        projector.handle(play(ConferenceId.random(), scenario));

        switch (scenario.expected()) {
            case Showing _ -> assertThat(projector.problems())
                    .as("a conference still on the schedule needs its nights covered")
                    .isNotEmpty();
            case Dropped(), Gone() -> assertThat(projector.problems())
                    .as("a conference that left the schedule needs nothing")
                    .isEmpty();
        }
    }

    private Stream<StoredEvent> play(ConferenceId id, Scenario scenario) {
        List<Event> events = new ArrayList<>();
        events.add(new ConferencePlanned(id, "BerlinConf",
                zt(FIRST_DAY.atTime(9, 0)), zt(FIRST_DAY.plusDays(2).atTime(17, 0)),
                "Congress Center", BERLIN, scenario.format()));
        events.addAll(scenario.afterPlanning().apply(id));
        return events.stream().map(this::stored);
    }

    private static ConferenceAttendanceConfirmed confirmed(ConferenceId id, AttendanceBasis basis) {
        return new ConferenceAttendanceConfirmed(id, basis, DECIDED_ON);
    }

    /**
     * Every record in the domain package that implements {@link Event} and carries a
     * {@link ConferenceId} — found by listing the sources, so a new one cannot be missed by a
     * hand-kept list.
     */
    private static Set<Class<?>> conferenceScopedEvents() throws IOException {
        Path domain = Path.of("src/main/java/dev/ted/jittertravel/domain");
        try (Stream<Path> files = Files.list(domain)) {
            return files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .map(name -> loadDomainClass(name.substring(0, name.length() - ".java".length())))
                    .filter(type -> type.isRecord() && Event.class.isAssignableFrom(type))
                    .filter(type -> Arrays.stream(type.getRecordComponents())
                            .map(RecordComponent::getType)
                            .anyMatch(ConferenceId.class::equals))
                    .collect(Collectors.toSet());
        }
    }

    private static Class<?> loadDomainClass(String simpleName) {
        try {
            return Class.forName("dev.ted.jittertravel.domain." + simpleName);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("A domain source with no compiled class: " + simpleName, e);
        }
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, ZONE);
    }

    private StoredEvent stored(Event event) {
        return new StoredEvent(sequence.incrementAndGet(), event.getClass(), UUID.randomUUID(),
                DECIDED_ON, event, UUID.randomUUID());
    }
}
