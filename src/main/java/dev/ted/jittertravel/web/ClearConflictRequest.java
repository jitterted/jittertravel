package dev.ted.jittertravel.web;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Form-backing record for clearing a schedule conflict.
 * <p>
 * The two ids and the four name/city fields arrive as hidden inputs the GET filled in, so they are
 * form data rather than path data and stay components here. Only {@code reason} is typed.
 */
public record ClearConflictRequest(
        String gatheringId,
        String conferenceId,
        String reason,
        String gatheringName,
        String gatheringCity,
        String conferenceName,
        String conferenceCity,
        // Optional to RequiredEntryAdvice, unlike every other bound date: nothing is written from
        // it, so refusing a clear-conflict because a display-only hidden input arrived empty would
        // fail a valid submit to protect a summary line.
        @OptionalEntry @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
) {
}
