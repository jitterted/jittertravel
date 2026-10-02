package dev.ted.jittertravel.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryEvaluationTest {

    @Test
    void aMissingConfirmationCodeIsTheEmptyStringNeverNull() {
        ItineraryEvaluation evaluation = new ItineraryEvaluation(List.of(), null, List.of(), List.of(), null);

        assertThat(evaluation.confirmationCode())
                .isEmpty();
    }

    @Test
    void aGivenConfirmationCodeIsKept() {
        ItineraryEvaluation evaluation = new ItineraryEvaluation(List.of(), "MD7LKB", List.of(), List.of(), null);

        assertThat(evaluation.confirmationCode())
                .isEqualTo("MD7LKB");
    }

    @Test
    void anEvaluationWithNoCommandIsNotBookable() {
        assertThat(ItineraryEvaluation.unparseable(List.of("No flights found")).bookable())
                .isFalse();
    }
}
