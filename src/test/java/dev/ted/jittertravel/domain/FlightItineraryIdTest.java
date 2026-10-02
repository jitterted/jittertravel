package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FlightItineraryIdTest {

    @Test
    void ofWrapsTheGivenUuid() {
        UUID uuid = UUID.randomUUID();

        assertThat(FlightItineraryId.of(uuid))
                .isEqualTo(new FlightItineraryId(uuid));
    }

    @Test
    void aMissingUuidIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FlightItineraryId.of(null));
    }
}
