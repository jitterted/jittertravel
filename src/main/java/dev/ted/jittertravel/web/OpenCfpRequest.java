package dev.ted.jittertravel.web;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Command record for recording that a conference's CFP is open and when it closes. A record rather
 * than a form bean: the id comes from the path and there is one field on the form, so the controller
 * builds this directly (mirrors {@link ConfirmConferenceAttendanceRequest}).
 * <p>
 * {@code closesOn} is wall-clock here, not an instant: the form asks for the deadline as it is
 * written on the CFP page, and the boundary pairs it with the conference's own venue zone. It also
 * rides into {@code command_log} as this record's {@code toString()}, which is why the zone is not
 * on it — the resolved {@code ZonedTimestamp} is in the event, where the audit trail wants it.
 */
public record OpenCfpRequest(
        UUID conferenceId,
        LocalDateTime closesOn
) {
}
