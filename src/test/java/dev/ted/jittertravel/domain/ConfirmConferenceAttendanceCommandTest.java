package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ConfirmConferenceAttendanceCommandTest {

    private static final Instant CONFIRMED_ON = Instant.parse("2026-08-19T16:45:00Z");

    @ParameterizedTest
    @EnumSource(AttendanceBasis.class)
    void emitsConfirmedEventCarryingTheBasisAndTimestamp(AttendanceBasis basis) {
        ConferenceId conferenceId = ConferenceId.random();

        List<ConferenceAttendanceConfirmed> events =
                new ConfirmConferenceAttendanceCommand(conferenceId, basis, CONFIRMED_ON)
                        .execute(new ConfirmConferenceAttendanceContext(true))
                        .toList();

        assertThat(events)
                .containsExactly(new ConferenceAttendanceConfirmed(conferenceId, basis, CONFIRMED_ON));
    }

    @Test
    void unknownConferenceIsRejected() {
        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> new ConfirmConferenceAttendanceCommand(
                        ConferenceId.random(), AttendanceBasis.TICKET_PURCHASED, CONFIRMED_ON)
                        .execute(new ConfirmConferenceAttendanceContext(false))
                        .toList());
    }

    // Two claims deliberately have no test *here*, because at this level they have no arrangement
    // that could fail: confirming is ungated on the conference dates (the context carries no clock
    // and no timestamps to consult — a structural guarantee), and confirming a second time with a
    // different basis is allowed (the command never consults prior confirmations). Both are really
    // claims about the fold that decides `conferenceExists`, and both are exercised where a prior
    // event can actually be arranged: ConfirmConferenceAttendanceTest.

    @Test
    void aConfirmationWithoutABasisFailsLoud() {
        // Unlike a free-text reason there is no sensible empty value, so an absent basis is a bug
        // in the caller rather than something a projector should have to defend against.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConferenceAttendanceConfirmed(
                        ConferenceId.random(), null, CONFIRMED_ON))
                .withMessageContaining("basis must not be null");
    }
}
