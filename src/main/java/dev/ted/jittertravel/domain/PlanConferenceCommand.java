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
        // A backwards range is checked first: it is a typo, and the more useful thing to report.
        // Both endpoints share the venue's zone, so comparing instants is the same as comparing
        // wall-clock — and stays right if that ever stops being true. Both are present by now: a
        // blank date is refused at the form by RequiredEntryAdvice, under its own input, and
        // reporting it here as a range would put it under End whichever one was missing.
        if (endDate.utc().isBefore(startDate.utc())) {
            throw new InvalidDateRange("End date must be on or after start date");
        }
        // Only a conference that is over is refused: Ted may go to one he only just heard about,
        // even after it has started (Ted, 2026-09-22). "Over" is a calendar-day question read in
        // the venue's own zone, so the last day itself still counts.
        if (!endDate.isOnOrAfterDayOf(context.now())) {
            throw new ConferenceAlreadyEnded("Conference has already ended");
        }
        return Stream.of(new ConferencePlanned(
                conferenceId, name, startDate, endDate, venueName, venueAddress, format, infoUrl));
    }
}
