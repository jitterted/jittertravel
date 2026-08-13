package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CancelHotelCommandTest {

    @Test
    void emitsCancelledEventCarryingTheReason() {
        HotelBookingId bookingId = HotelBookingId.random();

        List<HotelBookingCancelled> events = new CancelHotelCommand(bookingId, "Trip called off")
                .execute(new CancelHotelContext(true))
                .toList();

        assertThat(events)
                .containsExactly(new HotelBookingCancelled(bookingId, "Trip called off"));
    }

    @Test
    void absentReasonBecomesEmptyStringRatherThanNull() {
        List<HotelBookingCancelled> events = new CancelHotelCommand(HotelBookingId.random(), null)
                .execute(new CancelHotelContext(true))
                .toList();

        assertThat(events)
                .singleElement()
                .extracting(HotelBookingCancelled::reason)
                .isEqualTo("");
    }

    @Test
    void unknownBookingIsRejected() {
        assertThatExceptionOfType(HotelBookingNotFound.class)
                .isThrownBy(() -> new CancelHotelCommand(HotelBookingId.random(), "")
                        .execute(new CancelHotelContext(false))
                        .toList());
    }

    @Test
    void existingBookingIsCancellableWithNoTimeGateAtAll() {
        // Deliberately ungated on both check-in and the advisory cancelBy deadline: cancelling
        // happens with the hotel in the real world, and entering it here is a manual step that
        // routinely lags. This test pins the decision rather than a branch — the real guarantee is
        // structural, since CancelHotelContext carries no clock and no timestamps to consult.
        List<HotelBookingCancelled> events = new CancelHotelCommand(HotelBookingId.random(),
                "Already cancelled with the hotel last week")
                .execute(new CancelHotelContext(true))
                .toList();

        assertThat(events).hasSize(1);
    }
}
