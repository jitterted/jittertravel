package dev.ted.jittertravel.domain;

/**
 * A conference has been put on the schedule — a multi-day (or single-full-day) event, as opposed to
 * a {@link GatheringPlanned}, which is a few hours on one day.
 * <p>
 * Whether Ted is actually <em>going</em> is not recorded here: attendance commitment is derived by
 * folding the later commitment events, so this is the entry point onto the watch list and nothing more.
 * (Named {@code ConferenceTentativelyPlanned} until 2026-08-19, when "tentative" became that derived
 * status; {@code EventTypes} aliases the old wire ids.)
 * <p>
 * {@code startDate}/{@code endDate} are {@link ZonedTimestamp}s in the venue's single zone, so "is
 * it over?" is answerable without knowing where the server runs. Legacy payloads carrying bare
 * wall-clock scalars are rewritten by {@code EventPayloadUpcaster} at read time.
 * <p>
 * {@code format} (schema v3) records how the conference forms its program — see
 * {@link ConferenceFormat}. It is never null: the read-time upcaster injects
 * {@link ConferenceFormat#CALL_FOR_PAPERS} into pre-v3 payloads before they bind, so an absent value
 * fails loud here rather than reaching a projector as a null.
 * <p>
 * {@code infoUrl} is the conference's own public web page — the same field a
 * {@link GatheringPlanned} has carried all along, and the reason a conference title could not link
 * anywhere until 2026-08-22. <strong>It needs no schema bump and no upcaster</strong>, and the
 * contrast with {@code format} is the point: {@code format} is behavioural and non-null, so an
 * absent value had to be *stored* rather than invented at read time, while a missing URL has an
 * obvious empty sentinel. A legacy payload with no {@code infoUrl} field binds to null and the
 * compact constructor normalizes it to {@code ""} (CLAUDE.md, "No null Strings in domain"), exactly
 * as {@link GatheringPlanned} does — so every backup taken before this field existed still restores
 * unchanged.
 * <p>
 * <strong>{@code infoUrl} is public.</strong> A conference is a public event Ted attends publicly,
 * and CLAUDE.md lists its {@code infoUrl} among the things published in full; it reaches the
 * anonymous {@code /calendar} through {@code EntryDetails.PublicConference}. That is the opposite of
 * {@link CfpOpened#submissionUrl()}, which is OWNER-only — see there.
 */
public record ConferencePlanned(
        ConferenceId conferenceId,
        String name,
        ZonedTimestamp startDate,
        ZonedTimestamp endDate,
        String venueName,
        Address venueAddress,
        ConferenceFormat format,
        String infoUrl
) implements Event {

    public ConferencePlanned {
        if (format == null) {
            throw new IllegalArgumentException(
                    "format must not be null — legacy payloads are upcast to CALL_FOR_PAPERS before binding");
        }
        if (infoUrl == null) {
            infoUrl = "";
        }
    }

    /**
     * Convenience overload for call sites that predate {@code infoUrl} and are not about it. Not
     * used by Jackson, which binds through the canonical eight-argument constructor.
     */
    public ConferencePlanned(ConferenceId conferenceId, String name,
                             ZonedTimestamp startDate, ZonedTimestamp endDate,
                             String venueName, Address venueAddress, ConferenceFormat format) {
        this(conferenceId, name, startDate, endDate, venueName, venueAddress, format, "");
    }

    /**
     * Convenience overload for call sites (tests, the gathering→conference migration) that predate
     * {@code format} and do not care which program model a conference uses: it defaults to
     * {@link ConferenceFormat#CALL_FOR_PAPERS}, the same value the upcaster injects into legacy
     * payloads.
     */
    public ConferencePlanned(ConferenceId conferenceId, String name,
                             ZonedTimestamp startDate, ZonedTimestamp endDate,
                             String venueName, Address venueAddress) {
        this(conferenceId, name, startDate, endDate, venueName, venueAddress,
                ConferenceFormat.CALL_FOR_PAPERS, "");
    }
}
