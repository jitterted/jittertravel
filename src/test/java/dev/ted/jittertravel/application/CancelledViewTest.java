package dev.ted.jittertravel.application;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CancelledViewTest {

    @ParameterizedTest(name = "?cancelled={0}")
    @CsvSource({"show, SHOW", "SHOW, SHOW", "Show, SHOW", "hide, HIDE"})
    void aRecognizedValueResolvesWhateverItsCase(String param, CancelledView expected) {
        assertThat(CancelledView.fromParam(param))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "?cancelled={0}")
    @NullSource
    @ValueSource(strings = {"", "banana", "shown"})
    void anAbsentOrUnrecognizedValueHidesCanceledRows(String param) {
        assertThat(CancelledView.fromParam(param))
                .as("a hand-edited or stale link falls back to the default view")
                .isEqualTo(CancelledView.HIDE);
    }
}
