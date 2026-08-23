package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferencePlanning;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(request.getStartDate()).isEqualTo(LocalDateTime.of(2026, 6, 7, 9, 0));
        assertThat(request.getEndDate()).isEqualTo(LocalDateTime.of(2026, 6, 9, 17, 0));
    }

    @Test
    void getPlanConferenceFormWithDateSeedsStartOnThatDayAtNineAmAndEndTwoDaysLater() {
        PlanConferenceController controller = new PlanConferenceController(writableService(), null, FIXED_CLOCK);
        Model model = new ConcurrentModel();

        controller.planConferenceForm(model, LocalDate.of(2026, 7, 20));

        PlanConferenceRequest request =
                (PlanConferenceRequest) model.getAttribute("planConference");
        assertThat(request.getStartDate()).isEqualTo(LocalDateTime.of(2026, 7, 20, 9, 0));
        assertThat(request.getEndDate()).isEqualTo(LocalDateTime.of(2026, 7, 22, 17, 0));
    }

    // The form GET only reads isReadOnly() and the clock; the projector is unused here.
    private ConferencePlanning writableService() {
        return new ConferencePlanning(null, null, null) {
            @Override public boolean isReadOnly() { return false; }
        };
    }
}
