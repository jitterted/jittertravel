package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ChangeConferenceDatesCommandTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalDateTime MOVED_START = LocalDateTime.of(2027, 3, 29, 9, 0);
    private static final LocalDateTime MOVED_END = LocalDateTime.of(2027, 3, 31, 17, 0);

    private final ConferenceId conferenceId = ConferenceId.random();

    @Test
    void emitsTheMovedDatesStampedInTheConferencesOwnZone() {
        List<ConferenceDatesChanged> events =
                new ChangeConferenceDatesCommand(conferenceId, MOVED_START, MOVED_END)
                        .execute(new ChangeConferenceDatesContext(true, NEW_YORK))
                        .toList();

        assertThat(events)
                .containsExactly(new ConferenceDatesChanged(conferenceId,
                        ZonedTimestamp.fromLocal(MOVED_START, NEW_YORK),
                        ZonedTimestamp.fromLocal(MOVED_END, NEW_YORK)));
    }

    @Test
    void aConferenceThatIsNotLiveIsRejected() {
        // Never planned, cancelled by its organizers, or declined: a move for any of them would sit
        // in the log applying to nothing.
        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> new ChangeConferenceDatesCommand(conferenceId, MOVED_START, MOVED_END)
                        .execute(new ChangeConferenceDatesContext(false, null))
                        .toList());
    }

    @Test
    void anEndBeforeTheStartIsRejected() {
        assertThatExceptionOfType(InvalidDateRange.class)
                .isThrownBy(() -> new ChangeConferenceDatesCommand(conferenceId, MOVED_END, MOVED_START)
                        .execute(new ChangeConferenceDatesContext(true, NEW_YORK))
                        .toList())
                .withMessage("End date must be on or after start date");
    }

    @Test
    void anEndEqualToTheStartIsAccepted() {
        List<ConferenceDatesChanged> events =
                new ChangeConferenceDatesCommand(conferenceId, MOVED_START, MOVED_START)
                        .execute(new ChangeConferenceDatesContext(true, NEW_YORK))
                        .toList();

        assertThat(events)
                .as("the same boundary PlanConferenceCommand draws: on or after")
                .hasSize(1);
    }

    @Test
    void existenceIsCheckedBeforeTheRange() {
        // A conference that is gone must report "gone", not "fix the end date", because fixing it
        // would not help.
        assertThatExceptionOfType(ConferenceNotFound.class)
                .isThrownBy(() -> new ChangeConferenceDatesCommand(conferenceId, MOVED_END, MOVED_START)
                        .execute(new ChangeConferenceDatesContext(false, null))
                        .toList());
    }

    // No "a past conference can still be moved" case, for the reason
    // ChangePrivateEventMatchingLocationCommandTest gives: neither the command nor its context
    // carries a clock, so the case could not be told apart from the first one above. Give the
    // context a clock and this file needs it back.
}
