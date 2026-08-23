package dev.ted.jittertravel.web;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Command record for recording that a conference's CFP is open, when it closes, and where the talk
 * is submitted. A record rather than a form bean: the id comes from the path and the two fields are
 * typed straight through, so the controller builds this directly (mirrors
 * {@link ConfirmConferenceAttendanceRequest}).
 * <p>
 * It is also what rides into {@code command_log} when the <em>plan</em> form records a CFP in the
 * same submit — that path produces two commands, and this is the second one, so the audit trail
 * reads the same whichever page the deadline was typed on.
 * <p>
 * {@code closesOn} is wall-clock here, not an instant: the form asks for the deadline as it is
 * written on the CFP page, and the boundary pairs it with the conference's own venue zone. It also
 * rides into {@code command_log} as this record's {@code toString()}, which is why the zone is not
 * on it — the resolved {@code ZonedTimestamp} is in the event, where the audit trail wants it.
 */
public record OpenCfpRequest(
        UUID conferenceId,
        LocalDateTime closesOn,
        String submissionUrl
) {
    public OpenCfpRequest {
        if (submissionUrl == null) {
            submissionUrl = "";
        }
    }
}
