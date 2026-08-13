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
        assertThat(request.getDate()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(request.getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(request.getEndTime()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    void getPlanPrivateEventFormWithDateSeedsThatDayAndKeepsEveningTimes() {
        PlanPrivateEventController controller = new PlanPrivateEventController(null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.planPrivateEventForm(model, LocalDate.of(2026, 7, 20));

        PlanPrivateEventRequest request = (PlanPrivateEventRequest) model.getAttribute("planPrivateEvent");
        assertThat(request.getDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(request.getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(request.getEndTime()).isEqualTo(LocalTime.of(21, 0));
    }
}
