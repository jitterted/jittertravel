package dev.ted.jittertravel.web;

import java.util.UUID;

/**
 * Command record for recording a move on a conference's speaking axis. A record rather than a form
 * bean: the id comes from the path and there is one field, so the controller builds this directly
 * (mirrors {@link OpenCfpRequest}).
 * <p>
 * It rides into {@code command_log} as this record's {@code toString()}, which is why the outcome
 * is on it by name — reading the log back, "SUBMITTED" is the whole story of what was recorded.
 * The timestamp is not here: it is captured at the boundary and lands in the event, where the audit
 * trail wants it.
 */
public record RecordTalkRequest(
        UUID conferenceId,
        TalkOutcome outcome
) {
}
