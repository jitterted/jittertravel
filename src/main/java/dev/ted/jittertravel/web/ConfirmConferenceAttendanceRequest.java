package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.AttendanceBasis;

import java.util.UUID;

/**
 * Command record for confirming that Ted is attending a conference. A record rather than a form
 * bean: the id comes from the path and the basis from a single radio group, so the controller
 * builds this directly (mirrors {@link DeclineConferenceRequest}).
 */
public record ConfirmConferenceAttendanceRequest(
        UUID conferenceId,
        AttendanceBasis basis
) {
}
