package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PrivateEventPlanning;
import dev.ted.jittertravel.domain.InvalidPrivateEventTimeRange;
import dev.ted.jittertravel.domain.LocationField;
import dev.ted.jittertravel.domain.PrivateEventDateNotInFuture;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.UUID;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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

        String view = controller.planPrivateEventSubmit(request, bindingResult, new ConcurrentModel());

        assertThat(view)
                .as("a refusal re-renders the form, where the error can be seen")
                .isEqualTo("plan-private-event");
        assertThat(bindingResult.getFieldErrors())
                .extracting(FieldError::getField)
                .as("the refusal is reported on exactly one input")
                .containsExactly(field);
    }

    /**
     * One row per {@code catch} in the controller, each saying which input the refusal lands on
     * (CLAUDE.md, "Testing a form's validation: three claims, three tiers"). The venue refusals
     * have their own table above because they carry the field inside the exception. The
     * time-range row arrived on 2026-10-23, when mutation testing found that catch unreached.
     */
    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("refusals")
    void eachRefusalLandsOnTheInputThatFixesIt(RuntimeException refusal, String field) {
        PlanPrivateEventController controller =
                new PlanPrivateEventController(refusing(refusal), FIXED_CLOCK);
        PlanPrivateEventRequest request = new PlanPrivateEventRequest(
                "", "Dinner", "Alo", "", "Centennial", "CO", "", "US", "", "",
                LocalDate.of(2026, 6, 7), LocalTime.of(18, 0), LocalTime.of(21, 0));
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "planPrivateEvent");

        String view = controller.planPrivateEventSubmit(request, bindingResult, new ConcurrentModel());

        assertThat(view)
                .as("a refusal re-renders the form, where the error can be seen")
                .isEqualTo("plan-private-event");
        assertThat(bindingResult.getFieldErrors())
                .extracting(FieldError::getField)
                .as("the refusal is reported on exactly one input")
                .containsExactly(field);
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                arguments(new PrivateEventDateNotInFuture("Private event date must be today or later"), "date"),
                arguments(new InvalidPrivateEventTimeRange("End time must be after start time"), "endTime"),
                arguments(new ZoneResolutionException("Springfield", "Freedonia"), "zone"));
    }

    /**
     * The attempt that was refused retires its id. A recorded refusal has written a command row
     * under it, so a corrected resubmit wearing the same id collides with that row
     * ({@code docs/archived/ValidateBeforeTheWalPlan.md}). Everything else Ted typed comes back with it.
     */
    @Test
    void aRefusedFormIsReRenderedUnderAFreshId() {
        PlanPrivateEventController controller = new PlanPrivateEventController(
                refusing(new PrivateEventDateNotInFuture("Private event date must be today or later")),
                FIXED_CLOCK);
        PlanPrivateEventRequest request = new PlanPrivateEventRequest(
                UUID.randomUUID().toString(), "Dinner", "Alo", "", "Centennial", "CO", "", "US", "", "",
                LocalDate.of(2026, 6, 7), LocalTime.of(18, 0), LocalTime.of(21, 0));
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "planPrivateEvent");
        Model model = new ConcurrentModel();

        controller.planPrivateEventSubmit(request, bindingResult, model);

        PlanPrivateEventRequest reRendered = (PlanPrivateEventRequest) model.getAttribute("planPrivateEvent");
        assertThat(UUID.fromString(reRendered.privateEventId()))
                .as("a corrected resubmit is a new command, never a collision with the refused one")
                .isNotEqualTo(UUID.fromString(request.privateEventId()));
        assertThat(reRendered.title())
                .as("the form keeps what was typed")
                .isEqualTo(request.title());
        Object carried = model.getAttribute(BindingResult.MODEL_KEY_PREFIX + "planPrivateEvent");
        assertThat(carried)
                .as("th:field finds the errors through the binding result Spring keys by the attribute name")
                .isInstanceOf(BindingResult.class);
        BindingResult carriedOver = (BindingResult) carried;
        assertThat(carriedOver.getTarget())
                .as("and reads every value through its target, so that must be the re-rendered request")
                .isSameAs(reRendered);
        assertThat(carriedOver.getFieldErrors("date"))
                .as("the refusal still lands under the input that fixes it")
                .hasSize(1);
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
