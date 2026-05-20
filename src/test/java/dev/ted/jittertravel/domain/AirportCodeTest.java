package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AirportCodeTest {

    @Test
    void lowercaseIsNormalizedToUppercase() {
        assertThat(AirportCode.of("sfo").code()).isEqualTo("SFO");
    }

    @Test
    void mixedCaseIsNormalizedToUppercase() {
        assertThat(AirportCode.of("JfK").code()).isEqualTo("JFK");
    }

    @Test
    void nullCodeIsRejected() {
        assertThatThrownBy(() -> AirportCode.of(null))
                .isInstanceOf(InvalidAirportCode.class);
    }

    @Test
    void wrongLengthIsRejected() {
        assertThatThrownBy(() -> AirportCode.of("SF"))
                .isInstanceOf(InvalidAirportCode.class);
        assertThatThrownBy(() -> AirportCode.of("SFOO"))
                .isInstanceOf(InvalidAirportCode.class);
    }

    @Test
    void nonLettersAreRejected() {
        assertThatThrownBy(() -> AirportCode.of("S1O"))
                .isInstanceOf(InvalidAirportCode.class);
    }
}
