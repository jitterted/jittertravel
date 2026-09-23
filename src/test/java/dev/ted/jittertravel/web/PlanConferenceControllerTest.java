package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CfpDeadlineMissing;
import dev.ted.jittertravel.application.ConferencePlanning;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.ConferenceAlreadyEnded;
import dev.ted.jittertravel.domain.ConferenceHasNoCfp;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class PlanConferenceControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 5, 31, 10, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant(),
            ZoneId.systemDefault());

    @Test
    void getPlanConferenceFormSetsStartOneWeekFromNowAtNineAmAndEndTwoDaysLater() {
        PlanConferenceController controller = new PlanConferenceController(writableService(), null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.planConferenceForm(model, null);

        PlanConferenceRequest request =
                (PlanConferenceRequest) model.getAttribute("planConference");
        assertThat(request.startDate()).isEqualTo(LocalDateTime.of(2026, 6, 7, 9, 0));
        assertThat(request.endDate()).isEqualTo(LocalDateTime.of(2026, 6, 9, 17, 0));
    }

    @Test
    void getPlanConferenceFormWithDateSeedsStartOnThatDayAtNineAmAndEndTwoDaysLater() {
        PlanConferenceController controller = new PlanConferenceController(writableService(), null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.planConferenceForm(model, LocalDate.of(2026, 7, 20));

        PlanConferenceRequest request =
                (PlanConferenceRequest) model.getAttribute("planConference");
        assertThat(request.startDate()).isEqualTo(LocalDateTime.of(2026, 7, 20, 9, 0));
        assertThat(request.endDate()).isEqualTo(LocalDateTime.of(2026, 7, 22, 17, 0));
    }

    /**
     * Each refusal the service can raise lands on the input that fixes it, and the form is
     * re-rendered rather than redirected. The service is a stub programmed to throw, so the only
     * code deciding the field is the controller's own — whether a real input produces each
     * refusal is the command's and the service's tests to say.
     */
    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("refusals")
    void eachRefusalLandsOnTheInputThatFixesIt(RuntimeException refusal, String field) {
        PlanConferenceController controller =
                new PlanConferenceController(refusing(refusal), null, FIXED_CLOCK);
        PlanConferenceRequest request = aConferenceForm();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "planConference");

        String view = controller.planConferenceSubmit(request, bindingResult);

        assertThat(view)
                .as("a refusal re-renders the form, where the error can be seen")
                .isEqualTo("plan-conference");
        assertThat(bindingResult.getFieldErrors())
                .extracting(FieldError::getField)
                .as("the refusal is reported on exactly one input")
                .containsExactly(field);
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                arguments(new ConferenceAlreadyEnded("Conference has already ended"), "endDate"),
                arguments(new InvalidDateRange("End date must be on or after start date"), "endDate"),
                arguments(new ConferenceHasNoCfp("no call for papers"), "cfpClosesOn"),
                arguments(new CfpDeadlineMissing("needs the closing date too"), "cfpClosesOn"),
                arguments(new ZoneResolutionException("Springfield", "Freedonia"), "zone"));
    }

    @Test
    void readOnlyModeRedirectsRatherThanReportingAFieldError() {
        PlanConferenceController controller = new PlanConferenceController(
                refusing(new ReadOnlyModeException("read-only")), null, FIXED_CLOCK);
        PlanConferenceRequest request = aConferenceForm();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "planConference");

        String view = controller.planConferenceSubmit(request, bindingResult);

        assertThat(view)
                .isEqualTo("redirect:/read-only");
        assertThat(bindingResult.hasErrors())
                .as("nothing on the form was wrong")
                .isFalse();
    }

    // The form GET only reads isReadOnly() and the clock; the projector is unused here.
    private ConferencePlanning writableService() {
        return new ConferencePlanning(null, null, null) {
            @Override public boolean isReadOnly() { return false; }
        };
    }

    /** Any well-formed form: which refusal comes back is the stub's choice, not the input's. */
    private static PlanConferenceRequest aConferenceForm() {
        return new PlanConferenceRequest(
                UUID.randomUUID().toString(), "JitterConf",
                LocalDateTime.of(2026, 6, 7, 9, 0), LocalDateTime.of(2026, 6, 9, 17, 0),
                "Moscone Center", "747 Howard St", "San Francisco", "CA", "USA", "94103",
                null, null, null, null, null);
    }

    /** A writable service whose every plan is refused with {@code refusal}. */
    private static ConferencePlanning refusing(RuntimeException refusal) {
        return new ConferencePlanning(null, null, null) {
            @Override public boolean isReadOnly() { return false; }

            @Override public void planConference(PlanConferenceRequest request, Instant now,
                                                 UUID cfpCommandId) {
                throw refusal;
            }
        };
    }
}
