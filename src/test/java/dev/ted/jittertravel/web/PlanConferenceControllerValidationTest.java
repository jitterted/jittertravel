package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferencePlanning;
import dev.ted.jittertravel.domain.DateRangeNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.PlanTentativeConferenceCommand;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PlanConferenceControllerValidationTest {

    @Test
    void startDateInThePastIsInvalid() {
        ConferencePlanning service = mockService(false);
        PlanTentativeConferenceRequest command = new PlanTentativeConferenceRequest();
        LocalDateTime now = LocalDateTime.of(2026, 5, 16, 10, 0);
        command.setStartDate(now.plusHours(23)); // Less than 1 day in the future
        command.setEndDate(now.plusDays(2));
        command.setConferenceId(java.util.UUID.randomUUID().toString());
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "planTentativeConference");

        try {
            service.planConference(command);
        } catch (DateRangeNotInFuture e) {
            bindingResult.rejectValue("startDate", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("endDate", "afterStartDate", e.getMessage());
        }

        assertThat(bindingResult.hasFieldErrors("startDate"))
                .isTrue();
    }

    @Test
    void startDateExactlyOneDayInFutureIsValid() {
        ConferencePlanning service = mockService(false);
        PlanTentativeConferenceRequest command = new PlanTentativeConferenceRequest();
        LocalDateTime now = LocalDateTime.of(2026, 5, 16, 10, 0);
        command.setConferenceId(java.util.UUID.randomUUID().toString());
        command.setStartDate(now.plusDays(1)); 
        command.setEndDate(now.plusDays(2));
        command.setName("Valid Name");
        command.setVenueStreet("Street");
        command.setVenueCity("City");
        command.setVenueCountry("Country");
        command.setVenuePostalCode("12345");
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "planTentativeConference");

        try {
            service.planConference(command);
        } catch (Exception e) {
            bindingResult.reject("error", e.getMessage());
        }

        assertThat(bindingResult.hasFieldErrors("startDate"))
                .isFalse();
        assertThat(bindingResult.hasErrors())
                .isFalse();
    }

    @Test
    void endDateBeforeStartDateIsInvalid() {
        ConferencePlanning service = mockService(false);
        PlanTentativeConferenceRequest command = new PlanTentativeConferenceRequest();
        LocalDateTime now = LocalDateTime.of(2026, 5, 16, 10, 0);
        command.setStartDate(now.plusDays(1));
        command.setEndDate(now.plusDays(1).minusHours(1));
        command.setConferenceId(java.util.UUID.randomUUID().toString());
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "planTentativeConference");

        try {
            service.planConference(command);
        } catch (DateRangeNotInFuture e) {
            bindingResult.rejectValue("startDate", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("endDate", "afterStartDate", e.getMessage());
        }

        assertThat(bindingResult.hasFieldErrors("endDate"))
                .isTrue();
    }

    private ConferencePlanning mockService(boolean readOnly) {
        return new ConferencePlanning(null, null) {
            @Override public boolean isReadOnly() { return readOnly; }
            @Override public void planConference(PlanTentativeConferenceRequest request) {
                new PlanTentativeConferenceCommand().execute(request, LocalDateTime.of(2026, 5, 16, 10, 0));
            }
        };
    }
}
