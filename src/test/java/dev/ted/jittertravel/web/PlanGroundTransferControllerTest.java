package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class PlanGroundTransferControllerTest {

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
                new PlanGroundTransferController(null, null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.planGroundTransferForm(model, null);

        PlanGroundTransferRequest request = (PlanGroundTransferRequest) model.getAttribute("planGroundTransfer");
        assertThat(request.getDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(request.getDepartureTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(request.getArrivalTime()).isEqualTo(LocalTime.of(12, 45));
    }

    @Test
    void getPlanGroundTransferFormWithDateSeedsThatDayAndKeepsTheDefaultTimes() {
        PlanGroundTransferController controller =
                new PlanGroundTransferController(null, null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.planGroundTransferForm(model, LocalDate.of(2026, 9, 14));

        PlanGroundTransferRequest request = (PlanGroundTransferRequest) model.getAttribute("planGroundTransfer");
        assertThat(request.getDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(request.getDepartureTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(request.getArrivalTime()).isEqualTo(LocalTime.of(12, 45));
    }

    @Test
    void eachFormGetsItsOwnGroundTransferId() {
        PlanGroundTransferController controller =
                new PlanGroundTransferController(null, null, FIXED_CLOCK);
        Model first = new ConcurrentModel();
        Model second = new ConcurrentModel();

        controller.planGroundTransferForm(first, null);
        controller.planGroundTransferForm(second, null);

        assertThat(((PlanGroundTransferRequest) first.getAttribute("planGroundTransfer")).getGroundTransferId())
                .isNotEqualTo(((PlanGroundTransferRequest) second.getAttribute("planGroundTransfer")).getGroundTransferId());
    }
}
