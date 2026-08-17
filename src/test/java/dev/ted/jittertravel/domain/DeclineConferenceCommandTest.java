package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class DeclineConferenceCommandTest {

    private static final Instant DECLINED_ON = Instant.parse("2026-08-16T18:30:00Z");

    @Test
    void emitsDeclinedEventCarryingTheReasonAndTimestamp() {
        ConferenceId conferenceId = ConferenceId.random();

        List<ConferenceAttendanceDeclined> events =
                new DeclineConferenceCommand(conferenceId, "Schedule clash", DECLINED_ON)
                        .execute(new DeclineConferenceContext(true))
                        .toList();

        assertThat(events)
                .containsExactly(new ConferenceAttendanceDeclined(conferenceId, "Schedule clash", DECLINED_ON));
    }

    @Test
    void absentReasonBecomesEmptyStringRatherThanNull() {
        List<ConferenceAttendanceDeclined> events =
                new DeclineConferenceCommand(ConferenceId.random(), null, DECLINED_ON)
                        .execute(new DeclineConferenceContext(true))
                        .toList();

        assertThat(events)
                .singleElement()
                .extracting(ConferenceAttendanceDeclined::reason)
                .isEqualTo("");
    }

    @Test
    void unknownConferenceIsRejected() {
        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> new DeclineConferenceCommand(ConferenceId.random(), "", DECLINED_ON)
                        .execute(new DeclineConferenceContext(false))
                        .toList());
    }

    @Test
    void existingConferenceIsDeclinableWithNoTimeGateAtAll() {
        // Like cancelling a hotel, declining is deliberately ungated on the conference dates: Ted may
        // record "I'm not going" at any point, even during or after. The guarantee is structural —
        // DeclineConferenceContext carries no clock and no timestamps to consult.
        List<ConferenceAttendanceDeclined> events =
                new DeclineConferenceCommand(ConferenceId.random(),
                        "Decided weeks ago, only recording it now", DECLINED_ON)
                        .execute(new DeclineConferenceContext(true))
                        .toList();

        assertThat(events).hasSize(1);
    }
}
