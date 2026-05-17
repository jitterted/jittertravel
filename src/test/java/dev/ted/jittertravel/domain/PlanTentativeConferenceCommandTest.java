package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.domain.DateRangeNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.web.PlanTentativeConferenceRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class PlanTentativeConferenceCommandTest {

    @Test
    void startDateInThePastFails() {
        PlanTentativeConferenceCommand command = new PlanTentativeConferenceCommand();
        PlanTentativeConferenceRequest dto = new PlanTentativeConferenceRequest();
        LocalDateTime now = LocalDateTime.of(2026, 5, 16, 10, 0);
        dto.setStartDate(now.plusHours(23)); 
        dto.setEndDate(now.plusDays(2));

        assertThatExceptionOfType(DateRangeNotInFuture.class)
                .isThrownBy(() -> command.execute(dto, now));
    }

    @Test
    void endDateBeforeStartDateFails() {
        PlanTentativeConferenceCommand command = new PlanTentativeConferenceCommand();
        PlanTentativeConferenceRequest dto = new PlanTentativeConferenceRequest();
        LocalDateTime now = LocalDateTime.of(2026, 5, 16, 10, 0);
        dto.setStartDate(now.plusDays(1));
        dto.setEndDate(now.plusDays(1).minusHours(1));

        assertThatExceptionOfType(InvalidDateRange.class)
                .isThrownBy(() -> command.execute(dto, now));
    }

    @Test
    void successReturnsConferenceTentativelyPlannedEvent() {
        PlanTentativeConferenceRequest dto = new PlanTentativeConferenceRequest();
        ConferenceId conferenceId = ConferenceId.random();
        dto.setConferenceId(conferenceId.id().toString());
        dto.setName("Successful Conference");
        dto.setStartDate(LocalDateTime.of(2026, 6, 1, 10, 0));
        dto.setEndDate(LocalDateTime.of(2026, 6, 3, 17, 0));
        dto.setVenueName("Test Venue");
        dto.setVenueStreet("Test Street");
        dto.setVenueCity("Test City");
        dto.setVenueCountry("Test Country");
        dto.setVenuePostalCode("12345");

        LocalDateTime now = LocalDateTime.now();
        Stream<ConferenceTentativelyPlanned> events = new PlanTentativeConferenceCommand().execute(dto, now);

        List<ConferenceTentativelyPlanned> eventList = events.toList();
        assertThat(eventList).hasSize(1);
        assertThat(eventList.getFirst()).isInstanceOf(ConferenceTentativelyPlanned.class);

        ConferenceTentativelyPlanned event = eventList.getFirst();
        assertThat(event.conferenceId())
                .isEqualTo(conferenceId);
        assertThat(event.name())
                .isEqualTo("Successful Conference");
        assertThat(event.startDate())
                .isEqualTo(dto.getStartDate());
        assertThat(event.venueAddress().street())
                .isEqualTo("Test Street");
    }
}
