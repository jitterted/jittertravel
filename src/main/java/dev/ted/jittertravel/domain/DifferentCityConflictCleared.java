package dev.ted.jittertravel.domain;

public record DifferentCityConflictCleared(
        GatheringId gatheringId,
        ConferenceId conferenceId,
        String reason
) implements Event {
    public DifferentCityConflictCleared {
        if (reason == null) {
            reason = "";
        }
    }
}
