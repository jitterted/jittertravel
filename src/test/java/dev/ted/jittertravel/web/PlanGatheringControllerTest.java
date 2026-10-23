package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GatheringPlanning;
import dev.ted.jittertravel.domain.GatheringDateNotInFuture;
import dev.ted.jittertravel.domain.InvalidEnteredLocation;
import dev.ted.jittertravel.domain.InvalidGatheringTimeRange;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.LocationField;
import dev.ted.jittertravel.domain.LocationRole;
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
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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

        String view = controller.planGatheringSubmit(request, bindingResult, new ConcurrentModel());

        assertThat(view)
                .as("a refusal re-renders the form, where the error can be seen")
                .isEqualTo("plan-gathering");
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
        PlanGatheringController controller =
                new PlanGatheringController(refusing(refusal), FIXED_CLOCK);
        PlanGatheringRequest request = new PlanGatheringRequest(
                "", "NY JUG", "Venue", "", "New York", "NY", "", "US", "", "",
                LocalDate.of(2026, 6, 7), LocalTime.of(18, 0), LocalTime.of(21, 0), false, "");
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "planGathering");

        String view = controller.planGatheringSubmit(request, bindingResult, new ConcurrentModel());

        assertThat(view)
                .as("a refusal re-renders the form, where the error can be seen")
                .isEqualTo("plan-gathering");
        assertThat(bindingResult.getFieldErrors())
                .extracting(FieldError::getField)
                .as("the refusal is reported on exactly one input")
                .containsExactly(field);
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                arguments(new GatheringDateNotInFuture("Gathering date must be today or later"), "date"),
                arguments(new InvalidGatheringTimeRange("End time must be after start time"), "endTime"),
                arguments(new ZoneResolutionException("Springfield", "Freedonia"), "zone"));
    }

    /**
     * The attempt that was refused retires its id. A recorded refusal has written a command row
     * under it, so a corrected resubmit wearing the same id collides with that row
     * ({@code docs/archived/ValidateBeforeTheWalPlan.md}). Everything else Ted typed comes back with it.
     */
    @Test
    void aRefusedFormIsReRenderedUnderAFreshId() {
        PlanGatheringController controller = new PlanGatheringController(
                refusing(new GatheringDateNotInFuture("Gathering date must be today or later")), FIXED_CLOCK);
        PlanGatheringRequest request = new PlanGatheringRequest(
                UUID.randomUUID().toString(), "NY JUG", "Venue", "", "New York", "NY", "", "US", "", "",
                LocalDate.of(2026, 6, 7), LocalTime.of(18, 0), LocalTime.of(21, 0), false, "");
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "planGathering");
        Model model = new ConcurrentModel();

        controller.planGatheringSubmit(request, bindingResult, model);

        PlanGatheringRequest reRendered = (PlanGatheringRequest) model.getAttribute("planGathering");
        assertThat(UUID.fromString(reRendered.gatheringId()))
                .as("a corrected resubmit is a new command, never a collision with the refused one")
                .isNotEqualTo(UUID.fromString(request.gatheringId()));
        assertThat(reRendered.title())
                .as("the form keeps what was typed")
                .isEqualTo(request.title());
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
