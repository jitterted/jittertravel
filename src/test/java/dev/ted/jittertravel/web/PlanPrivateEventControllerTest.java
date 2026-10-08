package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PrivateEventPlanning;
import dev.ted.jittertravel.domain.LocationField;
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

import static org.assertj.core.api.Assertions.assertThat;

class PlanPrivateEventControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 5, 31, 10, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant(),
            ZoneId.systemDefault());

    @Test
    void getPlanPrivateEventFormSetsDateOneWeekFromNowWithEveningTimes() {
        PlanPrivateEventController controller = new PlanPrivateEventController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.planPrivateEventForm(model, null);

        PlanPrivateEventRequest request = (PlanPrivateEventRequest) model.getAttribute("planPrivateEvent");
        assertThat(request.date()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(request.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(request.endTime()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    void getPlanPrivateEventFormWithDateSeedsThatDayAndKeepsEveningTimes() {
        PlanPrivateEventController controller = new PlanPrivateEventController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.planPrivateEventForm(model, LocalDate.of(2026, 7, 20));

        PlanPrivateEventRequest request = (PlanPrivateEventRequest) model.getAttribute("planPrivateEvent");
        assertThat(request.date()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(request.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(request.endTime()).isEqualTo(LocalTime.of(21, 0));
    }

    @ParameterizedTest
    @CsvSource({"COUNTRY, country", "REGION, region"})
    void aRefusedVenueLandsOnTheInputThatFixesIt(LocationField refused, String field) {
        PlanPrivateEventController controller = new PlanPrivateEventController(
                refusing(PlanGatheringControllerTest.venueRefusal(refused)), FIXED_CLOCK);
        PlanPrivateEventRequest request = new PlanPrivateEventRequest(
                "", "Dinner", "Alo", "", "Centennial", "", "", "US", "", "",
                LocalDate.of(2026, 6, 7), LocalTime.of(18, 0), LocalTime.of(21, 0));
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "planPrivateEvent");

        String view = controller.planPrivateEventSubmit(request, bindingResult);

        assertThat(view)
                .as("a refusal re-renders the form, where the error can be seen")
                .isEqualTo("plan-private-event");
        assertThat(bindingResult.getFieldErrors())
                .extracting(FieldError::getField)
                .as("the refusal is reported on exactly one input")
                .containsExactly(field);
    }

    private static PrivateEventPlanning refusing(RuntimeException refusal) {
        return new PrivateEventPlanning(null, null) {
            @Override
            public void planPrivateEvent(PlanPrivateEventRequest request, Instant now) {
                throw refusal;
            }
        };
    }
}
