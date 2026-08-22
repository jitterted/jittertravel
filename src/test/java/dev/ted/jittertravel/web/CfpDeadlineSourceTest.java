package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.application.DroppedView;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CfpDeadlineSourceTest {

    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final UUID CONFERENCE_UUID = UUID.fromString("00000000-0000-0000-0000-0000000000cf");
    private static final ZoneId VENUE_ZONE = ZoneId.of("Europe/Amsterdam");

    private final ConferenceProjector projector = mock(ConferenceProjector.class);
    private final CfpDeadlineSource source = new CfpDeadlineSource(projector);

    @Test
    void aFutureCfpDeadlineBecomesOneVeventCarrying72hAnd24hAlarms() {
        Instant deadline = NOW.plus(Duration.ofDays(30));
        givenConferences(conference("J-Fall", deadline));

        ICalEvent event = onlyEvent();

        assertThat(event.uid()).isEqualTo(CONFERENCE_UUID + "-cfp@jittertravel");
        assertThat(event.start()).isEqualTo(deadline);
        assertThat(event.summary()).isEqualTo("CFP closes: J-Fall");
        assertThat(event.alarmTriggers())
                .as("72h to decide and write, 24h as the now-or-never nudge")
                .containsExactly("-PT72H", "-PT24H");
    }

    @Test
    void aConferenceWithNoRecordedCfpProducesNothing() {
        givenConferences(conference("SoCraTes DE", null));

        assertThat(source.events(NOW)).isEmpty();
    }

    @Test
    void aCfpThatHasAlreadyClosedIsExcluded() {
        givenConferences(conference("Closed Already", NOW.minus(Duration.ofHours(1))));

        assertThat(source.events(NOW))
                .as("a reminder that cannot fire is noise in the subscriber's calendar")
                .isEmpty();
    }

    /**
     * The CFP for a conference months away closes long before the conference does — so filtering on
     * {@code TimeView.FUTURE}, which asks whether the <em>conference</em> is over, would be asking
     * the wrong question. This pins that the deadline is the only thing filtered on.
     */
    @Test
    void theDeadlineIsWhatIsFilteredOnNotWhetherTheConferenceHasHappened() {
        given(projector.views(TimeView.ALL, DroppedView.HIDE, NOW))
                .willReturn(List.of(conference("J-Fall", NOW.plus(Duration.ofDays(30)))));

        assertThat(source.events(NOW)).hasSize(1);
    }

    @Test
    void theDescriptionSaysWhereAndWhenTheConferenceItselfIs() {
        givenConferences(conference("J-Fall", NOW.plus(Duration.ofDays(30))));

        assertThat(onlyEvent().description())
                .isEqualTo("Ede, Netherlands — conference runs 2026-11-05 to 2026-11-06");
    }

    private void givenConferences(ConferenceView... views) {
        // The exact filters are the claim: every conference whenever it happens (the deadline is
        // what matters, not the dates), but never one Ted has dropped — an alarm for a decision he
        // already made.
        given(projector.views(TimeView.ALL, DroppedView.HIDE, NOW)).willReturn(List.of(views));
    }

    private ICalEvent onlyEvent() {
        List<ICalEvent> events = source.events(NOW);
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private static ConferenceView conference(String name, Instant cfpClosesOn) {
        return new ConferenceView(
                ConferenceId.of(CONFERENCE_UUID),
                name,
                "ReeHorst",
                new Address("Bennekomseweg 24", "Ede", "", "6717 LM", "Netherlands", "Ede"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 5, 9, 0), VENUE_ZONE),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 6, 17, 0), VENUE_ZONE),
                AttendanceCommitment.WATCHING,
                false,
                SpeakingStatus.NOT_SPEAKING,
                cfpClosesOn == null ? null : new ZonedTimestamp(cfpClosesOn, VENUE_ZONE),
                ConferenceFormat.CALL_FOR_PAPERS);
    }
}
