package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GroundTransferEndpointChoices;
import dev.ted.jittertravel.application.GroundTransferPlanning;
import dev.ted.jittertravel.application.SameTransferEndpoints;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanGroundTransferControllerTest {

    /** No endpoints offered: these cases are about the defaults, not about preselection. */
    private static final GroundTransferEndpointChoices NO_CHOICES =
            GroundTransferEndpointChoices.nothing();

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 5, 31, 10, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant(),
            ZoneId.systemDefault());

    @Test
    void getPlanGroundTransferFormDefaultsToTodayWithAShortMiddayHop() {
        // Today, not a week out like a gathering: a transfer is normally added to a trip already
        // under way, which is why the command has no future-date rule at all (D6).
        PlanGroundTransferController controller =
                new PlanGroundTransferController(null, null, null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.planGroundTransferForm(model, NO_CHOICES, null, null);

        PlanGroundTransferRequest request = (PlanGroundTransferRequest) model.getAttribute("planGroundTransfer");
        assertThat(request.date()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(request.departureTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(request.arrivalTime()).isEqualTo(LocalTime.of(12, 45));
    }

    @Test
    void getPlanGroundTransferFormWithDateSeedsThatDayAndKeepsTheDefaultTimes() {
        PlanGroundTransferController controller =
                new PlanGroundTransferController(null, null, null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.planGroundTransferForm(model, NO_CHOICES, LocalDate.of(2026, 9, 14), null);

        PlanGroundTransferRequest request = (PlanGroundTransferRequest) model.getAttribute("planGroundTransfer");
        assertThat(request.date()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(request.departureTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(request.arrivalTime()).isEqualTo(LocalTime.of(12, 45));
    }

    @Test
    void eachFormGetsItsOwnGroundTransferId() {
        PlanGroundTransferController controller =
                new PlanGroundTransferController(null, null, null, FIXED_CLOCK);
        Model first = new ConcurrentModel();
        Model second = new ConcurrentModel();

        controller.planGroundTransferForm(first, NO_CHOICES, null, null);
        controller.planGroundTransferForm(second, NO_CHOICES, null, null);

        assertThat(((PlanGroundTransferRequest) first.getAttribute("planGroundTransfer")).groundTransferId())
                .isNotEqualTo(((PlanGroundTransferRequest) second.getAttribute("planGroundTransfer")).groundTransferId());
    }

    /**
     * The attempt that was refused retires its id. A refusal recorded in the command log has
     * written a row under it, so a corrected resubmit wearing the same id collides with that row
     * ({@code docs/archived/ValidateBeforeTheWalPlan.md}). Everything else Ted typed comes back with it.
     */
    @Test
    void aRefusedFormIsReRenderedUnderAFreshId() {
        PlanGroundTransferController controller = new PlanGroundTransferController(
                refusing(new SameTransferEndpoints("A transfer needs two different places")),
                null, null, FIXED_CLOCK);
        PlanGroundTransferRequest request = new PlanGroundTransferRequest(
                UUID.randomUUID().toString(), "airport:DEN", "airport:DEN", "taxi",
                LocalDate.of(2026, 9, 14), LocalTime.of(12, 0), LocalTime.of(12, 45));
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "planGroundTransfer");
        Model model = new ConcurrentModel();

        controller.planGroundTransferSubmit(request, bindingResult, null, null, model);

        PlanGroundTransferRequest reRendered =
                (PlanGroundTransferRequest) model.getAttribute("planGroundTransfer");
        assertThat(UUID.fromString(reRendered.groundTransferId()))
                .as("a corrected resubmit is a new command, never a collision with the refused one")
                .isNotEqualTo(UUID.fromString(request.groundTransferId()));
        assertThat(reRendered.mode())
                .as("the form keeps what was typed")
                .isEqualTo(request.mode());
        Object carried = model.getAttribute(BindingResult.MODEL_KEY_PREFIX + "planGroundTransfer");
        assertThat(carried)
                .as("th:field finds the errors through the binding result Spring keys by the attribute name")
                .isInstanceOf(BindingResult.class);
        BindingResult carriedOver = (BindingResult) carried;
        assertThat(carriedOver.getTarget())
                .as("and reads every value through its target, so that must be the re-rendered request")
                .isSameAs(reRendered);
        assertThat(carriedOver.getFieldErrors("destination"))
                .as("the refusal still lands under the input that fixes it")
                .hasSize(1);
    }

    /** From the calendar's day menu it goes back to that day; with no origin, to the calendar. */
    @Test
    void aPlannedTransferReturnsToTheDayItWasStartedFrom() {
        PlanGroundTransferController controller = new PlanGroundTransferController(
                new GroundTransferPlanning(null, null) {
                    @Override
                    public void planGroundTransfer(PlanGroundTransferRequest request) {
                    }
                },
                null, null, FIXED_CLOCK);
        PlanGroundTransferRequest request = new PlanGroundTransferRequest(
                UUID.randomUUID().toString(), "airport:DEN", "hotel:abc", "taxi",
                LocalDate.of(2026, 9, 14), LocalTime.of(12, 0), LocalTime.of(12, 45));

        String view = controller.planGroundTransferSubmit(request,
                new BeanPropertyBindingResult(request, "planGroundTransfer"),
                "calendar", "2026-09-14", new ConcurrentModel());

        assertThat(view)
                .as("where a planned transfer lands")
                .isEqualTo("redirect:/calendar?day=2026-09-14");
    }

    /** A service whose every transfer is refused with {@code refusal}. */
    private static GroundTransferPlanning refusing(RuntimeException refusal) {
        return new GroundTransferPlanning(null, null) {
            @Override
            public void planGroundTransfer(PlanGroundTransferRequest request) {
                throw refusal;
            }
        };
    }
}
