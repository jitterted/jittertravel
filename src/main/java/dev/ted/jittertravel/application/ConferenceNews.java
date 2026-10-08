package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ZonedTimestamp;

/**
 * Everything a family email may say about a conference, and nothing else. It is built from the
 * conference's own events by {@link ConferenceStanding}, and it is the whole list: no
 * {@code AttendanceBasis}, no CFP date, and above all no decline or cancellation reason (Ted,
 * 2026-09-10), so a field this record does not carry cannot reach an email.
 *
 * @param venueName the building, or blank when none was recorded
 * @param city      where it is, or blank
 * @param qualifier the US state code or the country, per {@link CityLabel}; or blank
 * @param infoUrl   the conference's own public page, or blank
 */
public record ConferenceNews(String name, String venueName, String city, String qualifier,
                             ZonedTimestamp startDate, ZonedTimestamp endDate, String infoUrl,
                             SpeakingLine speaking) {

    /** What, if anything, the going email says about a talk. */
    public enum SpeakingLine {
        NONE,
        /** His talk was accepted. */
        TALK_ACCEPTED,
        /** He was invited to speak, and took it up. */
        INVITED
    }

    /** Why he is no longer going, which picks the sentence (Ted, 2026-09-10). */
    public enum Exit {
        /** Ted decided not to go. */
        DECLINED,
        /** The organizers cancelled the conference. */
        CANCELLED,
        /** His talk was rejected, and where acceptance was the way in, that dropped the conference. */
        REJECTED
    }
}
