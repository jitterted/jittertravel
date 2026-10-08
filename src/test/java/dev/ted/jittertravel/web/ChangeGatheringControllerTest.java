package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeGathering;
import dev.ted.jittertravel.domain.LocationField;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeGatheringControllerTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-31T10:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest
    @CsvSource({"COUNTRY, country", "REGION, region"})
    void aRefusedVenueLandsOnTheInputThatFixesIt(LocationField refused, String field) {
        ChangeGatheringController controller = new ChangeGatheringController(
                refusing(PlanGatheringControllerTest.venueRefusal(refused)), null, FIXED_CLOCK);
        ChangeGatheringRequest request = new ChangeGatheringRequest(
                "NY JUG", "Venue", "", "New York", "", "", "US", "", "",
                LocalDate.of(2026, 6, 7), LocalTime.of(18, 0), LocalTime.of(21, 0), false, "");
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "changeGathering");

        String view = controller.changeGatheringSubmit(UUID.randomUUID().toString(), request,
                                                       bindingResult);

        assertThat(view)
                .as("a refusal re-renders the form, where the error can be seen")
                .isEqualTo("change-gathering");
        assertThat(bindingResult.getFieldErrors())
                .extracting(FieldError::getField)
                .as("the refusal is reported on exactly one input")
                .containsExactly(field);
    }

    private static ChangeGathering refusing(RuntimeException refusal) {
        return new ChangeGathering(null, null, null) {
            @Override
            public void changeGathering(UUID commandId, String gatheringId,
                                        ChangeGatheringRequest request, Instant now) {
                throw refusal;
            }
        };
    }
}
