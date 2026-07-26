package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ChangeGatheringCommand;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.web.ChangeGatheringRequest;

import java.util.UUID;

public class ChangeGatheringHandler {

    public ChangeGatheringCommand handle(ChangeGatheringRequest request) {
        return new ChangeGatheringCommand(
                GatheringId.of(UUID.fromString(request.getGatheringId())),
                request.getTitle(),
                request.getVenueName(),
                request.getLocation(),
                request.getDate(),
                request.getStartTime(),
                request.getEndTime(),
                request.isSpeaking(),
                request.getInfoUrl()
        );
    }
}
