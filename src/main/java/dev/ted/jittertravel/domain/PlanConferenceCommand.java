package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

public record PlanConferenceCommand(
        ConferenceId conferenceId,
        String name,
        ZonedTimestamp startDate,
        ZonedTimestamp endDate,
        String venueName,
        Address venueAddress,
        ConferenceFormat format,
        String infoUrl
) implements DomainCommand<PlanConferenceContext> {

    public PlanConferenceCommand {
        if (infoUrl == null) {
            infoUrl = "";
        }
    }

    /** Convenience overload for call sites that do not set the conference's own web page. */
    public PlanConferenceCommand(ConferenceId conferenceId, String name,
                                 ZonedTimestamp startDate, ZonedTimestamp endDate,
                                 String venueName, Address venueAddress, ConferenceFormat format) {
        this(conferenceId, name, startDate, endDate, venueName, venueAddress, format, "");
    }

    @Override
    public Stream<ConferencePlanned> execute(PlanConferenceContext context) {
        // "At least a day out" is a calendar-day question read in the venue's own zone, matching
        // PlanGatheringCommand. It used to be a 24-hour wall-clock comparison against the server's
        // clock, so a conference starting tomorrow morning is now accepted even when that is less
        // than 24 hours away — which is what "at least 1 day in the future" reads as.
        if (startDate == null || !startDate.isOnDayAfter(context.now())) {
            throw new DateRangeNotInFuture("Start date must be at least 1 day in the future");
        }
        // Both endpoints share the venue's zone, so comparing instants is the same as comparing
        // wall-clock — and stays right if that ever stops being true.
        if (endDate == null || endDate.utc().isBefore(startDate.utc())) {
            throw new InvalidDateRange("End date must be on or after start date");
        }
        return Stream.of(new ConferencePlanned(
                conferenceId, name, startDate, endDate, venueName, venueAddress, format, infoUrl));
    }
}
