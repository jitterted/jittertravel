package dev.ted.jittertravel.web;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Form-backing record for the "change conference dates" page — a record bound by
 * {@code th:object}/{@code th:field}, as {@link ChangePrivateEventMatchingLocationRequest} explains.
 * <p>
 * <strong>No id and no zone.</strong> Which conference moved is path data, and the zone is the one
 * the conference was planned in, folded from the stream by the write path. Neither is on the form,
 * so neither can be re-targeted by a crafted POST.
 * <p>
 * Both dates are required, which nothing here says: {@code RequiredEntryAdvice} makes every bound
 * {@code LocalDateTime} required, and the controller returns before the write when it has marked
 * one.
 */
public record ChangeConferenceDatesRequest(
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startDate,
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endDate
) {
}
