package dev.ted.jittertravel.domain;

/**
 * A conference has been tentatively planned — a multi-day (or single-full-day) event, as opposed to
 * a {@link GatheringPlanned}, which is a few hours on one day.
 * <p>
 * {@code startDate}/{@code endDate} are {@link ZonedTimestamp}s in the venue's single zone, so "is
 * it over?" is answerable without knowing where the server runs. Legacy payloads carrying bare
 * wall-clock scalars are rewritten by {@code EventPayloadUpcaster} at read time.
 * <p>
 * {@code format} (schema v3) records how the conference forms its program — see
 * {@link ConferenceFormat}. It is never null: the read-time upcaster injects
 * {@link ConferenceFormat#CALL_FOR_PAPERS} into pre-v3 payloads before they bind, so an absent value
 * fails loud here rather than reaching a projector as a null.
 */
public record ConferenceTentativelyPlanned(
        ConferenceId conferenceId,
        String name,
        ZonedTimestamp startDate,
        ZonedTimestamp endDate,
        String venueName,
        Address venueAddress,
        ConferenceFormat format
) implements Event {

    public ConferenceTentativelyPlanned {
        if (format == null) {
            throw new IllegalArgumentException(
                    "format must not be null — legacy payloads are upcast to CALL_FOR_PAPERS before binding");
        }
    }

    /**
     * Convenience overload for call sites (tests, the gathering→conference migration) that predate
     * {@code format} and do not care which program model a conference uses: it defaults to
     * {@link ConferenceFormat#CALL_FOR_PAPERS}, the same value the upcaster injects into legacy
     * payloads. Not used by Jackson, which binds through the canonical seven-argument constructor.
     */
    public ConferenceTentativelyPlanned(ConferenceId conferenceId, String name,
                                        ZonedTimestamp startDate, ZonedTimestamp endDate,
                                        String venueName, Address venueAddress) {
        this(conferenceId, name, startDate, endDate, venueName, venueAddress,
                ConferenceFormat.CALL_FOR_PAPERS);
    }
}
