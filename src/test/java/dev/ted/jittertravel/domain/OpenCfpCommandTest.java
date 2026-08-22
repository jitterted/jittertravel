package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class OpenCfpCommandTest {

    private static final ZoneId VENUE_ZONE = ZoneId.of("Europe/Amsterdam");
    private static final ZonedTimestamp CLOSES_ON =
            ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 12, 23, 59), VENUE_ZONE);

    @Test
    void emitsCfpOpenedCarryingTheClosingDeadline() {
        ConferenceId conferenceId = ConferenceId.random();

        List<CfpOpened> events = new OpenCfpCommand(conferenceId, CLOSES_ON)
                .execute(new OpenCfpContext(true))
                .toList();

        assertThat(events).containsExactly(new CfpOpened(conferenceId, CLOSES_ON));
    }

    @Test
    void unknownConferenceIsRejected() {
        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> new OpenCfpCommand(ConferenceId.random(), CLOSES_ON)
                        .execute(new OpenCfpContext(false))
                        .toList());
    }

    /**
     * A CFP whose deadline has already passed is still worth recording: "this closed and I did not
     * submit" is a state the radar shows, and backfilling an old conference is a legitimate use. The
     * guarantee is structural — {@link OpenCfpContext} carries no clock to consult.
     */
    @Test
    void aDeadlineInThePastIsRecordableWithNoTimeGateAtAll() {
        ZonedTimestamp longGone =
                ZonedTimestamp.fromLocal(LocalDateTime.of(2020, 1, 1, 12, 0), VENUE_ZONE);

        List<CfpOpened> events = new OpenCfpCommand(ConferenceId.random(), longGone)
                .execute(new OpenCfpContext(true))
                .toList();

        assertThat(events).hasSize(1);
    }

    /**
     * The deadline is the whole point of the event, and unlike a free-text reason it has no sensible
     * empty value — so an absent one fails loud here rather than reaching a projector as a null and
     * silently producing a conference with no reminder.
     */
    @Test
    void aMissingDeadlineFailsLoudRatherThanBecomingNull() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new CfpOpened(ConferenceId.random(), null))
                .withMessageContaining("closesOn must not be null");
    }
}
