package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.web.PlanTentativeConferenceRequest;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

public class PlanTentativeConferenceCommand {

    public Stream<ConferenceTentativelyPlanned> execute(PlanTentativeConferenceRequest dto, LocalDateTime now) {
        if (dto.getStartDate() == null || dto.getStartDate().isBefore(now.plusDays(1))) {
            throw new DateRangeNotInFuture("Start date must be at least 1 day in the future");
        }
        if (dto.getEndDate() == null || dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new InvalidDateRange("End date must be on or after start date");
        }

        ConferenceId conferenceId = ConferenceId.of(UUID.fromString(dto.getConferenceId()));
        return Stream.of(new ConferenceTentativelyPlanned(
                conferenceId,
            dto.getName(),
            dto.getStartDate(),
            dto.getEndDate(),
            dto.getVenueName(),
            dto.getVenueAddress()
        ));
    }
}
