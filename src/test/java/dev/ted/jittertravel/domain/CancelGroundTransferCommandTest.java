package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CancelGroundTransferCommandTest {

    @Test
    void emitsCancelledEventForTheTransferBeingCancelled() {
        GroundTransferId transferId = GroundTransferId.random();

        List<GroundTransferCancelled> events = new CancelGroundTransferCommand(transferId)
                .execute(new CancelGroundTransferContext(true))
                .toList();

        assertThat(events)
                .containsExactly(new GroundTransferCancelled(transferId));
    }

    @Test
    void unknownTransferIsRejected() {
        assertThatExceptionOfType(GroundTransferNotFound.class)
                .isThrownBy(() -> new CancelGroundTransferCommand(GroundTransferId.random())
                        .execute(new CancelGroundTransferContext(false))
                        .toList());
    }

    @Test
    void aTransferThatHasAlreadyHappenedIsStillCancellable() {
        // No time gate, for the same reason planning one has no future-date rule (D6): a transfer
        // is entered on a trip already under way, and the entry most worth removing is precisely a
        // past hop that never happened — left in place it goes on masking a real travel gap. This
        // test pins the decision; the guarantee is structural, since the context carries no clock.
        List<GroundTransferCancelled> events = new CancelGroundTransferCommand(GroundTransferId.random())
                .execute(new CancelGroundTransferContext(true))
                .toList();

        assertThat(events).hasSize(1);
    }
}
