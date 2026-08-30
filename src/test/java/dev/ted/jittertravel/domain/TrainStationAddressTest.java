package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A station normalizes like an {@link Address} does, and for the same reason: {@link Place} reads
 * its city, so the city is compared and not merely displayed. The fixtures here are the real
 * damage — the production log carries a leading space on " Frankfurt(M) Flughafen Fernbf" and a
 * trailing one on "London St Pancras Int'l (STP) East ".
 */
class TrainStationAddressTest {

    @Test
    void surroundingWhitespaceIsRemovedFromEveryField() {
        TrainStationAddress typedOnAPhone =
                new TrainStationAddress(" Frankfurt(M) Flughafen Fernbf", "Frankfurt ",
                                        " Germany ", " https://maps.example/1 ");

        assertThat(typedOnAPhone)
                .isEqualTo(new TrainStationAddress("Frankfurt(M) Flughafen Fernbf", "Frankfurt",
                                                   "Germany", "https://maps.example/1"));
    }

    @Test
    void aCityTypedWithATrailingSpaceIsTheSamePlaceAsOneWithout() {
        TrainStationAddress dirty = new TrainStationAddress("Hamburg Hbf", "Hamburg ", "DE", "");
        TrainStationAddress clean = new TrainStationAddress("Hamburg Dammtor", "Hamburg", "DE", "");

        assertThat(Place.of(dirty).matches(Place.of(clean)))
                .as("a stray space is invisible in the markup and must be invisible to matching")
                .isTrue();
    }

    @Test
    void everyAbsentFieldBecomesTheBlankString() {
        TrainStationAddress nothingTyped = new TrainStationAddress(null, null, null, null);

        assertThat(nothingTyped)
                .isEqualTo(new TrainStationAddress("", "", "", ""));
    }
}
