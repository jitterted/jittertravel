package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.CfpOpened;
import dev.ted.jittertravel.domain.ConferenceHasNoCfp;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.PlanConferenceRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The plan form can carry a CFP, which makes one submit two commands. What this pins is the
 * sequencing and the refusals — that the plan lands first, that the deadline is stamped with the
 * zone the plan already resolved, and that neither impossible combination writes anything at all.
 * <p>
 * See {@code docs/SessionizePrefillPlan.md}, Slice 0, which specified this shape before it existed.
 */
class ConferencePlanningTest {

    private static final Instant NOW = Instant.parse("2026-08-22T18:00:00Z");
    /** Deliberately not the test JVM's UTC, so "which zone was used" has a visible answer. */
    private static final ZoneId VENUE_ZONE = ZoneId.of("Europe/Amsterdam");

    @Test
    void aFormWithNoCfpProducesOnlyThePlanCommand() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor();
        ConferencePlanning planning = planningWith(executor);

        planning.planConference(request(null, null), NOW, UUID.randomUUID());

        assertThat(executor.emitted)
                .singleElement()
                .isInstanceOf(ConferencePlanned.class);
    }

    /**
     * <strong>Plan first, CFP second, and the order is forced</strong> — {@code OpenCfp} folds the
     * stream for a live {@code ConferencePlanned} before it will emit, so a CFP command that ran
     * first would be refused for a conference that does not exist yet.
     */
    @Test
    void aFormCarryingACfpProducesThePlanThenTheCfp() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor();
        ConferencePlanning planning = planningWith(executor);

        planning.planConference(
                request(LocalDateTime.of(2026, 9, 12, 23, 59), "https://sessionize.com/jfall-2027/"),
                NOW, UUID.randomUUID());

        assertThat(executor.emitted).hasSize(2);
        assertThat(executor.emitted.get(0)).isInstanceOf(ConferencePlanned.class);
        assertThat(executor.emitted.get(1)).isInstanceOf(CfpOpened.class);
    }

    /**
     * The claim the whole two-command shape rests on: the deadline takes the zone
     * {@code PlanConferenceHandler} already resolved for the conference, rather than being resolved
     * a second time from the address — a second resolution could only disagree with the first.
     * <p>
     * 23:59 in Amsterdam is 21:59 UTC, which is what makes this visible: a deadline stamped in the
     * server's own zone would read 23:59Z.
     */
    @Test
    void theDeadlineIsStampedWithTheZoneThePlanAlreadyResolved() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor();
        ConferencePlanning planning = planningWith(executor);

        planning.planConference(request(LocalDateTime.of(2026, 9, 12, 23, 59), ""),
                                NOW, UUID.randomUUID());

        CfpOpened cfp = (CfpOpened) executor.emitted.get(1);
        assertThat(cfp.closesOn().zone()).isEqualTo(VENUE_ZONE);
        assertThat(cfp.closesOn().utc()).isEqualTo(Instant.parse("2026-09-12T21:59:00Z"));
    }

    @Test
    void theSubmissionUrlRidesOnTheCfpEvent() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor();
        ConferencePlanning planning = planningWith(executor);

        planning.planConference(
                request(LocalDateTime.of(2026, 9, 12, 23, 59), "https://sessionize.com/jfall-2027/"),
                NOW, UUID.randomUUID());

        assertThat(((CfpOpened) executor.emitted.get(1)).submissionUrl())
                .isEqualTo("https://sessionize.com/jfall-2027/");
    }

    @Test
    void theConferencesOwnPageRidesOnThePlanEvent() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor();
        ConferencePlanning planning = planningWith(executor);

        PlanConferenceRequest request = request(null, null);
        request.setInfoUrl("https://jfall.nl/");

        planning.planConference(request, NOW, UUID.randomUUID());

        assertThat(((ConferencePlanned) executor.emitted.getFirst()).infoUrl())
                .isEqualTo("https://jfall.nl/");
    }

    /**
     * <strong>Refused before anything is written.</strong> The domain refuses this too, but only
     * once the plan command has already landed — which would leave a conference planned and a form
     * to re-render, and re-submitting that form would plan it a second time. Checking here is what
     * makes the failure a plain field error.
     */
    @Test
    void anOpenSpaceConferenceWithADeadlineIsRefusedAndWritesNothing() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor();
        ConferencePlanning planning = planningWith(executor);

        PlanConferenceRequest request = request(LocalDateTime.of(2026, 9, 12, 23, 59), "");
        request.setFormat("OPEN_SPACE");

        assertThatExceptionOfType(ConferenceHasNoCfp.class)
                .isThrownBy(() -> planning.planConference(request, NOW, UUID.randomUUID()));
        assertThat(executor.emitted)
                .as("not even the conference itself is planned")
                .isEmpty();
    }

    /** A submission URL is CFP data too, so it is refused on an open-space conference as well. */
    @Test
    void anOpenSpaceConferenceWithOnlyASubmissionUrlIsRefusedToo() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor();
        ConferencePlanning planning = planningWith(executor);

        PlanConferenceRequest request = request(null, "https://sessionize.com/socrates/");
        request.setFormat("OPEN_SPACE");

        assertThatExceptionOfType(ConferenceHasNoCfp.class)
                .isThrownBy(() -> planning.planConference(request, NOW, UUID.randomUUID()));
        assertThat(executor.emitted).isEmpty();
    }

    /**
     * {@code CfpOpened} is built around its deadline and cannot carry a URL alone, so a URL with no
     * date is refused rather than quietly dropped — a value that vanishes without a word is the
     * failure Ted would not notice.
     */
    @Test
    void aSubmissionUrlWithNoDeadlineIsRefusedRatherThanDropped() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor();
        ConferencePlanning planning = planningWith(executor);

        assertThatExceptionOfType(CfpDeadlineMissing.class)
                .isThrownBy(() -> planning.planConference(
                        request(null, "https://sessionize.com/jfall-2027/"),
                        NOW, UUID.randomUUID()));
        assertThat(executor.emitted).isEmpty();
    }

    /** A deadline on its own is the ordinary case: not every CFP page has been found yet. */
    @Test
    void aDeadlineWithNoSubmissionUrlIsFine() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor();
        ConferencePlanning planning = planningWith(executor);

        planning.planConference(request(LocalDateTime.of(2026, 9, 12, 23, 59), ""),
                                NOW, UUID.randomUUID());

        assertThat(((CfpOpened) executor.emitted.get(1)).submissionUrl()).isEmpty();
    }

    private static ConferencePlanning planningWith(RecordingCommandExecutor executor) {
        // The real resolver, not a stub: the claim under test is that the CFP deadline is stamped
        // with whatever zone the *plan* resolved, and a stub that always answered Amsterdam could
        // satisfy that by accident. Ede, Netherlands resolves there through the country table.
        return new ConferencePlanning(executor,
                                      new LocationZoneResolver(),
                                      new OpenCfp(executor));
    }

    /**
     * The form as the browser posts it, minus the CFP half — which each test supplies, because that
     * half is what these cases are about.
     */
    private static PlanConferenceRequest request(LocalDateTime cfpClosesOn, String cfpSubmissionUrl) {
        PlanConferenceRequest request = new PlanConferenceRequest();
        request.setConferenceId(UUID.randomUUID().toString());
        request.setName("J-Fall");
        request.setStartDate(LocalDateTime.of(2026, 11, 5, 9, 0));
        request.setEndDate(LocalDateTime.of(2026, 11, 5, 18, 0));
        request.setVenueName("Reehorst");
        request.setVenueCity("Ede");
        request.setVenueCountry("Netherlands");
        request.setCfpClosesOn(cfpClosesOn);
        request.setCfpSubmissionUrl(cfpSubmissionUrl);
        return request;
    }

    /**
     * Records what each command emitted, in order, and replays everything recorded so far as the
     * decision stream — which is what lets {@code OpenCfp} see the {@code ConferencePlanned} the
     * previous command just produced, exactly as it would in production.
     */
    private static final class RecordingCommandExecutor extends CommandExecutor {
        private final List<Event> emitted = new ArrayList<>();

        RecordingCommandExecutor() {
            super(null, null);
        }

        @Override
        public Stream<StoredEvent> eventsForDecision() {
            List<StoredEvent> stored = new ArrayList<>();
            for (int i = 0; i < emitted.size(); i++) {
                stored.add(new StoredEvent(i + 1, emitted.get(i).getClass(), UUID.randomUUID(),
                                           NOW, emitted.get(i), UUID.randomUUID()));
            }
            return stored.stream();
        }

        @Override
        public <C extends DecisionContext> void execute(UUID commandId, Object request, C context,
                                                        DomainCommand<C> command) {
            emitted.addAll(command.execute(context).toList());
        }
    }
}
