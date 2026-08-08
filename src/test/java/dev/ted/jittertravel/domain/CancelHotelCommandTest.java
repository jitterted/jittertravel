package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

class CancelHotelCommandTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final ZonedTimestamp CHECK_IN =
            ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 15, 0), ZONE);
    // Berlin CEST is +02:00, so check-in is 13:00Z.
    private static final Instant BEFORE_CHECK_IN = Instant.parse("2026-07-01T12:59:59Z");
    private static final Instant AT_CHECK_IN = Instant.parse("2026-07-01T13:00:00Z");

    @Test
    void emitsCancelledEventCarryingTheReason() {
        HotelBookingId bookingId = HotelBookingId.random();

        List<HotelBookingCancelled> events = new CancelHotelCommand(bookingId, "Trip called off")
                .execute(new CancelHotelContext(true, CHECK_IN, BEFORE_CHECK_IN))
                .toList();

        assertThat(events)
                .containsExactly(new HotelBookingCancelled(bookingId, "Trip called off"));
    }

    @Test
    void absentReasonBecomesEmptyStringRatherThanNull() {
        List<HotelBookingCancelled> events = new CancelHotelCommand(HotelBookingId.random(), null)
                .execute(new CancelHotelContext(true, CHECK_IN, BEFORE_CHECK_IN))
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
                        .execute(new CancelHotelContext(false, null, BEFORE_CHECK_IN))
                        .toList());
    }

    @Test
    void cancellingAtTheCheckInInstantIsRejected() {
        // The gate is "strictly before check-in": at check-in you have arrived.
        assertThatExceptionOfType(CannotCancelAfterCheckIn.class)
                .isThrownBy(() -> new CancelHotelCommand(HotelBookingId.random(), "")
                        .execute(new CancelHotelContext(true, CHECK_IN, AT_CHECK_IN))
                        .toList());
    }

    @Test
    void cancellingAfterCheckInIsRejected() {
        assertThatExceptionOfType(CannotCancelAfterCheckIn.class)
                .isThrownBy(() -> new CancelHotelCommand(HotelBookingId.random(), "")
                        .execute(new CancelHotelContext(true, CHECK_IN,
                                Instant.parse("2026-07-02T00:00:00Z")))
                        .toList());
    }

    @Test
    void nullCheckInMeansNoGate() {
        // The import path: no event stream to fold a check-in from, and IMPORT_BYPASS_INSTANT
        // could not trip the gate anyway. A far-future "now" proves the gate is genuinely skipped
        // rather than accidentally passing.
        assertThatNoException()
                .isThrownBy(() -> new CancelHotelCommand(HotelBookingId.random(), "")
                        .execute(new CancelHotelContext(true, null,
                                Instant.parse("2099-01-01T00:00:00Z")))
                        .toList());
    }

    @Test
    void aDeadlineThatHasPassedDoesNotBlockCancelling() {
        // cancelBy is advisory: it never appears in the context, so nothing here can consult it.
        // This test exists to pin that decision, not to exercise a branch.
        List<HotelBookingCancelled> events = new CancelHotelCommand(HotelBookingId.random(), "Late change")
                .execute(new CancelHotelContext(true, CHECK_IN, BEFORE_CHECK_IN))
                .toList();

        assertThat(events).hasSize(1);
    }
}
