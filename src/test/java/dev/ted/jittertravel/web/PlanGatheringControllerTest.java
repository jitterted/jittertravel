package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GatheringPlanning;
import dev.ted.jittertravel.domain.InvalidEnteredLocation;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.LocationField;
import dev.ted.jittertravel.domain.LocationRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanGatheringControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 5, 31, 10, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant(),
            ZoneId.systemDefault());

    @Test
    void getPlanGatheringFormSetsDateOneWeekFromNowWithEveningTimes() {
        PlanGatheringController controller = new PlanGatheringController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.planGatheringForm(model, null);

        PlanGatheringRequest request = (PlanGatheringRequest) model.getAttribute("planGathering");
        assertThat(request.date()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(request.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(request.endTime()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    void getPlanGatheringFormWithDateSeedsThatDayAndKeepsEveningTimes() {
        PlanGatheringController controller = new PlanGatheringController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.planGatheringForm(model, LocalDate.of(2026, 7, 20));

        PlanGatheringRequest request = (PlanGatheringRequest) model.getAttribute("planGathering");
        assertThat(request.date()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(request.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(request.endTime()).isEqualTo(LocalTime.of(21, 0));
    }

    @ParameterizedTest
    @CsvSource({"COUNTRY, country", "REGION, region"})
    void aRefusedVenueLandsOnTheInputThatFixesIt(LocationField refused, String field) {
        PlanGatheringController controller =
                new PlanGatheringController(refusing(venueRefusal(refused)), FIXED_CLOCK);
        PlanGatheringRequest request = new PlanGatheringRequest(
                "", "NY JUG", "Venue", "", "New York", "", "", "US", "", "",
                LocalDate.of(2026, 6, 7), LocalTime.of(18, 0), LocalTime.of(21, 0), false, "");
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "planGathering");

        String view = controller.planGatheringSubmit(request, bindingResult);

        assertThat(view)
                .as("a refusal re-renders the form, where the error can be seen")
                .isEqualTo("plan-gathering");
        assertThat(bindingResult.getFieldErrors())
                .extracting(FieldError::getField)
                .as("the refusal is reported on exactly one input")
                .containsExactly(field);
    }

    static InvalidEnteredLocation venueRefusal(LocationField field) {
        return new InvalidEnteredLocation(List.of(
                new InvalidLocationEntry(LocationRole.VENUE, field, "refused")));
    }

    private static GatheringPlanning refusing(RuntimeException refusal) {
        return new GatheringPlanning(null, null) {
            @Override
            public void planGathering(PlanGatheringRequest request, Instant now) {
                throw refusal;
            }
        };
    }
}
