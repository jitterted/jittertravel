package dev.ted.jittertravel.domain;

public record ConferenceCancelled(
        ConferenceId conferenceId,
        String reason
) implements Event {
    public ConferenceCancelled {
        if (reason == null) {
            reason = "";
        }
    }
}
